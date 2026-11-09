package com.chrispsz.rotinacheck.alarm

import com.chrispsz.rotinacheck.data.RoutineDay
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure schedule math: which block events exist for a routine day, which are
 * still pending "now", and the exact epoch instant for a minutes-of-day
 * value. No Android imports — fully unit-testable.
 */
data class BlockEvent(
    val blockId: String,
    val blockName: String,
    val kind: Kind,
    val minutesOfDay: Int,
) {
    enum class Kind { START, END, RESCHEDULE_ANCHOR }

    /** Notification/request-code seed — stable per (block, minute, kind). */
    fun code(): Int =
        (blockId.hashCode() * 31 + minutesOfDay * 2 + kind.ordinal) and 0x7FFFFFFF

    companion object {
        /** Virtual event used to re-arm the planner right after midnight. */
        val DAILY_ANCHOR = BlockEvent("__anchor", "", Kind.RESCHEDULE_ANCHOR, 1)
    }
}

object SchedulePlanner {

    /** All schedulable events of a routine day, ordered by time. */
    fun eventsForDay(day: RoutineDay): List<BlockEvent> =
        day.blocos.flatMap { block ->
            val range = block.timeRange ?: return@flatMap emptyList()
            buildList {
                add(BlockEvent(block.id, block.nome, BlockEvent.Kind.START, range.startMin))
                range.endMin?.let { add(BlockEvent(block.id, block.nome, BlockEvent.Kind.END, it)) }
            }
        }.sortedWith(compareBy({ it.minutesOfDay }, { it.kind.ordinal }))

    /** Events strictly after [nowMin] — the ones that must still fire today. */
    fun pendingEvents(events: List<BlockEvent>, nowMin: Int): List<BlockEvent> =
        events.filter { it.minutesOfDay > nowMin }

    fun nextEvent(events: List<BlockEvent>, nowMin: Int): BlockEvent? =
        pendingEvents(events, nowMin).minByOrNull { it.minutesOfDay }

    /**
     * The routine day that governs [date] plus its events (`null` → nothing
     * to schedule — routine absent or empty).
     */
    fun eventsForDate(routine: com.chrispsz.rotinacheck.data.Routine, date: LocalDate): List<BlockEvent>? {
        val day = routine.dayForDate(date) ?: return null
        return eventsForDay(day).ifEmpty { null }
    }

    fun nowMinutes(now: ZonedDateTime): Int = now.hour * 60 + now.minute

    /** Exact epoch for a minutes-of-day value on [date] (DST-safe). */
    fun epochForMinutes(date: LocalDate, minutesOfDay: Int, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli() + minutesOfDay * 60_000L
}
