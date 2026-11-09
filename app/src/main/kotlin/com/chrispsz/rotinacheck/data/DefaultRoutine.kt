package com.chrispsz.rotinacheck.data

/**
 * The app ships with a GENERIC example routine already loaded — the user
 * edits it in place (rename blocks, change times, add tasks) instead of
 * starting from a blank screen that only accepts an imported file.
 *
 * PRIVACY RULE: this constant must contain NOTHING personal — only neutral,
 * made-up placeholder blocks. Real routines live exclusively on the user's
 * device (import file / editor), never in the repository or the APK.
 *
 * Format: one day per weekday via `"dias": [1..7]` (ISO, 1 = Monday).
 */
object DefaultRoutine {

    const val JSON: String = """{
  "versao": "1.0",
  "diasSemana": [
    {
      "id": "seg",
      "nome": "Segunda",
      "dias": [1],
      "blocos": [
        { "id": "seg_b1", "nome": "Manhã", "horario": "06:00 - 12:00",
          "tarefas": ["Café da manhã", "Higiene", "Estudo ou trabalho"] },
        { "id": "seg_b2", "nome": "Almoço", "horario": "12:00 - 13:30",
          "tarefas": ["Almoço sem pressa", "Descansar um pouco"] },
        { "id": "seg_b3", "nome": "Tarde", "horario": "13:30 - 18:00",
          "tarefas": ["Estudo ou trabalho", "Pausa curta"] },
        { "id": "seg_b4", "nome": "Noite", "horario": "18:00 - 22:00",
          "tarefas": ["Jantar", "Lazer", "Preparar o sono"] }
      ]
    },
    {
      "id": "ter",
      "nome": "Terça",
      "dias": [2],
      "blocos": [
        { "id": "ter_b1", "nome": "Manhã", "horario": "06:00 - 12:00",
          "tarefas": ["Café da manhã", "Higiene", "Estudo ou trabalho"] },
        { "id": "ter_b2", "nome": "Almoço", "horario": "12:00 - 13:30",
          "tarefas": ["Almoço sem pressa", "Descansar um pouco"] },
        { "id": "ter_b3", "nome": "Tarde", "horario": "13:30 - 18:00",
          "tarefas": ["Estudo ou trabalho", "Pausa curta"] },
        { "id": "ter_b4", "nome": "Manutenção", "horario": "18:00 - 18:30",
          "tarefas": ["Arrumar o quarto"] },
        { "id": "ter_b5", "nome": "Noite", "horario": "18:30 - 22:00",
          "tarefas": ["Jantar", "Lazer", "Preparar o sono"] }
      ]
    },
    {
      "id": "qua",
      "nome": "Quarta",
      "dias": [3],
      "blocos": [
        { "id": "qua_b1", "nome": "Manhã", "horario": "06:00 - 12:00",
          "tarefas": ["Café da manhã", "Higiene", "Estudo ou trabalho"] },
        { "id": "qua_b2", "nome": "Almoço", "horario": "12:00 - 13:30",
          "tarefas": ["Almoço sem pressa", "Descansar um pouco"] },
        { "id": "qua_b3", "nome": "Tarde", "horario": "13:30 - 18:00",
          "tarefas": ["Estudo ou trabalho", "Pausa curta"] },
        { "id": "qua_b4", "nome": "Noite", "horario": "18:00 - 22:00",
          "tarefas": ["Jantar", "Lazer", "Preparar o sono"] }
      ]
    },
    {
      "id": "qui",
      "nome": "Quinta",
      "dias": [4],
      "blocos": [
        { "id": "qui_b1", "nome": "Manhã", "horario": "06:00 - 12:00",
          "tarefas": ["Café da manhã", "Higiene", "Estudo ou trabalho"] },
        { "id": "qui_b2", "nome": "Almoço", "horario": "12:00 - 13:30",
          "tarefas": ["Almoço sem pressa", "Descansar um pouco"] },
        { "id": "qui_b3", "nome": "Tarde", "horario": "13:30 - 18:00",
          "tarefas": ["Estudo ou trabalho", "Pausa curta"] },
        { "id": "qui_b4", "nome": "Manutenção", "horario": "18:00 - 18:30",
          "tarefas": ["Lavar a roupa"] },
        { "id": "qui_b5", "nome": "Noite", "horario": "18:30 - 22:00",
          "tarefas": ["Jantar", "Lazer", "Preparar o sono"] }
      ]
    },
    {
      "id": "sex",
      "nome": "Sexta",
      "dias": [5],
      "blocos": [
        { "id": "sex_b1", "nome": "Manhã", "horario": "06:00 - 12:00",
          "tarefas": ["Café da manhã", "Higiene", "Estudo ou trabalho"] },
        { "id": "sex_b2", "nome": "Almoço", "horario": "12:00 - 13:30",
          "tarefas": ["Almoço sem pressa", "Descansar um pouco"] },
        { "id": "sex_b3", "nome": "Tarde", "horario": "13:30 - 18:00",
          "tarefas": ["Estudo ou trabalho", "Pausa curta"] },
        { "id": "sex_b4", "nome": "Manutenção", "horario": "18:00 - 18:30",
          "tarefas": ["Organizar a mochila"] },
        { "id": "sex_b5", "nome": "Noite", "horario": "18:30 - 22:00",
          "tarefas": ["Jantar", "Lazer", "Preparar o sono"] }
      ]
    },
    {
      "id": "sab",
      "nome": "Sábado",
      "dias": [6],
      "blocos": [
        { "id": "sab_b1", "nome": "Manhã livre", "horario": "08:00 - 12:00",
          "tarefas": ["Café da manhã tranquilo", "Estudo ou projeto pessoal"] },
        { "id": "sab_b2", "nome": "Tarde e noite livre", "horario": "12:00 em diante",
          "tarefas": ["Almoço", "Lazer", "Família ou amigos"] }
      ]
    },
    {
      "id": "dom",
      "nome": "Domingo",
      "dias": [7],
      "blocos": [
        { "id": "dom_b1", "nome": "Dia de descanso", "horario": "08:00 - 19:00",
          "tarefas": ["Almoço em família", "Lazer", "Preparar a semana"] },
        { "id": "dom_b2", "nome": "Noite", "horario": "20:00 - 22:00",
          "tarefas": ["Preparar o sono", "Cama às 22:00"] }
      ]
    }
  ]
}"""

    /**
     * The bundled example, parsed per call site. Falls back to a blank
     * 7-day routine if the constant were ever corrupted — the app must
     * never crash on first launch.
     */
    fun routine(): Routine =
        (RoutineJson.parse(JSON) as? RoutineParseResult.Ok)?.routine ?: scratch()

    /** A blank per-day routine ("começar do zero"): 7 empty days. */
    fun scratch(): Routine = Routine(
        versao = "1.0",
        dias = listOf(
            RoutineDay("seg", "Segunda", emptyList(), weekdays = setOf(1)),
            RoutineDay("ter", "Terça", emptyList(), weekdays = setOf(2)),
            RoutineDay("qua", "Quarta", emptyList(), weekdays = setOf(3)),
            RoutineDay("qui", "Quinta", emptyList(), weekdays = setOf(4)),
            RoutineDay("sex", "Sexta", emptyList(), weekdays = setOf(5)),
            RoutineDay("sab", "Sábado", emptyList(), weekdays = setOf(6)),
            RoutineDay("dom", "Domingo", emptyList(), weekdays = setOf(7)),
        ),
    )
}
