package com.chrispsz.rotinacheck.data

import java.time.LocalDate

/**
 * Pure progress/consistency math.
 *
 * Check keys are `"$dayKey|$dayId|$blockId|$taskIndex"` — one flat string
 * set in DataStore, cheap to store, trivially prune-able and re-mappable
 * after editor operations.
 */
object Progress {

    const val KEY_SEPARATOR = "|"

    fun key(dayKey: String, dayId: String, blockId: String, taskIndex: Int): String =
        "$dayKey$KEY_SEPARATOR$dayId$KEY_SEPARATOR$blockId$KEY_SEPARATOR$taskIndex"

    fun dayKey(date: LocalDate): String = date.toString() // ISO-8601 yyyy-MM-dd

    fun totalTasks(day: RoutineDay): Int = day.blocos.sumOf { it.tarefas.size }

    fun checkedCount(day: RoutineDay, dayKey: String, checks: Set<String>): Int =
        day.blocos.sumOf { block ->
            block.tarefas.indices.count { i -> checks.contains(key(dayKey, day.id, block.id, i)) }
        }

    /** 0..100 (floor); a day with zero tasks stays at 0. */
    fun percent(day: RoutineDay, dayKey: String, checks: Set<String>): Int {
        val total = totalTasks(day)
        if (total == 0) return 0
        return checkedCount(day, dayKey, checks) * 100 / total
    }

    fun isComplete(day: RoutineDay, dayKey: String, checks: Set<String>): Boolean {
        val total = totalTasks(day)
        return total > 0 && checkedCount(day, dayKey, checks) == total
    }

    data class ConsistencyDay(
        val date: LocalDate,
        val dayId: String?,
        val complete: Boolean,
        val active: Boolean,
        val percent: Int,
    )

    /**
     * The last [window] days ending today (inclusive), each resolved against
     * the correct routine day (per-day mapping or weekday/weekend heuristic).
     */
    fun consistency(routine: Routine?, today: LocalDate, checks: Set<String>, window: Int = 7): List<ConsistencyDay> =
        (window - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val day = routine?.dayForDate(date)
            val total = day?.let { totalTasks(it) } ?: 0
            val checked = day?.let { checkedCount(it, dayKey(date), checks) } ?: 0
            ConsistencyDay(
                date = date,
                dayId = day?.id,
                complete = total > 0 && checked == total,
                active = checked > 0,
                percent = if (total == 0) 0 else checked * 100 / total,
            )
        }

    fun completedCount(history: List<ConsistencyDay>): Int = history.count { it.complete }

    /** Drops check keys older than [keepDays] — history stays bounded. */
    fun prune(checks: Set<String>, today: LocalDate, keepDays: Int = 14): Set<String> {
        val cutoff = today.minusDays(keepDays.toLong())
        return checks.filter { entry ->
            val day = LocalDate.parse(entry.substringBefore(KEY_SEPARATOR))
            !day.isBefore(cutoff)
        }.toSet()
    }
}
