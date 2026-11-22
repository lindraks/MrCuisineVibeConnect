package app.toque.core.recipes

/**
 * Texte d'une étape avec ses quantités : « Mettez {{0}} dans le bol » → « Mettez 2 oignons dans le
 * bol ». Les repères viennent du serveur maison ([Step.template], [Step.refs]) ; chaque quantité suit
 * la recette telle qu'elle est affichée (nombre de personnes, balance, quantités corrigées), comme la
 * liste d'ingrédients. Sans repères, le texte de l'étape est rendu tel quel.
 */
object StepText {
    /** Morceau du texte ; [quantity] : une quantité d'ingrédient (à mettre en valeur). */
    class Part(val text: String, val quantity: Boolean)

    private val MARK = Regex("\\{\\{(\\d+)\\}\\}")

    fun parts(step: Step, recipe: Recipe): List<Part> {
        if (step.template.isEmpty() || step.refs.isEmpty()) return listOf(Part(step.text, false))
        val items = recipe.allIngredients
        val out = ArrayList<Part>()
        var last = 0
        for (m in MARK.findAll(step.template)) {
            val ref = step.refs.getOrNull(m.groupValues[1].toInt())
            val ing = ref?.let { items.getOrNull(it.index) }
            // Repère qui ne correspond à rien (recette modifiée) : texte d'origine de l'étape.
            if (ref == null || ing == null) return listOf(Part(step.text, false))
            if (m.range.first > last) out += Part(step.template.substring(last, m.range.first), false)
            out += Part(mention(ref, ing), true)
            last = m.range.last + 1
        }
        if (last < step.template.length) out += Part(step.template.substring(last), false)
        return out
    }

    fun render(step: Step, recipe: Recipe): String = parts(step, recipe).joinToString("") { it.text }

    /** Rapport entre la quantité affichée de l'ingrédient et celle de la recette (1 si inchangée). */
    fun factor(i: Ingredient): Double {
        if (i.originalQty.isBlank()) return 1.0
        val now = QuantityLinks.parse(i.qty) ?: return 1.0
        val was = QuantityLinks.parse(i.originalQty) ?: return 1.0
        return if (was > 0) now / was else 1.0
    }

    fun mention(ref: QtyRef, ing: Ingredient): String {
        val f = factor(ing)
        if (ref.qty.isNotBlank()) {
            val scaled = if (f == 1.0) null else Proportions.scaledQty(Ingredient(ref.qty, ref.unit, ing.name), f)
            return phrase(scaled ?: ref.qty.trim(), ref.unit.trim(), ref.name.trim())
        }
        val base = Ingredient(ing.originalQty.ifBlank { ing.qty }, ing.unit, ing.name)
        val q = Proportions.scaledQty(base, f * ref.part) ?: ing.qty.trim()
        return phrase(q, ing.unit.trim(), shortName(ing.name))
    }

    /** « marrons (sous-vide), précuits » → « marrons ». */
    fun shortName(name: String): String {
        val n = name.substringBefore(',').substringBefore('(').substringBefore(';').trim().ifEmpty { name.trim() }
        return n.replaceFirstChar { it.lowercaseChar() }
    }

    private fun phrase(qty: String, unit: String, name: String): String = when {
        qty.isEmpty() -> name
        unit.isEmpty() -> "$qty $name"
        else -> "$qty $unit " + (if (elided(name)) "d'" else "de ") + name
    }

    private fun elided(name: String): Boolean = name.firstOrNull()?.lowercaseChar()?.let { it in "aeiouyhâàéèêëîïôöûüœ" } ?: false
}
