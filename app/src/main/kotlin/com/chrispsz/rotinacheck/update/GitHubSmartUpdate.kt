package com.chrispsz.rotinacheck.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
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
 *  8. One-tap update: "Atualizar" hands the APK to the system [DownloadManager]
 *     (system notification while it runs, survives process death) and the
 *     finished APK opens in the platform installer via FileProvider. The host
 *     app MUST declare REQUEST_INSTALL_PACKAGES (a normal, install-time
 *     permission): without it the platform installer SILENTLY refuses the
 *     ACTION_VIEW flow on Android 8+ — the "Instalar agora" tap does
 *     nothing. [installUpdate] drives the one-time "Allow from this source"
 *     gate itself. If anything in the download path fails, we fall back to
 *     the browser.
 *
 * Network contract: one HTTPS GET to `repos/{owner}/{repo}/releases/latest`
 * per day plus the explicit APK download the user asked for. GitHub's
 * `latest` endpoint already excludes drafts and prereleases, so tags like
 * `v1.1.0-test` (prerelease channel) never trigger the banner.
 *
 * REUSE IN ANOTHER APP: this file is self-contained (HttpURLConnection +
 * DownloadManager + FileProvider + org.json + Preferences DataStore — all
 * platform APIs, no new dependency, no string resources). Copy it, add the
 * FileProvider AND the REQUEST_INSTALL_PACKAGES permission to your manifest,
 * call [onAppOpened] on every foreground entry, observe [state], and render
 * the banner. That's the entire integration.
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
    private var pollJob: Job? = null

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

        /** APK is being fetched by the system DownloadManager (0–100%). */
        data class Downloading(val version: String, val percent: Int) : State

        /** APK is on disk — one tap opens the platform installer. */
        data class ReadyToInstall(val version: String) : State
    }

    /** Manual-check outcomes for the Settings inline status. */
    enum class Feedback { UP_TO_DATE, CHECK_FAILED }

    /**
     * Call on EVERY foreground entry (ON_START). In the common case this does
     * one DataStore read and zero network: it re-resolves the persisted
     * pending update AND any download in flight (system DownloadManager keeps
     * running even if the process died), then enforces the once-a-day gate.
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
        runCatching {
            app.smartUpdateDataStore.edit { it[KEY_IGNORED] = info.version }
        }
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

    /**
     * One-tap update: enqueue the APK in the system DownloadManager. The
     * download keeps running even if the app process dies; the finished file
     * is opened in the platform installer. Any failure falls back to the
     * browser (previous behavior) — the user is never stuck.
     */
    fun downloadUpdate() {
        val info = (_state.value as? State.Available)?.info
        if (info?.apkUrl == null) {
            openDownload(app)
            return
        }
        deleteApkFiles()
        val dm = app.getSystemService(DownloadManager::class.java)
        if (dm == null) {
            openDownload(app)
            return
        }
        runCatching {
            val name = "RotinaCheck-v${info.version}.apk"
            val request = DownloadManager.Request(info.apkUrl.toUri()).apply {
                setTitle("RotinaCheck v${info.version}")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, name)
                // The user EXPLICITLY tapped "Atualizar" — allow mobile data.
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val id = dm.enqueue(request)
            scope.launch {
                runCatching {
                    app.smartUpdateDataStore.edit {
                        it[KEY_DOWNLOAD_ID] = id
                        it[KEY_DOWNLOAD_VERSION] = info.version
                    }
                }
                _state.value = State.Downloading(info.version, 0)
                pollDownload(id, info.version)
            }
        }.onFailure {
            // DownloadManager refused (rare, e.g. storage full) → browser.
            openDownload(app)
        }
    }

    /**
     * Opens the finished APK in the platform installer (FileProvider URI).
     *
     * Android 8+ gate: the very first install needs the one-time "Allow from
     * this source" grant for THIS app. Without it the platform installer
     * silently refuses the ACTION_VIEW intent (tap does nothing). So we check
     * [PackageManager.canRequestPackageInstalls] first and, if needed, open
     * the exact per-app settings page — the state stays ReadyToInstall, and
     * the next "Instalar agora" tap lands straight in the installer.
     *
     * If the file is somehow gone, the state is cleaned and we fall back to
     * the release page in the browser.
     */
    fun installUpdate() {
        val version = (_state.value as? State.ReadyToInstall)?.version ?: return
        val dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val file = dir?.let { File(it, "RotinaCheck-v$version.apk") }
        if (file == null || !file.exists()) {
            // APK vanished (storage cleanup) → clean state, browser fallback.
            scope.launch { clearDownloadState() }
            openDownload(app)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !app.packageManager.canRequestPackageInstalls()
        ) {
            val gateOpened = runCatching {
                app.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        "package:${app.packageName}".toUri(),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            // Gate handled → user enables the toggle and taps again.
            // Exotic device without that settings screen → fall through and
            // let the platform installer show its own gate (or fail again).
            if (gateOpened) return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.updateprovider", file)
            app.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }.onFailure {
            scope.launch { clearDownloadState() }
            openDownload(app)
        }
    }

    /** Opens the APK asset (falls back to the release page) in the browser. */
    fun openDownload(context: Context) {
        val info = (_state.value as? State.Available)?.info
        val url = info?.apkUrl ?: info?.pageUrl ?: releasesPage
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, (info?.pageUrl ?: releasesPage).toUri()))
            }
        }
    }

    fun consumeFeedback() {
        _feedback.value = null
    }

    // ---- Core ------------------------------------------------------------------

    private fun evaluate(force: Boolean, manual: Boolean = false) = scope.launch {
        // Belt-and-braces: a DataStore IOException must never crash the host
        // app — the updater is a passenger, not the pilot.
        runCatching {
            mutex.withLock {
                pollJob?.cancel()
                val prefs = app.smartUpdateDataStore.data.first()

                // 1) OFFLINE-FIRST: resolve whatever we already know — no network.
                val pending = prefs[KEY_PENDING]?.let { UpdateInfo.fromJson(it) }
                val ignored = prefs[KEY_IGNORED]
                if (pending != null && !SemVer.isNewer(pending.version, installedVersion)) {
                    // The user already installed this (or a newer) version
                    // — stale state is cleaned up locally, no network.
                    app.smartUpdateDataStore.edit { it.remove(KEY_PENDING) }
                    deleteApkFiles()
                }
                val showable = pending
                    ?.takeIf { SemVer.isNewer(it.version, installedVersion) && it.version != ignored }

                var resolved: State = if (showable != null) State.Available(showable) else State.Idle

                // 2) OFFLINE: reconcile a download in flight / finished. The
                // system DownloadManager survives process death, so a user who
                // closed the app mid-download comes back to "Instalar agora".
                val dlId = prefs[KEY_DOWNLOAD_ID]
                val dlVersion = prefs[KEY_DOWNLOAD_VERSION]
                when {
                    dlId == null || dlVersion == null -> Unit
                    !SemVer.isNewer(dlVersion, installedVersion) -> {
                        // That version is already installed — clean everything.
                        clearDownloadState()
                        deleteApkFiles()
                    }
                    else -> when (val status = downloadStatus(dlId)) {
                        DownloadStatus.SUCCESS ->
                            resolved = State.ReadyToInstall(dlVersion)
                        DownloadStatus.FAILED ->
                            // Download died (network/storage) → back to the
                            // Available banner; user can retry with one tap.
                            clearDownloadState()
                        DownloadStatus.RUNNING -> {
                            resolved = State.Downloading(dlVersion, downloadPercent(dlId))
                            pollDownload(dlId, dlVersion)
                        }
                        DownloadStatus.GONE -> clearDownloadState()
                    }
                }
                _state.value = resolved

                // 3) GATE: at most 1 check/day; +3-day grace on mobile data.
                val today = System.currentTimeMillis() / DAY_MS
                if (!force) {
                    val last = prefs[KEY_LAST_CHECK_DAY] ?: 0L
                    if (last == today) return@withLock
                    if (isMetered(app) && today - last < METERED_GRACE_DAYS) return@withLock
                }
                // A manual check must not stomp an active download/ready state
                // (the poller re-renders progress within a second anyway).
                if (manual && _state.value !is State.Downloading && _state.value !is State.ReadyToInstall) {
                    _state.value = State.Checking
                }

                // 4) THE one small request of the day. Any failure = silence.
                val info = withContext(Dispatchers.IO) {
                    runCatching { fetchLatestRelease(owner = owner, repo = repo) }.getOrNull()
                }
                // Count the attempt even when it failed: offline flapping must
                // not turn into a request on every re-entry.
                app.smartUpdateDataStore.edit { it[KEY_LAST_CHECK_DAY] = today }

                if (info == null) {
                    if (manual) {
                        _state.value = resolved
                        _feedback.value = Feedback.CHECK_FAILED
                    }
                    return@withLock
                }
                if (!SemVer.isNewer(info.version, installedVersion)) {
                    if (!SemVer.isNewer(pending?.version ?: "", installedVersion)) {
                        app.smartUpdateDataStore.edit { it.remove(KEY_PENDING) }
                    }
                    if (_state.value !is State.Downloading && _state.value !is State.ReadyToInstall) {
                        _state.value = State.Idle
                    }
                    if (manual) _feedback.value = Feedback.UP_TO_DATE
                    return@withLock
                }
                app.smartUpdateDataStore.edit { it[KEY_PENDING] = info.toJson() }
                if (_state.value !is State.Downloading && _state.value !is State.ReadyToInstall) {
                    _state.value = if (info.version != ignored) State.Available(info) else State.Idle
                }
            }
        }
    }

    // ---- Download tracking --------------------------------------------------------

    private fun pollDownload(id: Long, version: String) {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                when (val status = downloadStatus(id)) {
                    DownloadStatus.SUCCESS -> {
                        _state.value = State.ReadyToInstall(version)
                        return@launch
                    }
                    DownloadStatus.FAILED, DownloadStatus.GONE -> {
                        runCatching { clearDownloadState() }
                        return@launch
                    }
                    DownloadStatus.RUNNING ->
                        _state.value = State.Downloading(version, downloadPercent(id))
                }
            }
        }
    }

    private enum class DownloadStatus { RUNNING, SUCCESS, FAILED, GONE }

    private fun downloadStatus(id: Long): DownloadStatus {
        val dm = app.getSystemService(DownloadManager::class.java) ?: return DownloadStatus.GONE
        return runCatching {
            dm.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) return DownloadStatus.GONE
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESS
                    DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                    else -> DownloadStatus.RUNNING
                }
            }
        }.getOrDefault(DownloadStatus.GONE)
    }

    private fun downloadPercent(id: Long): Int {
        val dm = app.getSystemService(DownloadManager::class.java) ?: return 0
        return runCatching {
            dm.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                if (!cursor.moveToFirst()) return 0
                val soFar = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (total <= 0) 0 else ((soFar * 100) / total).toInt().coerceIn(0, 100)
            }
        }.getOrDefault(0)
    }

    private suspend fun clearDownloadState() {
        app.smartUpdateDataStore.edit {
            it.remove(KEY_DOWNLOAD_ID)
            it.remove(KEY_DOWNLOAD_VERSION)
        }
    }

    /** APKs live in the app's private external dir — delete needs no permission. */
    private fun deleteApkFiles() {
        runCatching {
            app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?.listFiles { f -> f.name.endsWith(".apk") }
                ?.forEach { it.delete() }
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
 * A prerelease suffix (`1.2.3-rc1`, `0.4.1-test`) is considered OLDER than
 * the bare number — defense-in-depth on top of `releases/latest` already
 * excluding prereleases.
 */
object SemVer {
    private val RX = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?$")

    fun isNewer(remote: String, current: String): Boolean {
        val r = parse(remote) ?: return false
        val c = parse(current) ?: return false
        for (i in 0..2) {
            if (r.nums[i] != c.nums[i]) return r.nums[i] > c.nums[i]
        }
        // Equal numbers: only a stable remote beats a prerelease current.
        return !r.pre && c.pre
    }

    private fun parse(raw: String): Parsed? {
        val trimmed = raw.trim().removePrefix("v").removePrefix("V")
        val dash = trimmed.indexOf('-')
        val base = if (dash >= 0) trimmed.substring(0, dash) else trimmed
        val m = RX.matchEntire(base) ?: return null
        val g = m.groupValues
        val major = g[1].toIntOrNull() ?: return null
        val minor = g[2].toIntOrNull() ?: return null
        val patch = g.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        return Parsed(intArrayOf(major, minor, patch), pre = dash >= 0)
    }

    private data class Parsed(val nums: IntArray, val pre: Boolean)
}

// ---- Plumbing ------------------------------------------------------------------

/** Separate DataStore file: update state survives even "Limpar dados". */
private val Context.smartUpdateDataStore by preferencesDataStore(name = "smart_update")

private val KEY_LAST_CHECK_DAY = longPreferencesKey("su_last_check_day")
private val KEY_IGNORED = stringPreferencesKey("su_ignored_version")
private val KEY_PENDING = stringPreferencesKey("su_pending_json")
private val KEY_DOWNLOAD_ID = longPreferencesKey("su_download_id")
private val KEY_DOWNLOAD_VERSION = stringPreferencesKey("su_download_version")

private const val DAY_MS = 86_400_000L

/** On mobile data, only re-check if the last attempt was this many days ago. */
private const val METERED_GRACE_DAYS = 3L

private const val TIMEOUT_MS = 8_000

/** Release-notes preview kept tiny — the full text lives on the release page. */
private const val NOTES_PREVIEW = 300

/** Progress poll while a download is in flight — nothing runs otherwise. */
private const val POLL_INTERVAL_MS = 800L

private fun isMetered(context: Context): Boolean =
    context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
