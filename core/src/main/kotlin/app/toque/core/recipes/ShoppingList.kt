package app.toque.core.recipes

import app.toque.core.json.Json

/**
 * Liste de courses d'une recette, telle qu'elle est affichée (nombre de personnes et prorata
 * compris), à envoyer à Home Assistant. Un même ingrédient présent plusieurs fois est additionné ;
 * les précisions de préparation (« , coupé en dés ») sont retirées.
 */
object ShoppingList {
    data class Item(val text: String, val checked: Boolean)

    fun items(r: Recipe): List<Item> {
        class Line(val name: String, val unit: String, var qty: Double?, var raw: String, val checked: Boolean)
        val lines = LinkedHashMap<String, Line>()
        for (i in r.allIngredients) {
            val name = QuantityLinks.keyName(i.name).substringBefore(',').substringBefore(';').trim().ifEmpty { i.name.trim() }
            if (name.isEmpty()) continue
            val unit = i.unit.trim()
            val key = QuantityLinks.norm(name) + "|" + QuantityLinks.norm(unit)
            val q = QuantityLinks.parse(i.qty)
            val prev = lines[key]
            if (prev == null) {
                // Le poids entre parenthèses (« 2 échalotes (60 g) ») reste utile au magasin.
                val shown = if (i.name.contains('(') && QuantityLinks.keyName(i.name) != i.name.trim()) i.name.substringBefore(',').trim() else name
                lines[key] = Line(shown, unit, q, i.qty.trim(), !pantry(i))
            } else if (prev.qty != null && q != null) {
                prev.qty = prev.qty!! + q
                prev.raw = ""
            } else if (q != null || i.qty.isNotBlank()) {
                prev.raw = listOf(prev.raw, i.qty.trim()).filter { it.isNotEmpty() }.joinToString(" + ")
                prev.qty = null
            }
        }
        return lines.values.map { l ->
            val amount = when {
                l.qty != null && l.raw.isNotEmpty() -> amount(l.raw, l.qty!!, l.unit)
                l.qty != null -> amount(null, l.qty!!, l.unit)
                l.raw.isNotEmpty() -> listOf(l.raw, l.unit).filter { it.isNotEmpty() }.joinToString(" ")
                else -> ""
            }
            Item(listOf(amount, l.name).filter { it.isNotEmpty() }.joinToString(" "), l.checked)
        }
    }

    /** « 1200 g » → « 1,2 kg », « 1500 ml » → « 1,5 l » ; les fractions de la recette restent écrites telles quelles. */
    private fun amount(raw: String?, q: Double, unit: String): String {
        val u = unit.lowercase()
        if ((u == "g" || u == "ml") && q >= 1000) return number(q / 1000) + (if (u == "g") " kg" else " l")
        val n = if (raw != null && raw.any { it in "¼½¾" }) raw else if (u.isEmpty() || u.startsWith("c") || u.startsWith("cuil")) Proportions.fraction(q, 4) else number(q)
        return listOf(n, unit).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun number(v: Double): String {
        val r = Math.round(v * 100) / 100.0
        return if (r == Math.floor(r)) r.toLong().toString() else r.toString().replace('.', ',')
    }

    /** Eau, sel, poivre, pincées : rarement à acheter, décochés par défaut. */
    fun pantry(i: Ingredient): Boolean {
        val all = QuantityLinks.norm(i.unit + " " + i.name)
        if (all.contains("pince")) return true
        val first = QuantityLinks.keywords(i.name).firstOrNull()
        return first == "eau" || first == "sel" || first == "poivre" || first == "glacon"
    }

    /** Corps envoyé au webhook : la recette, le nombre de personnes (0 : inconnu) et les articles. */
    fun payload(recipe: String, servings: Int, items: List<String>): String = Json.write(linkedMapOf(
        "recipe" to recipe,
        "servings" to servings,
        "items" to items,
        "text" to items.joinToString("\n"),
    ))

    /**
     * Adresse du webhook : « 192.168.1.20 » → « http://192.168.1.20:8123/api/webhook/toque_courses ».
     * Null si l'adresse ou l'identifiant est vide ou invalide.
     */
    fun webhookUrl(base: String, id: String): String? {
        var b = base.trim().trimEnd('/')
        val hook = id.trim()
        if (b.isEmpty() || !hook.matches(Regex("[A-Za-z0-9_-]+"))) return null
        if (!b.contains("://")) {
            val host = b.substringBefore('/')
            b = "http://" + (if (host.contains(':')) b else host + ":8123" + b.removePrefix(host))
        }
        if (!b.startsWith("http://") && !b.startsWith("https://")) return null
        return "$b/api/webhook/$hook"
    }
}
