package com.chrispsz.rotinacheck.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.LocalDate

/**
 * Hand-computed progress/consistency math over the sample routine.
 * Reference: "semana" has 19 tasks (3+2+3+2+2+2+2+3), "fim-de-semana" has 10.
 */
class ProgressLogicTest {

    private val routine = (RoutineJson.parse(SAMPLE_ROUTINE) as RoutineParseResult.Ok).routine

    @Test
    fun `dayKey is ISO yyyy-MM-dd`() {
        assertEquals("2026-11-02", Progress.dayKey(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun `key joins four fields`() {
        assertEquals("2026-11-02|semana|b3|0", Progress.key("2026-11-02", "semana", "b3", 0))
    }

    @Test
    fun `percent is floor of checked over total`() {
        val day = routine.dayFor(weekend = false)!!
        // 3 of 19 → 300/19 = 15.79 → 15 (floor)
        val checks = setOf(
            Progress.key("2026-11-02", "semana", "b1", 0),
            Progress.key("2026-11-02", "semana", "b1", 1),
            Progress.key("2026-11-02", "semana", "b1", 2),
        )
        assertEquals(15, Progress.percent(day, "2026-11-02", checks))
    }

    @Test
    fun `empty day stays at zero percent and never completes`() {
        val emptyDay = RoutineDay("d", "D", listOf(RoutineBlock("b", "B", "", null, null, emptyList())))
        assertEquals(0, Progress.percent(emptyDay, "2026-11-02", emptySet()))
        assertFalse(Progress.isComplete(emptyDay, "2026-11-02", setOf("x")))
    }

    @Test
    fun `all 19 weekday tasks complete the day`() {
        val day = routine.dayFor(weekend = false)!!
        val dayKey = "2026-11-02"
        val checks = day.blocos.flatMap { b ->
            b.tarefas.indices.map { i -> Progress.key(dayKey, "semana", b.id, i) }
        }.toSet()
        assertEquals(19, checks.size)
        assertEquals(100, Progress.percent(day, dayKey, checks))
        assertTrue(Progress.isComplete(day, dayKey, checks))
    }

    @Test
    fun `consistency resolves the correct routine day per weekday`() {
        // Monday 2026-11-02 → window covers Tue Oct 27 … Mon Nov 2.
        val history = Progress.consistency(routine, LocalDate.of(2026, 11, 2), emptySet())
        assertEquals(7, history.size)
        assertEquals(LocalDate.of(2026, 10, 27), history.first().date)
        assertEquals("semana", history[0].dayId)       // Tuesday
        assertEquals("fim-de-semana", history[4].dayId) // Saturday Oct 31
        assertEquals("fim-de-semana", history[5].dayId) // Sunday Nov 1
        assertEquals("semana", history[6].dayId)        // Monday Nov 2
        assertTrue(history.none { it.complete })
    }

    @Test
    fun `completed count sums complete days only`() {
        val day = routine.dayFor(weekend = false)!!
        val dayKey = "2026-11-02"
        val checks = day.blocos.flatMap { b ->
            b.tarefas.indices.map { i -> Progress.key(dayKey, "semana", b.id, i) }
        }.toSet()
        val history = Progress.consistency(routine, LocalDate.of(2026, 11, 2), checks)
        assertEquals(1, Progress.completedCount(history))
        assertTrue(history.last().complete)
    }

    @Test
    fun `prune keeps the keepDays window and today`() {
        val today = LocalDate.of(2026, 11, 2)
        val checks = setOf(
            Progress.key("2026-11-02", "semana", "b1", 0),               // today — keep
            Progress.key("2026-10-19", "semana", "b1", 0),               // 14 days back — keep edge
            Progress.key("2026-10-18", "semana", "b1", 0),               // 15 days back — drop
        )
        val pruned = Progress.prune(checks, today, keepDays = 14)
        assertTrue(pruned.contains(Progress.key("2026-11-02", "semana", "b1", 0)))
        assertTrue(pruned.contains(Progress.key("2026-10-19", "semana", "b1", 0)))
        assertFalse(pruned.contains(Progress.key("2026-10-18", "semana", "b1", 0)))
    }

    private companion object {
        val SAMPLE_ROUTINE = """
            {"versao":"1.0","diasSemana":[
              {"id":"semana","nome":"Segunda a Sexta","blocos":[
                {"id":"b1","nome":"B1","horario":"05:00 - 12:30","tarefas":["t1","t2","t3"]},
                {"id":"b2","nome":"B2","horario":"12:30 - 13:40","tarefas":["t1","t2"]},
                {"id":"b3","nome":"B3","horario":"13:40 - 14:15","tarefas":["t1","t2","t3"]},
                {"id":"b4","nome":"B4","horario":"14:20 - 16:00","tarefas":["t1","t2"]},
                {"id":"b5","nome":"B5","horario":"16:00 - 16:30","tarefas":["t1","t2"]},
                {"id":"b6","nome":"B6","horario":"16:30 - 18:00","tarefas":["t1","t2"]},
                {"id":"b7","nome":"B7","horario":"18:00 - 19:30","tarefas":["t1","t2"]},
                {"id":"b8","nome":"B8","horario":"19:30 - 22:00","tarefas":["t1","t2","t3"]}]},
              {"id":"fim-de-semana","nome":"Sábado e Domingo","blocos":[
                {"id":"sab1","nome":"S1","horario":"07:30 - 11:30","tarefas":["t1","t2"]},
                {"id":"sab2","nome":"S2","horario":"12:00 em diante","tarefas":["t1","t2"]},
                {"id":"dom1","nome":"D1","horario":"08:00 - 19:00","tarefas":["t1","t2","t3"]},
                {"id":"dom2","nome":"D2","horario":"21:30 - 22:00","tarefas":["t1","t2"]}]}
            ]}
        """.trimIndent()
    }
}
