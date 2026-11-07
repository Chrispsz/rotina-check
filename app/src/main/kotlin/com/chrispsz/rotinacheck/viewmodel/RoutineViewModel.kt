package com.chrispsz.rotinacheck.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chrispsz.rotinacheck.R
import com.chrispsz.rotinacheck.alarm.BlockAlarmManager
import com.chrispsz.rotinacheck.data.Graph
import com.chrispsz.rotinacheck.data.Progress
import com.chrispsz.rotinacheck.data.Routine
import com.chrispsz.rotinacheck.data.RoutineEditor
import com.chrispsz.rotinacheck.data.RoutineParseResult
import com.chrispsz.rotinacheck.data.RoutineStore
import com.chrispsz.rotinacheck.data.isWeekend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** Internal navigation — no nav library needed for three screens. */
enum class Screen { HOME, SETTINGS, EDITOR }

private data class Nav(val screen: Screen = Screen.HOME, val editorBlockId: String? = null)

private data class StoreData(
    val routine: Routine?,
    val checks: Set<String>,
    val alertsOn: Boolean,
    val pomodoroUrl: String,
    val mode: String,
)

/**
 * Single source of UI state. Every mutation goes through the pure logic
 * layer ([RoutineEditor], [Progress]) and persists immediately — the editor
 * is real-time by construction, with no separate "save" step.
 */
class RoutineViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Graph.store(app)
    private val alarms = BlockAlarmManager(app)

    private val nav = MutableStateFlow(Nav())
    private val message = MutableStateFlow<String?>(null)

    private val data = combine(
        store.routineFlow,
        store.checksFlow,
        store.alertsFlow,
        store.pomodoroFlow,
        store.modeFlow,
    ) { routine, checks, alerts, pomodoro, mode ->
        StoreData(routine, checks, alerts, pomodoro, mode)
    }

    val ui: StateFlow<UiState> = combine(data, nav, message) { d, n, msg ->
        UiState(
            routine = d.routine,
            checks = d.checks,
            alertsOn = d.alertsOn,
            pomodoroUrl = d.pomodoroUrl,
            mode = d.mode,
            screen = n.screen,
            editorBlockId = n.editorBlockId,
            canExactAlarms = alarms.canScheduleExactAlarms(),
            message = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    // ---- Derived helpers ------------------------------------------------------

    fun weekendActive(mode: String): Boolean = when (mode) {
        RoutineStore.MODE_WEEK -> false
        RoutineStore.MODE_WEEKEND -> true
        else -> isWeekend(LocalDate.now())
    }

    private suspend fun resolveWeekend(): Boolean = weekendActive(store.modeFlow.first())

    private suspend fun persistRoutine(routine: Routine) {
        store.saveRoutine(routine)
        alarms.reschedule(store.routineNow(), store.alertsNow())
    }

    private fun showMessage(text: String) {
        message.value = text
    }

    fun consumeMessage() {
        message.value = null
    }

    // ---- Checklist ------------------------------------------------------------

    fun toggleTask(dayId: String, blockId: String, taskIndex: Int) = viewModelScope.launch {
        store.toggleCheck(Progress.key(Progress.dayKey(LocalDate.now()), dayId, blockId, taskIndex))
    }

    fun setMode(mode: String) = viewModelScope.launch {
        store.setMode(mode)
    }

    fun resetHistory() = viewModelScope.launch(Dispatchers.IO) {
        val todayKey = Progress.dayKey(LocalDate.now())
        val kept = store.checksNow().filter { it.substringBefore(Progress.KEY_SEPARATOR) == todayKey }.toSet()
        store.setChecks(kept)
    }

    // ---- Navigation ------------------------------------------------------------

    fun openSettings() {
        nav.value = Nav(Screen.SETTINGS, null)
    }

    fun openHome() {
        nav.value = Nav(Screen.HOME, null)
    }

    /** True when back was consumed (editor/settings), false on HOME. */
    fun back(): Boolean {
        val current = nav.value
        if (current.screen == Screen.HOME) return false
        nav.value = Nav(Screen.HOME, null)
        return true
    }

    // ---- Editor ----------------------------------------------------------------

    fun openEditor(blockId: String) {
        nav.value = Nav(Screen.EDITOR, blockId)
    }

    fun newBlock() = viewModelScope.launch {
        val routine = store.routineNow() ?: return@launch
        val day = routine.dayFor(resolveWeekend()) ?: return@launch
        val id = RoutineEditor.newId()
        persistRoutine(RoutineEditor.addBlock(routine, day.id, getApplication<Application>().getString(R.string.editor_new_block_default), id))
        nav.value = Nav(Screen.EDITOR, id)
    }

    fun closeEditor() {
        nav.value = Nav(Screen.HOME, null)
    }

    fun renameBlock(dayId: String, blockId: String, nome: String) = viewModelScope.launch {
        val routine = store.routineNow() ?: return@launch
        persistRoutine(RoutineEditor.renameBlock(routine, dayId, blockId, nome))
    }

    fun setBlockTime(dayId: String, blockId: String, startMin: Int?, endMin: Int?) = viewModelScope.launch {
        val routine = store.routineNow() ?: return@launch
        persistRoutine(RoutineEditor.setBlockTime(routine, dayId, blockId, startMin, endMin))
    }

    fun renameTask(dayId: String, blockId: String, index: Int, text: String) = viewModelScope.launch {
        val routine = store.routineNow() ?: return@launch
        persistRoutine(RoutineEditor.renameTask(routine, dayId, blockId, index, text))
    }

    fun addTask(dayId: String, blockId: String, text: String) = viewModelScope.launch {
        val routine = store.routineNow() ?: return@launch
        if (text.isBlank()) return@launch
        persistRoutine(RoutineEditor.addTask(routine, dayId, blockId, text))
    }

    fun removeTask(dayId: String, blockId: String, index: Int) = viewModelScope.launch {
        val routine = store.routineNow() ?: return@launch
        val (newRoutine, newChecks) = RoutineEditor.removeTask(routine, store.checksNow(), dayId, blockId, index)
        store.setChecks(newChecks)
        persistRoutine(newRoutine)
    }

    fun removeBlock(dayId: String, blockId: String) = viewModelScope.launch {
        val routine = store.routineNow() ?: return@launch
        val (newRoutine, newChecks) = RoutineEditor.removeBlock(routine, store.checksNow(), dayId, blockId)
        store.setChecks(newChecks)
        persistRoutine(newRoutine)
        nav.value = Nav(Screen.HOME, null)
    }

    // ---- Settings ---------------------------------------------------------------

    fun setAlerts(on: Boolean) = viewModelScope.launch {
        store.setAlerts(on)
        alarms.reschedule(store.routineNow(), on)
    }

    fun setPomodoro(url: String) = viewModelScope.launch {
        store.setPomodoro(url)
    }

    fun openExactAlarmSettings() {
        alarms.launchExactAlarmSettings()
    }

    fun clearAll() = viewModelScope.launch {
        store.clearAll()
        alarms.cancel()
        nav.value = Nav(Screen.HOME, null)
        showMessage(getApplication<Application>().getString(R.string.msg_cleared))
    }

    // ---- Import / export ----------------------------------------------------------

    fun importFromUri(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val text = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            showMessage(getApplication<Application>().getString(R.string.msg_import_error, "leitura falhou"))
        } else {
            doImport(text)
        }
    }

    private suspend fun doImport(text: String) {
        when (val result = store.importJson(text)) {
            is RoutineParseResult.Err -> {
                showMessage(
                    getApplication<Application>().getString(R.string.msg_import_error, result.message),
                )
            }
            is RoutineParseResult.Ok -> {
                val base = getApplication<Application>().getString(R.string.msg_imported)
                showMessage(if (result.warnings.isEmpty()) base else "$base · ${result.warnings.first()}")
                alarms.reschedule(store.routineNow(), store.alertsNow())
            }
        }
    }

    fun exportToUri(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val json = store.exportBackupJson(Instant.now().toString())
        val app = getApplication<Application>()
        val ok = json != null && runCatching {
            app.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json!!.toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)
        showMessage(
            app.getString(
                if (ok) R.string.msg_exported else R.string.msg_import_error,
                if (ok) "" else "backup falhou",
            ),
        )
    }
}

/**
 * Immutable UI snapshot. Derived values (percent, consistency) are computed
 * once per emission from the pure logic layer — no logic lives in composables.
 */
data class UiState(
    val routine: Routine? = null,
    val checks: Set<String> = emptySet(),
    val alertsOn: Boolean = true,
    val pomodoroUrl: String = "",
    val mode: String = RoutineStore.MODE_AUTO,
    val screen: Screen = Screen.HOME,
    val editorBlockId: String? = null,
    val canExactAlarms: Boolean = true,
    val message: String? = null,
) {
    val weekendActive: Boolean
        get() = when (mode) {
            RoutineStore.MODE_WEEK -> false
            RoutineStore.MODE_WEEKEND -> true
            else -> isWeekend(LocalDate.now())
        }

    val activeDay: com.chrispsz.rotinacheck.data.RoutineDay?
        get() = routine?.dayFor(weekendActive)

    val todayKey: String get() = Progress.dayKey(LocalDate.now())

    val percent: Int
        get() = activeDay?.let { Progress.percent(it, todayKey, checks) } ?: 0

    val consistency: List<Progress.ConsistencyDay>
        get() = Progress.consistency(routine, LocalDate.now(), checks)

    val completedLast7: Int get() = Progress.completedCount(consistency)
}
