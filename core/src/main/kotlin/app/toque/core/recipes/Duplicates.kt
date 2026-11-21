package app.toque.core.recipes

/**
 * Doublons du catalogue : la même recette publiée plusieurs fois sous le même titre (souvent une
 * recette officielle refaite pour le Smart, ou republiée). Même titre (casse, accents et
 * ponctuation ignorés) et ingrédients presque identiques : une seule est montrée.
 */
object Duplicates {
    /** Part d'ingrédients communs (Jaccard) au-delà de laquelle deux recettes de même titre sont la même. */
    const val SIMILAR = 0.6

    /** Ingrédients comparables : le premier mot significatif de chacun (« crème liquide (30 %) » → creme). */
    fun ingredientKeys(r: Recipe): Set<String> =
        r.allIngredients.mapNotNull { QuantityLinks.keywords(QuantityLinks.split(it).name).firstOrNull() }.toSet()

    fun similar(a: Set<String>, b: Set<String>): Boolean {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty()
        val common = a.intersect(b).size.toDouble()
        return common / (a.size + b.size - common) >= SIMILAR
    }

    /**
     * Le catalogue sans ses doublons, dans l'ordre d'origine. Dans chaque groupe, la recette gardée
     * est celle que [preferred] classe le plus haut (favori, déjà cuisinée…), puis la plus notée,
     * puis celle qui a le plus de réglages machine.
     */
    fun collapse(recipes: List<Recipe>, preferred: (Recipe) -> Int = { 0 }): List<Recipe> {
        val byTitle = LinkedHashMap<String, MutableList<Recipe>>()
        for (r in recipes) byTitle.getOrPut(RecipeAdjustments.titleKey(r.title)) { ArrayList(1) } += r
        val hidden = HashSet<String>()
        val best = compareByDescending<Recipe> { preferred(it) }
            .thenByDescending { it.ratingCount }
            .thenByDescending { it.rating }
            .thenByDescending { it.machineSteps }
            .thenBy { it.id }
        for (group in byTitle.values) {
            if (group.size < 2) continue
            val keys = group.associateWith { ingredientKeys(it) }
            // Grappes de recettes semblables, chacune comparée à la première de la grappe.
            val clusters = ArrayList<MutableList<Recipe>>()
            for (r in group) {
                val home = clusters.firstOrNull { similar(keys.getValue(it[0]), keys.getValue(r)) }
                if (home != null) home += r else clusters += arrayListOf(r)
            }
            for (cl in clusters) {
                if (cl.size < 2) continue
                val keep = cl.sortedWith(best).first()
                for (r in cl) if (r !== keep) hidden += r.id
            }
        }
        return if (hidden.isEmpty()) recipes else recipes.filter { it.id !in hidden }
    }
}
