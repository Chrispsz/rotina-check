package com.chrispsz.rotinacheck.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the importable routine format (generic sample —
 * rotinas de verdade ficam APENAS no aparelho, nunca no repositório).
 */
class RoutineJsonTest {

    private val sample = """
        {
          "versao": "1.0",
          "diasSemana": [
            {
              "id": "semana",
              "nome": "Segunda a Sexta",
              "blocos": [
                { "id": "b1", "nome": "Manhã", "horario": "06:00 - 12:00",
                  "tarefas": ["Tarefa 1", "Tarefa 2", "Tarefa 3"] },
                { "id": "b2", "nome": "Almoço", "horario": "12:00 - 13:00",
                  "tarefas": ["Tarefa 1", "Tarefa 2"] },
                { "id": "b3", "nome": "Pausa", "horario": "13:00 - 13:45",
                  "tarefas": ["Tarefa 1", "Tarefa 2", "Tarefa 3"] },
                { "id": "b4", "nome": "Foco", "horario": "14:00 - 16:00",
                  "tarefas": ["Tarefa 1", "Tarefa 2"] },
                { "id": "b5", "nome": "Intervalo", "horario": "16:00 - 16:30",
                  "tarefas": ["Tarefa 1", "Tarefa 2"] },
                { "id": "b6", "nome": "Estudo", "horario": "16:30 - 18:00",
                  "tarefas": ["Tarefa 1", "Tarefa 2"] },
                { "id": "b7", "nome": "Casa", "horario": "18:00 - 19:00",
                  "tarefas": ["Tarefa 1", "Tarefa 2"] },
                { "id": "b8", "nome": "Noite", "horario": "19:00 - 21:00",
                  "tarefas": ["Tarefa 1", "Tarefa 2", "Tarefa 3"] }
              ]
            },
            {
              "id": "fim-de-semana",
              "nome": "Sábado e Domingo",
              "blocos": [
                { "id": "sab1", "nome": "Sábado manhã", "horario": "08:00 - 12:00",
                  "tarefas": ["Tarefa 1", "Tarefa 2"] },
                { "id": "sab2", "nome": "Sábado tarde", "horario": "12:30 em diante",
                  "tarefas": ["Tarefa 1", "Tarefa 2"] },
                { "id": "dom1", "nome": "Domingo", "horario": "09:00 - 18:00",
                  "tarefas": ["Tarefa 1", "Tarefa 2", "Tarefa 3"] },
                { "id": "dom2", "nome": "Fim do domingo", "horario": "20:00 - 21:00",
                  "tarefas": ["Tarefa 1", "Tarefa 2"] }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `full sample routine parses with zero warnings`() {
        val result = RoutineJson.parse(sample)
        val ok = assertIs<RoutineParseResult.Ok>(result)
        assertTrue(ok.warnings.isEmpty())
        assertEquals(2, ok.routine.dias.size)
    }

    @Test
    fun `weekday blocks and times land exactly`() {
        val ok = RoutineJson.parse(sample) as RoutineParseResult.Ok
        val weekday = ok.routine.dayFor(weekend = false)!!
        assertEquals("semana", weekday.id)
        assertEquals(8, weekday.blocos.size)

        val nap = weekday.blocos.first { it.id == "b3" }
        assertEquals(780, nap.startMin)  // 13:00
        assertEquals(825, nap.endMin)    // 13:45
        assertEquals(3, nap.tarefas.size)
    }

    @Test
    fun `open weekend block has no end`() {
        val ok = RoutineJson.parse(sample) as RoutineParseResult.Ok
        val weekend = ok.routine.dayFor(weekend = true)!!
        assertEquals("fim-de-semana", weekend.id)
        val lazer = weekend.blocos.first { it.id == "sab2" }
        assertEquals(750, lazer.startMin)
        assertNull(lazer.endMin)
    }

    @Test
    fun `day heuristic resolves weekday vs weekend`() {
        val ok = RoutineJson.parse(sample) as RoutineParseResult.Ok
        assertEquals("semana", ok.routine.dayFor(weekend = false)?.id)
        assertEquals("fim-de-semana", ok.routine.dayFor(weekend = true)?.id)
    }

    @Test
    fun `single-day routine applies to both modes`() {
        val ok = RoutineJson.parse(
            """{"diasSemana":[{"id":"todo","nome":"Todos","blocos":[{"id":"a","nome":"A","horario":"08:00 - 09:00","tarefas":[]}]}]}""",
        ) as RoutineParseResult.Ok
        assertEquals("todo", ok.routine.dayFor(weekend = false)?.id)
        assertEquals("todo", ok.routine.dayFor(weekend = true)?.id)
    }

    @Test
    fun `malformed empty and structureless files fail with clear messages`() {
        assertEquals(RoutineJson.ERR_MALFORMED, (RoutineJson.parse("{") as RoutineParseResult.Err).message)
        assertEquals(RoutineJson.ERR_EMPTY, (RoutineJson.parse("   ") as RoutineParseResult.Err).message)
        assertEquals(RoutineJson.ERR_NO_DAYS, (RoutineJson.parse("""{"versao":"1"}""") as RoutineParseResult.Err).message)
    }

    @Test
    fun `unparseable horario keeps the block and emits a warning`() {
        val ok = RoutineJson.parse(
            """{"diasSemana":[{"id":"d","nome":"D","blocos":[{"id":"x","nome":"X","horario":"zzz","tarefas":[]}]}]}""",
        ) as RoutineParseResult.Ok
        assertEquals(1, ok.warnings.size)
        val block = ok.routine.dias[0].blocos[0]
        assertNull(block.startMin)
        assertEquals("zzz", block.horarioTexto)
    }

    @Test
    fun `non-string tasks are skipped without failing`() {
        val ok = RoutineJson.parse(
            """{"diasSemana":[{"id":"d","nome":"D","blocos":[{"id":"x","nome":"X","horario":"","tarefas":["ok",5,null,""]}]}]}""",
        ) as RoutineParseResult.Ok
        assertEquals(listOf("ok"), ok.routine.dias[0].blocos[0].tarefas)
    }

    @Test
    fun `routine round-trip keeps every field`() {
        val ok = RoutineJson.parse(sample) as RoutineParseResult.Ok
        val json = RoutineJson.toRoutineJson(ok.routine)
        val reparsed = RoutineJson.parse(json) as RoutineParseResult.Ok
        assertEquals(ok.routine, reparsed.routine)
    }

    @Test
    fun `backup wrapper carries settings and history`() {
        val ok = RoutineJson.parse(sample) as RoutineParseResult.Ok
        val backup = RoutineJson.toBackupJson(
            routine = ok.routine,
            alertsOn = false,
            pomodoroUrl = "https://pomodoro.example",
            checks = setOf("2026-11-02|semana|b3|0", "2026-11-02|semana|b3|1"),
            exportedAtIso = "2026-11-02T12:00:00Z",
        )
        val restored = RoutineJson.parseBackup(backup)!!
        assertEquals(ok.routine, restored.routine)
        assertEquals(false, restored.alertsOn)
        assertEquals("https://pomodoro.example", restored.pomodoroUrl)
        assertEquals(2, restored.checks?.size)
    }

    @Test
    fun `plain routine via parseBackup yields null settings (import keeps current)`() {
        val restored = RoutineJson.parseBackup(sample)!!
        assertNull(restored.alertsOn)
        assertNull(restored.checks)
    }
}
