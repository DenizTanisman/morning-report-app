package app.morningreport

/** When automatic checks read a source. Manual refresh ignores this and reads everything. */
object Schedule {
    private val HHMM = Regex("^([01]?\\d|2[0-3]):([0-5]\\d)$")

    /** "7:5" / "07:05" / "0705" → "07:05"; anything else → null. */
    fun normalize(input: String): String? {
        val s = input.trim().let { if (Regex("^\\d{3,4}$").matches(it)) it.dropLast(2) + ":" + it.takeLast(2) else it }
        val m = HHMM.find(s) ?: return null
        return m.groupValues[1].padStart(2, '0') + ":" + m.groupValues[2]
    }

    /**
     * Without a time a source is due on every automatic check (at most every [minGapMs]).
     * With a time it is due once a day: from that time on, if it was not checked since.
     */
    fun isDue(source: Source, now: Long, minGapMs: Long = 25 * 60_000L): Boolean {
        val t = source.time?.let(::normalize) ?: return now - source.lastChecked >= minGapMs
        val (_, _, dayStart) = localTime(now)
        val at = dayStart + (t.substring(0, 2).toInt() * 60 + t.substring(3).toInt()) * 60_000L
        return now >= at && source.lastChecked < at
    }
}
