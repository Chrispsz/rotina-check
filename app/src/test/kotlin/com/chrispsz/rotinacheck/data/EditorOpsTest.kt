package com.chrispsz.rotinacheck.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Editor operations must keep routine + check history coherent:
 * task removal remaps historical indices; block removal drops its checks.
 */
class EditorOpsTest {

    private fun block(
        id: String,
        nome: String = id,
        start: Int? = null,
        end: Int? = null,
        tarefas: List<String> = emptyList(),
    ) = RoutineBlock(id, nome, "", start, end, tarefas)

    private val routine = Routine(
        versao = "1.0",
        dias = listOf(
            RoutineDay(
                "semana",
                "Segunda a Sexta",
                listOf(
                    block("b1", start = 300, end = 750, tarefas = listOf("A", "B", "C")),
                    block("b2", start = 820, end = 855, tarefas = listOf("X")),
                ),
            ),
        ),
    )

    @Test
    fun `renameBlock updates the name only`() {
        val result = RoutineEditor.renameBlock(routine, "semana", "b1", "Novo nome ")
        assertEquals("Novo nome", result.dias[0].blocos[0].nome)
        assertEquals(300, result.dias[0].blocos[0].startMin)
    }

    @Test
    fun `setBlockTime rewrites the canonical text`() {
        val result = RoutineEditor.setBlockTime(routine, "semana", "b1", 840, 900)
        val updated = result.dias[0].blocos[0]
        assertEquals("14:00 - 15:00", updated.horarioTexto)
        assertEquals(840, updated.startMin)
        assertEquals(900, updated.endMin)
    }

    @Test
    fun `end before or equal to start degrades to open block`() {
        val result = RoutineEditor.setBlockTime(routine, "semana", "b1", 840, 840)
        val updated = result.dias[0].blocos[0]
        assertEquals(null, updated.endMin)
        assertEquals("14:00 em diante", updated.horarioTexto)
    }

    @Test
    fun `null start clears the schedule text`() {
        val result = RoutineEditor.setBlockTime(routine, "semana", "b1", null, 900)
        val updated = result.dias[0].blocos[0]
        assertEquals("", updated.horarioTexto)
        assertEquals(null, updated.startMin)
        assertEquals(null, updated.endMin)
    }

    @Test
    fun `removeTask remaps historical check indices`() {
        val checks = setOf(
            "2026-11-02|semana|b1|0",
            "2026-11-02|semana|b1|2",       // will shift to 1
            "2026-11-01|semana|b1|2",       // any day: will shift to 1
            "2026-11-02|semana|b1|1",       // the removed task itself — dropped
            "2026-11-02|semana|b2|0",       // other block — untouched
        )
        val (newRoutine, newChecks) = RoutineEditor.removeTask(routine, checks, "semana", "b1", 1)
        assertEquals(listOf("A", "C"), newRoutine.dias[0].blocos[0].tarefas)
        assertEquals(
            setOf(
                "2026-11-02|semana|b1|0",
                "2026-11-02|semana|b1|1",
                "2026-11-01|semana|b1|1",
                "2026-11-02|semana|b2|0",
            ),
            newChecks,
        )
    }

    @Test
    fun `addTask appends without touching checks`() {
        val newRoutine = RoutineEditor.addTask(routine, "semana", "b1", " D ")
        assertEquals(listOf("A", "B", "C", "D"), newRoutine.dias[0].blocos[0].tarefas)
        // addTask shifts no indices — historical sets stay valid untouched.
    }

    @Test
    fun `removeBlock drops every check of that block only`() {
        val checks = setOf(
            "2026-11-02|semana|b1|0",
            "2026-10-30|semana|b1|2",
            "2026-11-02|semana|b2|0",
        )
        val (newRoutine, newChecks) = RoutineEditor.removeBlock(routine, checks, "semana", "b1")
        assertEquals(1, newRoutine.dias[0].blocos.size)
        assertEquals(setOf("2026-11-02|semana|b2|0"), newChecks)
    }

    @Test
    fun `addBlock creates an unscheduled block in the right day`() {
        val result = RoutineEditor.addBlock(routine, "semana", "Extra", "blk_1")
        val added = result.dias[0].blocos.last()
        assertEquals("blk_1", added.id)
        assertEquals("Extra", added.nome)
        assertEquals(null, added.startMin)
        assertTrue(added.tarefas.isEmpty())
        assertFalse(result.dias[0].blocos.first().id == added.id)
    }

    @Test
    fun `remap ignores malformed keys instead of crashing`() {
        val checks = setOf("lixo", "2026-11-02|semana|b1|x", "2026-11-02|semana|b1|3")
        val result = RoutineEditor.remapChecksAfterTaskRemoval(checks, "semana", "b1", 1)
        // "lixo" kept verbatim; non-numeric index kept; index 3 → 2.
        assertEquals(setOf("lixo", "2026-11-02|semana|b1|x", "2026-11-02|semana|b1|2"), result)
    }
}
