package com.chrispsz.rotinacheck.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDate

/**
 * The bundled example IS the first-launch experience: it must parse clean,
 * cover all seven weekdays via per-day mappings, stay GENERIC (privacy rule:
 * no real routine ever ships inside the APK) and round-trip through the
 * documented public format.
 */
class DefaultRoutineTest {

    @Test
    fun `bundled example parses with zero warnings`() {
        val result = RoutineJson.parse(DefaultRoutine.JSON)
        val ok = assertIs<RoutineParseResult.Ok>(result)
        assertTrue(ok.warnings.isEmpty(), "example JSON must import clean: ${ok.warnings}")
    }

    @Test
    fun `example maps each weekday to its own day`() {
        val routine = DefaultRoutine.routine()
        assertEquals(7, routine.dias.size)
        assertTrue(routine.dias.all { it.weekdays != null && it.weekdays!!.size == 1 })

        assertEquals("seg", routine.dayForDate(LocalDate.of(2026, 11, 9))?.id)  // Monday
        assertEquals("ter", routine.dayForDate(LocalDate.of(2026, 11, 10))?.id) // Tuesday
        assertEquals("sab", routine.dayForDate(LocalDate.of(2026, 11, 14))?.id) // Saturday
        assertEquals("dom", routine.dayForDate(LocalDate.of(2026, 11, 15))?.id) // Sunday
    }

    @Test
    fun `example blocks are machine-parseable and checkable`() {
        val routine = DefaultRoutine.routine()
        routine.dias.forEach { day ->
            assertTrue(day.blocos.isNotEmpty(), "${day.id} needs blocks")
            assertTrue(day.blocos.all { it.tarefas.isNotEmpty() }, "every block needs tasks")
            day.blocos.forEach { block ->
                assertNotNull(block.startMin, "block ${day.id}/${block.id} has no start time")
            }
        }
        // The open-ended Saturday block stays open by design.
        val sab = routine.dias.first { it.id == "sab" }
        assertNull(sab.blocos.last().endMin)
    }

    @Test
    fun `example stays GENERIC — no real personal routine inside the APK`() {
        // Privacy regression guard: the bundled example must never contain
        // the personal routine (or fragments of it) that once leaked.
        val json = DefaultRoutine.JSON.lowercase()
        listOf(
            "bica", "ads", "deep work", "elements", "cochilo", "ônibus",
            "trava de sono", "recolhimento", "faculdade", "moto", "barba",
        ).forEach { forbidden ->
            assertTrue(!json.contains(forbidden), "personal/odd term leaked into bundled example: \"$forbidden\"")
        }
    }

    @Test
    fun `example round-trips through the public format`() {
        val routine = DefaultRoutine.routine()
        val json = RoutineJson.toRoutineJson(routine)
        val reparsed = RoutineJson.parse(json) as RoutineParseResult.Ok
        assertEquals(routine, reparsed.routine)
    }

    @Test
    fun `scratch routine is seven blank per-day days`() {
        val scratch = DefaultRoutine.scratch()
        assertEquals(7, scratch.dias.size)
        assertTrue(scratch.dias.all { it.blocos.isEmpty() && it.weekdays != null })
        assertEquals("seg", scratch.dayForDate(LocalDate.of(2026, 11, 9))?.id)
        assertEquals("dom", scratch.dayForDate(LocalDate.of(2026, 11, 15))?.id)
    }
}
