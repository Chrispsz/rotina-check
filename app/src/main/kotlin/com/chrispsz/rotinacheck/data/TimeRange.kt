package com.chrispsz.rotinacheck.data

/**
 * A time window parsed from a routine block's free-form `horario` text.
 *
 * Supported source shapes (see [parse]):
 *  * `"05:00 - 12:30"`  → start + end
 *  * `"12:00 em diante"` → start only (open-ended block, no END alert)
 *  * `"21:30"`           → start only
 *
 * Minutes-of-day ([0, 1439]) is the canonical representation everywhere in
 * the app: comparisons, scheduling math and formatting stay integer-exact —
 * zero floating point, zero locale drift.
 */
data class TimeRange(val startMin: Int, val endMin: Int?) {

    /** `"07:30 - 11:30"` / `"12:00 em diante"` — locale-independent digits. */
    fun format(): String = label(startMin) + when (endMin) {
        null -> " em diante"
        else -> " - " + label(endMin)
    }

    fun contains(minutesOfDay: Int): Boolean =
        endMin != null && minutesOfDay >= startMin && minutesOfDay < endMin

    companion object {
        private val TIME = Regex("""(\d{1,2}):(\d{2})""")

        /**
         * Lenient parser: never throws. Returns `null` when no valid time can
         * be extracted (the block is still shown — it simply has no schedule,
         * so it emits no alerts).
         */
        fun parse(raw: String?): TimeRange? {
            if (raw.isNullOrBlank()) return null
            val matches = TIME.findAll(raw).toList()
            if (matches.isEmpty()) return null

            val first = toMinutes(matches[0]) ?: return null
            if (matches.size < 2) return TimeRange(first, null)

            val second = toMinutes(matches[1])
            // Second time invalid or not after the first → degrade to an
            // open-ended block instead of dropping the schedule entirely.
            return if (second != null && second > first) TimeRange(first, second)
            else TimeRange(first, null)
        }

        fun label(minutesOfDay: Int): String {
            val h = (minutesOfDay / 60).toString().padStart(2, '0')
            val m = (minutesOfDay % 60).toString().padStart(2, '0')
            return "$h:$m"
        }

        private fun toMinutes(match: MatchResult): Int? {
            val h = match.groupValues[1].toInt()
            val m = match.groupValues[2].toInt()
            if (h !in 0..23 || m !in 0..59) return null
            return h * 60 + m
        }
    }
}
