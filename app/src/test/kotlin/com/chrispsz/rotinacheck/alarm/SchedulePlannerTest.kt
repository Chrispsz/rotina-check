package com.chrispsz.rotinacheck.alarm

import com.chrispsz.rotinacheck.data.RoutineJson
import com.chrispsz.rotinacheck.data.RoutineParseResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Hand-verified event planning over the sample routine (generic).
 * Weekday blocks: b1 06:00-12:00 … b8 19:00-21:00 → 16 events (8 START + 8 END).
 * Weekend entry (single merged day): 4 blocks, sab2 open → 7 events.
 */
class SchedulePlannerTest {

    private val routine = (RoutineJson.parse(SAMPLE) as RoutineParseResult.Ok).routine

    @Test
    fun `weekday yields sixteen ordered events`() {
        val events = SchedulePlanner.eventsForDate(routine, LocalDate.of(2026, 11, 2))!!
        assertEquals(16, events.size)
        assertEquals(360, events.first().minutesOfDay)       // b1 start 06:00
        assertEquals(1260, events.last().minutesOfDay)       // b8 end 21:00
        assertEquals(BlockEvent.Kind.START, events.first().kind)
        assertEquals(BlockEvent.Kind.END, events.last().kind)
    }

    @Test
    fun `merged weekend day yields seven events`() {
        val events = SchedulePlanner.eventsForDate(routine, LocalDate.of(2026, 11, 1))!!
        assertEquals(7, events.size)
        // sab2 is open-ended → exactly one event (its START).
        assertEquals(1, events.count { it.blockId == "sab2" })
        assertEquals(BlockEvent.Kind.START, events.first { it.blockId == "sab2" }.kind)
    }

    @Test
    fun `pending excludes events at or before now`() {
        val events = SchedulePlanner.eventsForDate(routine, LocalDate.of(2026, 11, 2))!!
        val pending = SchedulePlanner.pendingEvents(events, 840) // b4 start 14:00
        assertTrue(pending.all { it.minutesOfDay > 840 })
        assertNull(pending.firstOrNull { it.minutesOfDay == 840 })
    }

    @Test
    fun `next event is the earliest pending`() {
        val events = SchedulePlanner.eventsForDate(routine, LocalDate.of(2026, 11, 2))!!
        // 12:20 now → next is b3 START 13:00 (780). At 12:00 there is a tie
        // (b1 END + b2 START) — covered in the tie test below.
        val next = SchedulePlanner.nextEvent(events, 740)
        assertEquals(780, next?.minutesOfDay)
        assertEquals(BlockEvent.Kind.START, next?.kind)
        assertEquals("b3", next?.blockId)
    }

    @Test
    fun `tie at the same minute resolves by kind order (START first)`() {
        val events = SchedulePlanner.eventsForDate(routine, LocalDate.of(2026, 11, 2))!!
        // b1 END 12:00 == b2 START 12:00 → sorted list puts START (ordinal 0) first.
        val next = SchedulePlanner.nextEvent(events, 700)
        assertEquals(720, next?.minutesOfDay)
        assertEquals("b2", next?.blockId)
    }

    @Test
    fun `no pending events left returns null`() {
        val events = SchedulePlanner.eventsForDate(routine, LocalDate.of(2026, 11, 2))!!
        assertNull(SchedulePlanner.nextEvent(events, 1260)) // everything fired
    }

    @Test
    fun `epoch maps minutes-of-day to the correct wall clock instant`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val date = LocalDate.of(2026, 11, 2)
        val epoch = SchedulePlanner.epochForMinutes(date, 360, zone)
        val expected = ZonedDateTime.of(2026, 11, 2, 6, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, epoch)
    }

    @Test
    fun `event code is stable and non-negative`() {
        val e1 = BlockEvent("b3", "Pausa", BlockEvent.Kind.START, 780)
        assertEquals(e1.code(), e1.code())
        assertTrue(e1.code() >= 0)
    }

    private companion object {
        val SAMPLE = """
            {"versao":"1.0","diasSemana":[
              {"id":"semana","nome":"Segunda a Sexta","blocos":[
                {"id":"b1","nome":"B1","horario":"06:00 - 12:00","tarefas":[]},
                {"id":"b2","nome":"B2","horario":"12:00 - 13:00","tarefas":[]},
                {"id":"b3","nome":"B3","horario":"13:00 - 13:45","tarefas":[]},
                {"id":"b4","nome":"B4","horario":"14:00 - 16:00","tarefas":[]},
                {"id":"b5","nome":"B5","horario":"16:00 - 16:30","tarefas":[]},
                {"id":"b6","nome":"B6","horario":"16:30 - 18:00","tarefas":[]},
                {"id":"b7","nome":"B7","horario":"18:00 - 19:00","tarefas":[]},
                {"id":"b8","nome":"B8","horario":"19:00 - 21:00","tarefas":[]}]},
              {"id":"fim-de-semana","nome":"Sábado e Domingo","blocos":[
                {"id":"sab1","nome":"S1","horario":"08:00 - 12:00","tarefas":[]},
                {"id":"sab2","nome":"S2","horario":"12:30 em diante","tarefas":[]},
                {"id":"dom1","nome":"D1","horario":"09:00 - 18:00","tarefas":[]},
                {"id":"dom2","nome":"D2","horario":"20:00 - 21:00","tarefas":[]}]}
            ]}
        """.trimIndent()
    }
}
