package com.chrispsz.rotinacheck.data

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Pure routine model — mirrors the importable `.json` shape
 * (`diasSemana → blocos → tarefas`) with times pre-parsed into
 * minutes-of-day so every consumer works on integers.
 */
data class RoutineBlock(
    val id: String,
    val nome: String,
    val horarioTexto: String,
    val startMin: Int?,
    val endMin: Int?,
    val tarefas: List<String>,
) {
    val timeRange: TimeRange?
        get() = startMin?.let { TimeRange(it, endMin) }

    /** True when [minutesOfDay] falls inside a closed (start+end) block. */
    fun isCurrent(minutesOfDay: Int): Boolean = timeRange?.contains(minutesOfDay) == true
}

data class RoutineDay(
    val id: String,
    val nome: String,
    val blocos: List<RoutineBlock>,
)

data class Routine(
    val versao: String,
    val dias: List<RoutineDay>,
) {

    /**
     * Picks the routine day matching [weekend]. The heuristics accept the
     * canonical ids from the sample routine (`semana`, `fim-de-semana`) and
     * reasonable variants (`fds`, `weekend`, `Sábado e Domingo`); a
     * single-day routine applies to every day of the week.
     */
    fun dayFor(weekend: Boolean): RoutineDay? {
        if (dias.isEmpty()) return null
        if (dias.size == 1) return dias[0]

        val weekendDay = dias.firstOrNull { d ->
            isWeekendDay(d.id) || isWeekendDay(d.nome)
        }
        val weekdayDay = dias.firstOrNull { it !== weekendDay }
        return (if (weekend) weekendDay else weekdayDay) ?: dias.first()
    }

    private fun isWeekendDay(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("fim") || t.contains("fds") ||
            t.contains("weekend") || t.contains("sab") ||
            t.contains("sáb") || t.contains("dom")
    }
}

fun isWeekend(date: LocalDate): Boolean =
    date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

/**
 * Pure editor operations over [Routine]. The ViewModel persists the returned
 * value verbatim — keeping the math here makes the whole editor unit-testable
 * without Android.
 */
object RoutineEditor {

    private fun Routine.replaceDay(dayId: String, transform: (RoutineDay) -> RoutineDay): Routine =
        copy(dias = dias.map { if (it.id == dayId) transform(it) else it })

    private fun RoutineDay.replaceBlock(blockId: String, transform: (RoutineBlock) -> RoutineBlock): RoutineDay =
        copy(blocos = blocos.map { if (it.id == blockId) transform(it) else it })

    fun renameBlock(routine: Routine, dayId: String, blockId: String, nome: String): Routine =
        routine.replaceDay(dayId) { day ->
            day.replaceBlock(blockId) { it.copy(nome = nome.trim()) }
        }

    /** Updates the times and re-derives the canonical `horario` text. */
    fun setBlockTime(
        routine: Routine,
        dayId: String,
        blockId: String,
        startMin: Int?,
        endMin: Int?,
    ): Routine = routine.replaceDay(dayId) { day ->
        day.replaceBlock(blockId) { block ->
            // End is only kept when strictly after the start; the stored
            // text always matches the stored minutes (round-trip safe).
            val effectiveEnd = endMin?.takeIf { startMin != null && it > startMin }
            val texto = if (startMin == null) ""
            else TimeRange(startMin, effectiveEnd).format()
            block.copy(horarioTexto = texto, startMin = startMin, endMin = effectiveEnd)
        }
    }

    /** Keeps raw text while typing (no trim) — trailing spaces must not fight the cursor. */
    fun renameTask(routine: Routine, dayId: String, blockId: String, index: Int, text: String): Routine =
        routine.replaceDay(dayId) { day ->
            day.replaceBlock(blockId) { block ->
                block.copy(tarefas = block.tarefas.mapIndexed { i, t -> if (i == index) text else t })
            }
        }

    fun addTask(routine: Routine, dayId: String, blockId: String, text: String): Routine =
        routine.replaceDay(dayId) { day ->
            day.replaceBlock(blockId) { block ->
                block.copy(tarefas = block.tarefas + text.trim())
            }
        }

    /**
     * Removes a task AND remaps every historical check key of that block so
     * the 7-day consistency history stays meaningful after edits.
     */
    fun removeTask(
        routine: Routine,
        checks: Set<String>,
        dayId: String,
        blockId: String,
        index: Int,
    ): Pair<Routine, Set<String>> {
        val newRoutine = routine.replaceDay(dayId) { day ->
            day.replaceBlock(blockId) { block ->
                block.copy(tarefas = block.tarefas.filterIndexed { i, _ -> i != index })
            }
        }
        return newRoutine to remapChecksAfterTaskRemoval(checks, dayId, blockId, index)
    }

    fun addBlock(routine: Routine, dayId: String, nome: String, id: String = newId()): Routine =
        routine.replaceDay(dayId) { day ->
            day.copy(blocos = day.blocos + RoutineBlock(id, nome.trim(), "", null, null, emptyList()))
        }

    fun removeBlock(routine: Routine, checks: Set<String>, dayId: String, blockId: String): Pair<Routine, Set<String>> {
        val newRoutine = routine.replaceDay(dayId) { day ->
            day.copy(blocos = day.blocos.filterNot { it.id == blockId })
        }
        val suffix = "|$dayId|$blockId|"
        return newRoutine to checks.filterNot { it.contains(suffix) }.toSet()
    }

    /** Shifts check indices > [removedIndex] down one; drops the removed one. */
    fun remapChecksAfterTaskRemoval(
        checks: Set<String>,
        dayId: String,
        blockId: String,
        removedIndex: Int,
    ): Set<String> = checks.mapNotNull { key ->
        val parts = key.split('|')
        if (parts.size != 4 || parts[1] != dayId || parts[2] != blockId) return@mapNotNull key
        val idx = parts[3].toIntOrNull() ?: return@mapNotNull key
        when {
            idx == removedIndex -> null
            idx > removedIndex -> "${parts[0]}|$dayId|$blockId|${idx - 1}"
            else -> key
        }
    }.toSet()

    fun newId(): String = "blk_${System.currentTimeMillis().toString(36)}${(0..99).random()}"
}
