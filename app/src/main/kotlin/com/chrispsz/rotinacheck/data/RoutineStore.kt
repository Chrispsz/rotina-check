package com.chrispsz.rotinacheck.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The ONLY storage layer — Preferences DataStore, 100% local on the device.
 * No network, no accounts, no analytics: the backup is a file the user
 * explicitly exports.
 */
private val Context.rotinaDataStore by preferencesDataStore(name = "rotina_check")

class RoutineStore(private val context: Context) {

    private object Keys {
        val ROUTINE = stringPreferencesKey("routine_json")
        val CHECKS = stringSetPreferencesKey("checks")
        val ALERTS = booleanPreferencesKey("alerts_on")

        /** Set by "Limpar dados" so the bundled example is NOT re-seeded on
         *  the next launch — clearing data must actually mean empty. */
        val SEED_DISMISSED = booleanPreferencesKey("seed_dismissed")
    }

    // ---- Flows ----------------------------------------------------------------

    val routineFlow: Flow<Routine?> = context.rotinaDataStore.data.map { prefs ->
        prefs[Keys.ROUTINE]?.let { json ->
            (RoutineJson.parse(json) as? RoutineParseResult.Ok)?.routine
        }
    }

    val checksFlow: Flow<Set<String>> = context.rotinaDataStore.data.map { prefs ->
        prefs[Keys.CHECKS] ?: emptySet()
    }

    val alertsFlow: Flow<Boolean> = context.rotinaDataStore.data.map { prefs ->
        prefs[Keys.ALERTS] ?: true
    }

    val seedDismissedFlow: Flow<Boolean> = context.rotinaDataStore.data.map { prefs ->
        prefs[Keys.SEED_DISMISSED] ?: false
    }

    // ---- Writes ----------------------------------------------------------------

    suspend fun saveRoutine(routine: Routine) {
        context.rotinaDataStore.edit { it[Keys.ROUTINE] = RoutineJson.toRoutineJson(routine) }
    }

    suspend fun toggleCheck(key: String) {
        context.rotinaDataStore.edit { prefs ->
            val current = prefs[Keys.CHECKS] ?: emptySet()
            prefs[Keys.CHECKS] = if (key in current) current - key else current + key
        }
    }

    suspend fun setChecks(checks: Set<String>) {
        context.rotinaDataStore.edit { it[Keys.CHECKS] = checks }
    }

    suspend fun setAlerts(on: Boolean) {
        context.rotinaDataStore.edit { it[Keys.ALERTS] = on }
    }

    suspend fun setSeedDismissed(dismissed: Boolean) {
        context.rotinaDataStore.edit { it[Keys.SEED_DISMISSED] = dismissed }
    }

    // ---- Snapshot helpers (receivers / one-shot reads) ---------------------------

    suspend fun routineNow(): Routine? = routineFlow.first()
    suspend fun checksNow(): Set<String> = checksFlow.first()
    suspend fun alertsNow(): Boolean = alertsFlow.first()
    suspend fun seedDismissedNow(): Boolean = seedDismissedFlow.first()

    /** Backup snapshot assembled from the live state (lossless round-trip). */
    suspend fun exportBackupJson(exportedAtIso: String): String? {
        val routine = routineNow() ?: return null
        return RoutineJson.toBackupJson(
            routine = routine,
            alertsOn = alertsNow(),
            checks = checksNow(),
            exportedAtIso = exportedAtIso,
        )
    }

    /**
     * Imports a routine OR a backup file. Backup settings/history apply when
     * present; plain routines keep the current settings. Returns the parse
     * result (routine + warnings, or a PT error message) for the UI.
     */
    suspend fun importJson(text: String): RoutineParseResult {
        val backup = RoutineJson.parseBackup(text)
        if (backup != null) {
            context.rotinaDataStore.edit { prefs ->
                prefs[Keys.ROUTINE] = RoutineJson.toRoutineJson(backup.routine)
                backup.alertsOn?.let { prefs[Keys.ALERTS] = it }
                backup.checks?.takeIf { it.isNotEmpty() }?.let { prefs[Keys.CHECKS] = it }
            }
            return RoutineParseResult.Ok(backup.routine, backup.warnings)
        }
        // Not a backup (or invalid) — surface the plain-routine parse result.
        return RoutineJson.parse(text)
    }

    /** Factory reset: routine, checks, toggles — everything. */
    suspend fun clearAll() {
        context.rotinaDataStore.edit { it.clear() }
    }
}

/**
 * Process-wide accessor. DataStore delegates are already per-process
 * singletons; this just keeps one [RoutineStore] wrapper alive.
 */
object Graph {
    @Volatile
    private var store: RoutineStore? = null

    fun store(context: Context): RoutineStore =
        store ?: synchronized(this) {
            store ?: RoutineStore(context.applicationContext).also { store = it }
        }
}
