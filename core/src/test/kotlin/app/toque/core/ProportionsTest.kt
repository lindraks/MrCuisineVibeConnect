package app.toque.core

import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.IngredientGroup
import app.toque.core.recipes.Proportions
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.RecipeAdjustments
import app.toque.core.recipes.Step
import app.toque.core.recipes.Weigh
import app.toque.core.recipes.adjusted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProportionsTest {
    private val soup = Recipe(
        id = "web-2",
        title = "Velouté de butternut",
        ingredients = listOf(IngredientGroup("", listOf(
            Ingredient("800", "g", "butternut, en morceaux"),
            Ingredient("2", "", "oignons"),
            Ingredient("1", "c. à s.", "huile d'olive"),
            Ingredient("0,5", "l", "bouillon de légumes"),
            Ingredient(name = "1 pincée de sel"),
            Ingredient("2", "", "échalotes (60 g)"),
            Ingredient("", "", "poivre"),
        ))),
        steps = listOf(
            Step("Mettre 800 g de butternut dans le bol.", weigh = Weigh("butternut", 800)),
            Step("Ajouter 300 g d'eau.", weigh = Weigh("Ajouter 300 g d'eau.", 300)),
            Step("Cuire."),
        ),
    )

    private fun qty(r: Recipe) = r.allIngredients.map { it.qty }

    @Test fun weighingTheReferenceScalesEverything() {
        val adj = Proportions.scale(soup, RecipeAdjustments(), 0, 652)!!
        val r = soup.adjusted(adj)
        // 652 / 800 = 81,5 %
        assertEquals(listOf("652", "1 ½", "¾", "0,41", "1", "1 ½", ""), qty(r))
        assertEquals("1 pincée de sel", r.allIngredients[4].display)        // au goût : inchangé
        assertEquals(652, r.steps[0].weigh!!.grams)                          // pesée reliée à la butternut
        assertEquals("Mettre 652 g de butternut dans le bol.", r.steps[0].text)
        assertEquals(244, r.steps[1].weigh!!.grams)                          // l'eau, hors liste, suit aussi
        assertEquals(800, r.allIngredients[0].originalQty.toInt())           // la valeur de la recette reste visible
    }

    @Test fun moreThanPlannedScalesUp() {
        val r = soup.adjusted(Proportions.scale(soup, RecipeAdjustments(), 0, 1200)!!)
        assertEquals(listOf("1200", "3", "1 ½", "0,75", "1", "3", ""), qty(r))
    }

    @Test fun piecesWithTheirWeightCanBeTheReference() {
        // 2 échalotes (60 g) ; on en pèse 90 g → ×1,5
        assertEquals(1.5, Proportions.factor(soup.allIngredients[5], 90)!!, 1e-9)
        val r = soup.adjusted(Proportions.scale(soup, RecipeAdjustments(), 5, 90)!!)
        assertEquals("1200", r.allIngredients[0].qty)
        assertEquals("3", r.allIngredients[5].qty)
    }

    @Test fun piecesWithoutWeightCannotBeTheReference() {
        assertNull(Proportions.factor(soup.allIngredients[1], 300))          // « 2 oignons »
        assertNull(Proportions.scale(soup, RecipeAdjustments(), 1, 300))
    }

    @Test fun litresAsReference() {
        val r = soup.adjusted(Proportions.scale(soup, RecipeAdjustments(), 3, 652)!!)
        assertEquals("0,652", r.allIngredients[3].qty)
        assertEquals("1043", r.allIngredients[0].qty)
    }

    @Test fun keepsTheUsersOwnNote() {
        val mine = RecipeAdjustments(note = "moins salé")
        assertEquals("moins salé", Proportions.scale(soup, mine, 0, 400)!!.note)
    }

    @Test fun fractions() {
        assertEquals("½", Proportions.fraction(0.4, 2))
        assertEquals("½", Proportions.fraction(0.1, 2))                       // jamais 0
        assertEquals("1 ¾", Proportions.fraction(1.8, 4))
        assertEquals("2", Proportions.fraction(2.1, 2))
    }
}
