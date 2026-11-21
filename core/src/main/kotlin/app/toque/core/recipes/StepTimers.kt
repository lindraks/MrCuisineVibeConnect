package app.toque.core.recipes

/**
 * Durées écrites dans le texte d'une étape à faire à la main (« laisser reposer 10 min »,
 * « enfourner 1 h 15 », « 2 à 3 minutes ») : de quoi proposer un minuteur. Une fourchette
 * donne sa plus petite valeur, pour vérifier tôt plutôt que trop tard.
 */
object StepTimers {
    /** 1 s à 24 h : au-delà, ce n'est pas une durée à minuter (« 48 h au réfrigérateur » si, jusqu'à 24 h). */
    const val MAX_SECONDS = 24 * 3600

    private const val N = "(\\d+(?:[.,]\\d+)?)"
    private const val UPTO = "(?:\\s*(?:à|a|-|–|ou)\\s*\\d+(?:[.,]\\d+)?)?"
    /** « 1 h 30 », « 1h30 », « 2 heures », « 1 heure et demie », « 1 h 30 min ». */
    private val HOURS = Regex(
        "(?<![\\d.,])$N$UPTO\\s*(?:h(?![a-zà-ÿ])|heures?)(?:\\s*(?:et\\s+)?(demie|quart|\\d{1,2})(?:\\s*(?:min(?:utes?)?|mn)\\b)?)?",
        RegexOption.IGNORE_CASE,
    )
    /** « 10 min », « 10 minutes », « 2-3 mn ». */
    private val MINUTES = Regex("(?<![\\d.,])$N$UPTO\\s*(?:min(?:utes?)?|mn)\\b", RegexOption.IGNORE_CASE)
    /** « 30 secondes », « 30 s », « 45 sec ». */
    private val SECONDS = Regex("(?<![\\d.,])$N$UPTO\\s*(?:secondes?|sec|s)(?![a-zà-ÿ])", RegexOption.IGNORE_CASE)

    private fun num(s: String): Double = s.replace(',', '.').toDouble()

    /** Durées trouvées, en secondes, dans l'ordre du texte, sans doublon. */
    fun durations(text: String): List<Int> {
        val found = ArrayList<Pair<Int, Int>>() // position → secondes
        val taken = ArrayList<IntRange>()
        fun free(r: IntRange) = taken.none { it.first <= r.last && r.first <= it.last }
        for (m in HOURS.findAll(text)) {
            var s = num(m.groupValues[1]) * 3600
            when (val extra = m.groupValues[2].lowercase()) {
                "" -> {}
                "demie" -> s += 1800
                "quart" -> s += 900
                else -> s += extra.toInt() * 60
            }
            taken += m.range
            found += m.range.first to Math.round(s).toInt()
        }
        for (m in MINUTES.findAll(text)) if (free(m.range)) {
            taken += m.range
            found += m.range.first to Math.round(num(m.groupValues[1]) * 60).toInt()
        }
        for (m in SECONDS.findAll(text)) if (free(m.range)) {
            taken += m.range
            found += m.range.first to Math.round(num(m.groupValues[1])).toInt()
        }
        return found.sortedBy { it.first }.map { it.second }.filter { it in 1..MAX_SECONDS }.distinct()
    }

    /** 600 → « 10 min », 4500 → « 1 h 15 », 45 → « 45 s », 90 → « 1 min 30 ». */
    fun label(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> if (m > 0) "$h h ${m.toString().padStart(2, '0')}" else "$h h"
            m > 0 -> if (s > 0) "$m min ${s.toString().padStart(2, '0')}" else "$m min"
            else -> "$s s"
        }
    }

    /** Compte à rebours : « 9:05 », « 1:02:30 ». */
    fun clock(seconds: Int): String {
        val t = seconds.coerceAtLeast(0)
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
        else "$m:${s.toString().padStart(2, '0')}"
    }
}
