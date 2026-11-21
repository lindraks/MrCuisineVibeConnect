package app.toque.core

import app.toque.core.recipes.Duplicates
import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.IngredientGroup
import app.toque.core.recipes.Proportions
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.RecipeAdjustments
import app.toque.core.recipes.RecipeFilter
import app.toque.core.recipes.SearchIndex
import app.toque.core.recipes.StepTimers
import app.toque.core.recipes.adjusted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeExtrasTest {
    private fun r(id: String, title: String, vararg ing: String, rating: Double = 0.0, count: Int = 0) = Recipe(
        id = id, title = title, rating = rating, ratingCount = count,
        ingredients = listOf(IngredientGroup("", ing.map { Ingredient(name = it) })),
    )

    // ------------------------------------------------------------------ doublons

    @Test fun sameTitleSameIngredientsIsOneRecipe() {
        val a = r("web-1", "Aïoli", "4 gousses d'ail", "1 jaune d'oeuf", "250 ml d'huile d'olive", "sel", rating = 4.5, count = 40)
        val b = r("web-2", "Aioli", "3 gousses d'ail", "1 jaune d'œuf", "200 ml d'huile d'olive", "1 pincée de sel", rating = 4.8, count = 3)
        val other = r("web-3", "Aïoli", "ail", "pommes de terre", "morue", "carottes", "haricots verts")
        val shown = Duplicates.collapse(listOf(a, b, other))
        // a et b : même recette, la plus notée (40 avis) est gardée ; l'aïoli « plat complet » reste.
        assertEquals(listOf("web-1", "web-3"), shown.map { it.id })
    }

    @Test fun aFavouriteCopyWins() {
        val a = r("web-1", "Baguettes", "500 g de farine", "300 g d'eau", "10 g de sel", "10 g de levure", count = 90, rating = 4.4)
        val b = r("web-2", "baguettes", "500 g de farine", "320 g d'eau", "10 g de sel", "8 g de levure", count = 2, rating = 4.0)
        val shown = Duplicates.collapse(listOf(a, b)) { if (it.id == "web-2") 1 else 0 }
        assertEquals(listOf("web-2"), shown.map { it.id })
    }

    @Test fun differentTitlesAreNeverMerged() {
        val a = r("web-1", "Biscuits aux noisettes", "farine", "noisettes", "beurre", "sucre")
        val b = r("web-2", "Biscuits aux amandes", "farine", "amandes", "beurre", "sucre")
        assertEquals(2, Duplicates.collapse(listOf(a, b)).size)
    }

    // ------------------------------------------------------------------ classement

    @Test fun manyGoodReviewsBeatAFewPerfectOnes() {
        val idx = SearchIndex(listOf(
            r("a", "A", rating = 5.0, count = 2),
            r("b", "B", rating = 4.7, count = 900),
            r("c", "C", rating = 3.2, count = 50),
            r("d", "D"),
        ))
        assertEquals(listOf("b", "a", "d", "c"), idx.search("").map { it.entry.id })
        assertEquals(listOf("a", "b", "c", "d"), idx.search("", RecipeFilter(alphabetical = true)).map { it.entry.id })
    }

    // ------------------------------------------------------------------ ingrédients

    @Test fun ingredientQueries() {
        val idx = SearchIndex(emptyList())
        assertEquals(listOf("butternut", "lardons"), idx.ingredientsOf("butternut et lardons"))
        assertEquals(listOf("pomme de terre", "crème"), idx.ingredientsOf("pomme de terre, crème"))
    }

    @Test fun recipesWithMoreOfMyIngredientsComeFirst() {
        val idx = SearchIndex(listOf(
            r("soup", "Velouté", "butternut", "oignon", "crème", rating = 4.0, count = 10),
            r("pasta", "Pâtes", "lardons", "crème", "pâtes", rating = 4.9, count = 500),
            r("gratin", "Gratin", "butternut", "lardons", "fromage", rating = 3.5, count = 5),
        ))
        val hits = idx.withIngredients(idx.ingredientsOf("butternut, lardons"))
        assertEquals("gratin", hits.first().entry.id)
        assertEquals(2, hits.first().matched)
        assertEquals(listOf("pasta", "soup"), hits.drop(1).map { it.entry.id })   // 1 ingrédient chacune : la mieux notée d'abord
    }

    // ------------------------------------------------------------------ portions

    @Test fun servingsScaleEveryQuantity() {
        val base = Recipe(id = "web-9", title = "Soupe", servings = 4, ingredients = listOf(IngredientGroup("", listOf(
            Ingredient("800", "g", "butternut"), Ingredient("2", "", "oignons"), Ingredient(name = "1 pincée de sel"),
        ))))
        val six = base.adjusted(Proportions.byFactor(base, RecipeAdjustments(), 6 / 4.0))
        assertEquals(listOf("1200", "3", "1"), six.allIngredients.map { it.qty })
        assertTrue(Proportions.byFactor(base, RecipeAdjustments(), 1.0).isEmpty)
    }

    // ------------------------------------------------------------------ minuteurs

    @Test fun durationsInStepText() {
        assertEquals(listOf(600), StepTimers.durations("Laisser reposer 10 min."))
        assertEquals(listOf(4500), StepTimers.durations("Enfourner 1 h 15 à 180 °C."))
        assertEquals(listOf(5400), StepTimers.durations("Cuire 1h30 au four"))
        assertEquals(listOf(5400), StepTimers.durations("Laisser lever 1 heure et demie."))
        assertEquals(listOf(120), StepTimers.durations("Faire revenir 2 à 3 minutes."))
        assertEquals(listOf(30, 900), StepTimers.durations("Mixer 30 secondes puis laisser reposer 15 minutes."))
        assertEquals(listOf(7200), StepTimers.durations("Réfrigérer pendant 2 heures."))
        assertEquals(emptyList<Int>(), StepTimers.durations("Ajouter 2 œufs et 150 g de sucre, 3 c. à s. d'huile."))
        assertEquals(emptyList<Int>(), StepTimers.durations("Préchauffer le four à 180 °C (th. 6)."))
    }

    @Test fun timerLabels() {
        assertEquals("10 min", StepTimers.label(600))
        assertEquals("1 h 15", StepTimers.label(4500))
        assertEquals("45 s", StepTimers.label(45))
        assertEquals("9:05", StepTimers.clock(545))
        assertEquals("1:02:30", StepTimers.clock(3750))
    }
}
