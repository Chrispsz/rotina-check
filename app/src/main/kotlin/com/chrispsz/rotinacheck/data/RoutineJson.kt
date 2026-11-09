package com.chrispsz.rotinacheck.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * org.json based codec for routine files and backups.
 *
 * Why org.json instead of a serialization library: it ships with the
 * platform (zero APK bytes, zero extra dependency) and the grammar is tiny.
 * The parser is deliberately lenient — every block survives on its own, and
 * unparseable pieces surface as PT warnings instead of failing the import.
 *
 * Accepted inputs:
 *  1. Plain routine (`diasSemana` array) — the documented public format.
 *  2. RotinaCheck backup (`{ "rotina": {...}, "config": {...}, "historico": ... }`).
 */
sealed interface RoutineParseResult {
    data class Ok(val routine: Routine, val warnings: List<String>) : RoutineParseResult
    data class Err(val message: String) : RoutineParseResult
}

object RoutineJson {

    // ---- User-facing (PT) error messages ------------------------------------
    const val ERR_EMPTY = "arquivo vazio"
    const val ERR_MALFORMED = "JSON malformado"
    const val ERR_NO_DAYS = "campo \"diasSemana\" ausente"

    /** Full backup snapshot — routine + settings + check history (lossless). */
    data class Backup(
        val routine: Routine,
        val warnings: List<String>,
        val alertsOn: Boolean?,
        val checks: Set<String>?,
    )

    fun parse(text: String): RoutineParseResult {
        if (text.isBlank()) return RoutineParseResult.Err(ERR_EMPTY)
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return RoutineParseResult.Err(ERR_MALFORMED)
        }
        // Backup file? Extract the inner routine; settings/history are
        // handled by [parseBackup].
        val routineRoot = root.optJSONObject("rotina") ?: root
        return parseRoutineRoot(routineRoot)
    }

    /**
     * Backup-aware parse: returns `null` only for blank/malformed/invalid
     * structures — plain routines parse with `null` settings/history.
     */
    fun parseBackup(text: String): Backup? {
        if (text.isBlank()) return null
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return null
        }
        val parsed = when (val result = parseRoutineRoot(root.optJSONObject("rotina") ?: root)) {
            is RoutineParseResult.Ok -> result
            is RoutineParseResult.Err -> return null
        }
        val config = root.optJSONObject("config")
        val checksArr = root.optJSONObject("historico")?.optJSONArray("checks")
        val checks = checksArr?.let { arr ->
            buildSet { for (i in 0 until arr.length()) add(arr.optString(i)) }
        }
        // Unknown config keys (e.g. "pomodoroUrl" from old backups) are
        // silently ignored — imports stay forward-compatible.
        return Backup(
            routine = parsed.routine,
            warnings = parsed.warnings,
            alertsOn = config?.optBoolean("alertas", true),
            checks = checks,
        )
    }

    private fun parseRoutineRoot(root: JSONObject): RoutineParseResult {
        val daysArr = root.optJSONArray("diasSemana")
            ?: return RoutineParseResult.Err(ERR_NO_DAYS)
        if (daysArr.length() == 0) return RoutineParseResult.Err(ERR_NO_DAYS)

        val warnings = mutableListOf<String>()
        val days = buildList {
            for (d in 0 until daysArr.length()) {
                val dayJson = daysArr.optJSONObject(d) ?: continue
                val dayId = dayJson.optString("id").ifBlank { "dia${d + 1}" }
                val dayName = dayJson.optString("nome").ifBlank { dayId }
                val weekdays = parseWeekdays(dayJson.optJSONArray("dias"))
                val blocksArr = dayJson.optJSONArray("blocos") ?: JSONArray()
                val blocks = buildList {
                    for (b in 0 until blocksArr.length()) {
                        val blockJson = blocksArr.optJSONObject(b) ?: continue
                        val nome = blockJson.optString("nome").ifBlank { "Bloco ${b + 1}" }
                        val horarioTexto = blockJson.optString("horario").trim()
                        val range = TimeRange.parse(horarioTexto)
                        if (horarioTexto.isNotEmpty() && range == null) {
                            warnings += "Horário não reconhecido em \"$nome\": \"$horarioTexto\""
                        }
                        val tarefas = blockJson.optJSONArray("tarefas")?.let { arr ->
                            buildList {
                                for (i in 0 until arr.length()) {
                                    // Strict String check: optString would stringify
                                    // numbers/booleans into fake tasks.
                                    val value = arr.opt(i)
                                    if (value is String) {
                                        val t = value.trim()
                                        if (t.isNotEmpty()) add(t)
                                    }
                                }
                            }
                        } ?: emptyList()
                        add(
                            RoutineBlock(
                                id = blockJson.optString("id").ifBlank { "${dayId}_b${b + 1}" },
                                nome = nome,
                                horarioTexto = horarioTexto,
                                startMin = range?.startMin,
                                endMin = range?.endMin,
                                tarefas = tarefas,
                            ),
                        )
                    }
                }
                add(RoutineDay(id = dayId, nome = dayName, blocos = blocks, weekdays = weekdays))
            }
        }
        val routine = Routine(versao = root.optString("versao").ifBlank { "1.0" }, dias = days)
        return RoutineParseResult.Ok(routine, warnings)
    }

    /**
     * Per-day mapping: `"dias": [1..7]` (ISO, 1 = Monday … 7 = Sunday).
     * Tolerates numeric strings; anything else is ignored. `null` keeps the
     * legacy weekday/weekend heuristic — both formats import cleanly.
     */
    private fun parseWeekdays(arr: JSONArray?): Set<Int>? {
        if (arr == null || arr.length() == 0) return null
        val set = buildSet {
            for (i in 0 until arr.length()) {
                when (val v = arr.opt(i)) {
                    is Int -> if (v in 1..7) add(v)
                    is String -> v.trim().toIntOrNull()?.let { if (it in 1..7) add(it) }
                }
            }
        }
        return set.ifEmpty { null }
    }

    // ---- Serialization -------------------------------------------------------

    /** Canonical routine JSON (the same shape users import). */
    fun toRoutineJson(routine: Routine): String {
        val days = JSONArray()
        routine.dias.forEach { day ->
            val blocks = JSONArray()
            day.blocos.forEach { b ->
                val tasks = JSONArray()
                b.tarefas.forEach { tasks.put(it) }
                blocks.put(
                    JSONObject()
                        .put("id", b.id)
                        .put("nome", b.nome)
                        .put("horario", b.horarioTexto)
                        .put("tarefas", tasks),
                )
            }
            val dayJson = JSONObject()
                .put("id", day.id)
                .put("nome", day.nome)
            // Legacy days (weekdays == null) round-trip without the field,
            // so old files stay byte-compatible after export.
            day.weekdays?.let { weekdays ->
                val arr = JSONArray()
                weekdays.sorted().forEach { arr.put(it) }
                dayJson.put("dias", arr)
            }
            dayJson.put("blocos", blocks)
            days.put(dayJson)
        }
        return JSONObject()
            .put("versao", routine.versao)
            .put("diasSemana", days)
            .toString(2)
    }

    /**
     * Full local backup: routine + toggles + check history. Export → import
     * must be lossless (verified by RoutineJsonTest).
     */
    fun toBackupJson(
        routine: Routine,
        alertsOn: Boolean,
        checks: Set<String>,
        exportedAtIso: String,
    ): String {
        val checksArr = JSONArray()
        checks.sorted().forEach { checksArr.put(it) }
        return JSONObject()
            .put("app", "RotinaCheck")
            .put("versao", 1)
            .put("exportadoEm", exportedAtIso)
            .put("rotina", JSONObject(toRoutineJson(routine)))
            .put("config", JSONObject().put("alertas", alertsOn))
            .put("historico", JSONObject().put("checks", checksArr))
            .toString(2)
    }
}
