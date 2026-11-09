package com.chrispsz.rotinacheck.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Smart OTA updates straight from GitHub Releases — designed to be DISCREET,
 * nearly free to run and impossible to find annoying.
 *
 * DESIGN RULES (the whole point of this class):
 *
 *  1. ZERO cost while the app is closed. No WorkManager, no AlarmManager, no
 *     push, no service. The only trigger is "the app came to the foreground".
 *  2. At most ONE tiny network check per calendar day (~2–5 KB). The daily
 *     gate is persisted, so rotations/re-entries never cause extra traffic.
 *  3. Metered-aware: on mobile data a check only happens if the last one was
 *     [METERED_GRACE_DAYS] days ago — Wi-Fi does the daily work.
 *  4. Silent failure: offline / timeout / rate-limit simply do nothing. The
 *     next day is tried again. The user NEVER sees an error they didn't ask for.
 *  5. Offline-first resolution: once an update is found it is persisted in
 *     DataStore, so the banner keeps appearing on every open (soft pressure
 *     to update) WITHOUT any network use until the user updates or ignores.
 *  6. "Ignorar esta versão" persists the ignored tag: that version never
 *     nags again, but a NEWER one always will.
 *  7. After the user updates (installs the APK), the stale pending state is
 *     cleaned up locally by version comparison — again, no network needed.
 *
 * Network contract: one HTTPS GET to `repos/{owner}/{repo}/releases/latest`
 * per day. GitHub's `latest` endpoint already excludes drafts and prereleases,
 * so tags like `v1.1.0-test` (prerelease channel) never trigger the banner.
 *
 * REUSE IN ANOTHER APP: this file is self-contained (HttpURLConnection +
 * org.json + Preferences DataStore — all platform APIs, no new dependency).
 * Copy it, call [onAppOpened] on every foreground entry, observe [state],
 * and render the update banner. That's the entire integration.
 */
class GitHubSmartUpdate(
    context: Context,
    private val owner: String,
    private val repo: String,
    currentVersion: String,
    private val scope: CoroutineScope,
) {

    private val app = context.applicationContext
    private val releasesPage = "https://github.com/$owner/$repo/releases"
    private val mutex = Mutex()

    /** Version currently installed (shared with the UI for the Settings row). */
    val installedVersion: String = currentVersion

    private val _state = MutableStateFlow<State>(State.Idle)

    /** Observable update state — drive the banner / Settings card from this. */
    val state: StateFlow<State> = _state.asStateFlow()

    private val _feedback = MutableStateFlow<Feedback?>(null)

    /** Outcome of a MANUAL check ("Verificar agora") — auto checks stay silent. */
    val feedback: StateFlow<Feedback?> = _feedback.asStateFlow()

    sealed interface State {
        /** Nothing to show (or banner hidden for this session). */
        data object Idle : State

        /** A manual "Verificar agora" is running (spinner in Settings only). */
        data object Checking : State

        /** A newer release exists — show the discreet banner. */
        data class Available(val info: UpdateInfo) : State
    }

    /** Manual-check outcomes for the Settings inline status. */
    enum class Feedback { UP_TO_DATE, CHECK_FAILED }

    /**
     * Call on EVERY foreground entry (ON_START). In the common case this does
     * one DataStore read and zero network: it re-resolves the persisted
     * pending update offline and enforces the once-a-day gate.
     */
    fun onAppOpened() = evaluate(force = false)

    /** Manual check from Settings — bypasses the daily gate on user request. */
    fun checkNow() = evaluate(force = true, manual = true)

    /**
     * "Ignorar esta versão": the pending tag is stored and never shows the
     * banner again — until a strictly newer release appears.
     */
    fun ignoreCurrent() = scope.launch {
        val info = (_state.value as? State.Available)?.info ?: return@launch
        app.smartUpdateDataStore.edit { it[KEY_IGNORED] = info.version }
        _state.value = State.Idle
    }

    /**
     * Banner ✕: hides for THIS session only. The persisted pending update
     * makes the banner quietly return on the next open — the user is gently
     * led to update without dialogs, sounds or interruptions.
     */
    fun hideForSession() {
        if (_state.value is State.Available) _state.value = State.Idle
    }

    /** Opens the APK asset (falls back to the release page) in the browser. */
    fun openDownload(context: Context) {
        val info = (_state.value as? State.Available)?.info ?: return
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl ?: info.pageUrl)))
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.pageUrl)))
            }
        }
    }

    fun consumeFeedback() {
        _feedback.value = null
    }

    // ---- Core ------------------------------------------------------------------

    private fun evaluate(force: Boolean, manual: Boolean = false) = scope.launch {
        mutex.withLock {
            val prefs = app.smartUpdateDataStore.data.first()

            // 1) OFFLINE-FIRST: resolve whatever we already know — no network.
            val pending = prefs[KEY_PENDING]?.let { UpdateInfo.fromJson(it) }
            val ignored = prefs[KEY_IGNORED]
            if (pending != null) {
                _state.value = when {
                    !SemVer.isNewer(pending.version, installedVersion) -> {
                        // The user already installed this (or a newer) version
                        // — stale state is cleaned up locally, no network.
                        app.smartUpdateDataStore.edit { it.remove(KEY_PENDING) }
                        State.Idle
                    }
                    pending.version == ignored -> State.Idle
                    else -> State.Available(pending)
                }
            }

            // 2) GATE: at most 1 check/day; +3-day grace on mobile data.
            val today = System.currentTimeMillis() / DAY_MS
            if (!force) {
                val last = prefs[KEY_LAST_CHECK_DAY] ?: 0L
                if (last == today) return@withLock
                if (isMetered(app) && today - last < METERED_GRACE_DAYS) return@withLock
            }
            if (manual) _state.value = State.Checking

            // 3) THE one small request of the day. Any failure = silence.
            val info = withContext(Dispatchers.IO) {
                runCatching { fetchLatestRelease(owner = owner, repo = repo) }.getOrNull()
            }
            // Count the attempt even when it failed: offline flapping must not
            // turn into a request on every re-entry.
            app.smartUpdateDataStore.edit { it[KEY_LAST_CHECK_DAY] = today }

            if (info == null) {
                if (manual) {
                    _state.value = State.Idle
                    _feedback.value = Feedback.CHECK_FAILED
                }
                return@withLock
            }
            if (!SemVer.isNewer(info.version, installedVersion)) {
                app.smartUpdateDataStore.edit { it.remove(KEY_PENDING) }
                _state.value = State.Idle
                if (manual) _feedback.value = Feedback.UP_TO_DATE
                return@withLock
            }
            app.smartUpdateDataStore.edit { it[KEY_PENDING] = info.toJson() }
            _state.value = if (info.version != ignored) State.Available(info) else State.Idle
        }
    }

    // ---- Release fetch / parse ---------------------------------------------------

    private fun fetchLatestRelease(owner: String, repo: String): UpdateInfo? {
        val conn = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
            .openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            // GitHub REQUIRES a User-Agent; omitting it returns 403.
            conn.setRequestProperty("User-Agent", "$repo-update-checker")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parseRelease(body, fallbackPageUrl = releasesPage)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRelease(body: String, fallbackPageUrl: String): UpdateInfo? = runCatching {
        val json = JSONObject(body)
        val tag = json.optString("tag_name").trim()
        if (tag.isEmpty()) return@runCatching null

        // Prefer the .apk asset (download without opening the repo); fall back
        // to the release page, where the APK is one click away anyway.
        var apkUrl: String? = null
        json.optJSONArray("assets")?.let { assets ->
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url")
                    break
                }
            }
        }
        UpdateInfo(
            version = tag.removePrefix("v").removePrefix("V"),
            notes = json.optString("body").take(NOTES_PREVIEW).trim(),
            apkUrl = apkUrl?.takeIf { it.isNotEmpty() },
            pageUrl = json.optString("html_url").ifEmpty { fallbackPageUrl },
        )
    }.getOrNull()
}

// ---- Public model ------------------------------------------------------------------

/**
 * Everything the UI needs about a newer release. Persisted as a tiny JSON
 * blob so the banner resolves offline on every subsequent open.
 */
data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String?,
    val pageUrl: String,
) {
    fun toJson(): String = JSONObject().apply {
        put("v", version)
        put("n", notes)
        put("apk", apkUrl ?: "")
        put("page", pageUrl)
    }.toString()

    companion object {
        fun fromJson(raw: String): UpdateInfo? = runCatching {
            val o = JSONObject(raw)
            val version = o.optString("v")
            if (version.isEmpty()) return@runCatching null
            UpdateInfo(
                version = version,
                notes = o.optString("n"),
                apkUrl = o.optString("apk").ifEmpty { null },
                pageUrl = o.optString("page"),
            )
        }.getOrNull()
    }
}

// ---- Semver ------------------------------------------------------------------

/**
 * Minimal, dependency-free version comparison for `vX.Y.Z` tags. Anything
 * unparseable is treated as "not newer" — a broken tag must never nag.
 */
object SemVer {
    private val RX = Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?")

    fun isNewer(remote: String, current: String): Boolean {
        val r = parse(remote) ?: return false
        val c = parse(current) ?: return false
        for (i in 0..2) {
            if (r[i] != c[i]) return r[i] > c[i]
        }
        return false
    }

    private fun parse(raw: String): IntArray? {
        val m = RX.find(raw.trim()) ?: return null
        val g = m.groupValues
        val major = g[1].toIntOrNull() ?: return null
        val minor = g[2].toIntOrNull() ?: return null
        val patch = g.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        return intArrayOf(major, minor, patch)
    }
}

// ---- Plumbing ------------------------------------------------------------------

/** Separate DataStore file: update state survives even "Limpar dados". */
private val Context.smartUpdateDataStore by preferencesDataStore(name = "smart_update")

private val KEY_LAST_CHECK_DAY = longPreferencesKey("su_last_check_day")
private val KEY_IGNORED = stringPreferencesKey("su_ignored_version")
private val KEY_PENDING = stringPreferencesKey("su_pending_json")

private const val DAY_MS = 86_400_000L

/** On mobile data, only re-check if the last attempt was this many days ago. */
private const val METERED_GRACE_DAYS = 3L

private const val TIMEOUT_MS = 8_000

/** Release-notes preview kept tiny — the full text lives on the release page. */
private const val NOTES_PREVIEW = 300

private fun isMetered(context: Context): Boolean =
    context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
