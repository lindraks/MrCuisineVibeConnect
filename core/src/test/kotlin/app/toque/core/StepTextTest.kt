package app.toque.core

import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.IngredientGroup
import app.toque.core.recipes.Proportions
import app.toque.core.recipes.QtyRef
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.RecipeAdjustments
import app.toque.core.recipes.RecipeCodec
import app.toque.core.recipes.RecipeFilter
import app.toque.core.recipes.SearchIndex
import app.toque.core.recipes.Step
import app.toque.core.recipes.StepText
import app.toque.core.recipes.adjusted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StepTextTest {
    private val recipe = Recipe(
        id = "ckx-test",
        title = "Velouté test",
        servings = 4,
        ingredients = listOf(IngredientGroup("", listOf(
            Ingredient("400", "g", "Marrons (sous-vide)"),
            Ingredient("2", "", "Oignons jaunes"),
            Ingredient("1", "pincée", "Sel"),
            Ingredient("100", "g", "Beurre"),
        ))),
        steps = listOf(
            Step("Mettre 400 g de marrons dans le bol.", template = "Mettre {{0}} dans le bol.",
                refs = listOf(QtyRef(0, "400", "g", "marrons"))),
            Step("Ajouter les oignons et le sel.", template = "Ajouter {{0}} et {{1}}.",
                refs = listOf(QtyRef(1), QtyRef(2))),
            Step("Ajouter la moitié du beurre.", template = "Ajouter {{0}}.", refs = listOf(QtyRef(3, part = 0.5))),
            Step("Mixer."),
        ),
        categories = listOf("Soupes et veloutés", "Plats végétariens"),
        filters = listOf("Végétarien"),
    )

    @Test fun quantitiesAreWrittenIntoTheSteps() {
        assertEquals("Mettre 400 g de marrons dans le bol.", StepText.render(recipe.steps[0], recipe))
        assertEquals("Ajouter 2 oignons jaunes et 1 pincée de sel.", StepText.render(recipe.steps[1], recipe))
        assertEquals("Ajouter 50 g de beurre.", StepText.render(recipe.steps[2], recipe))
        assertEquals("Mixer.", StepText.render(recipe.steps[3], recipe))
        assertTrue(StepText.parts(recipe.steps[1], recipe).count { it.quantity } == 2)
    }

    @Test fun quantitiesFollowTheNumberOfServings() {
        val six = recipe.adjusted(Proportions.byFactor(recipe, RecipeAdjustments(), 1.5))
        assertEquals("Mettre 600 g de marrons dans le bol.", StepText.render(six.steps[0], six))
        assertEquals("Ajouter 3 oignons jaunes et 1 pincée de sel.", StepText.render(six.steps[1], six))
        assertEquals("Ajouter 75 g de beurre.", StepText.render(six.steps[2], six))
    }

    @Test fun anUnknownMarkKeepsTheOriginalText() {
        val broken = Step("Texte d'origine.", template = "Mettre {{4}}.", refs = listOf(QtyRef(9)))
        assertEquals("Texte d'origine.", StepText.render(broken, recipe))
    }

    @Test fun codecKeepsMarksCategoriesAndFilters() {
        val back = RecipeCodec.decode(RecipeCodec.encode(recipe))!!
        assertEquals(recipe.steps, back.steps)
        assertEquals(recipe.categories, back.categories)
        assertEquals(recipe.filters, back.filters)
    }

    @Test fun searchFiltersOnEveryCategoryAndOnTags() {
        val r = recipe.copy(category = "Soupes et veloutés")
        val index = SearchIndex(listOf(r, r.copy(id = "web-2", title = "Autre", categories = emptyList(), filters = emptyList())))
        assertEquals(1, index.search("", RecipeFilter(category = "Plats végétariens")).size)
        assertEquals(1, index.search("", RecipeFilter(tag = "Végétarien")).size)
        assertTrue("Plats végétariens" in index.categories)
    }
}
