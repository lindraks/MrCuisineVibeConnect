package app.toque.core

import app.toque.core.json.Json
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.IngredientGroup
import app.toque.core.recipes.Proportions
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.RecipeAdjustments
import app.toque.core.recipes.ShoppingList
import app.toque.core.recipes.adjusted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingListTest {
    private val soup = Recipe(
        id = "web-2", title = "Velouté", servings = 4,
        ingredients = listOf(
            IngredientGroup("Velouté", listOf(
                Ingredient("800", "g", "butternut, en morceaux"),
                Ingredient("2", "", "oignons"),
                Ingredient("20", "g", "beurre"),
                Ingredient("500", "g", "eau"),
                Ingredient(name = "1 pincée de sel"),
                Ingredient("", "", "poivre"),
                Ingredient("2", "", "échalotes (60 g)"),
            )),
            IngredientGroup("Croûtons", listOf(
                Ingredient("10", "g", "beurre"),
                Ingredient("½", "", "baguette"),
            )),
        ),
    )

    @Test fun itemsFollowTheShownQuantities() {
        val six = soup.adjusted(Proportions.byFactor(soup, RecipeAdjustments(), 1.5))
        val items = ShoppingList.items(six)
        assertEquals(listOf(
            "1,2 kg butternut", "3 oignons", "45 g beurre", "750 g eau", "1 pincée de sel", "poivre", "3 échalotes (90 g)", "¾ baguette",
        ), items.map { it.text })
        // Eau, sel et poivre décochés par défaut.
        assertEquals(listOf(true, true, true, false, false, false, true, true), items.map { it.checked })
    }

    @Test fun payloadIsJson() {
        val p = Json.parse(ShoppingList.payload("Velouté", 6, listOf("3 oignons", "45 g beurre"))).obj()
        assertEquals("Velouté", p["recipe"])
        assertEquals(listOf("3 oignons", "45 g beurre"), p["items"].list())
        assertEquals("3 oignons\n45 g beurre", p["text"])
    }

    @Test fun webhookAddresses() {
        assertEquals("http://192.168.1.20:8123/api/webhook/toque_courses", ShoppingList.webhookUrl("192.168.1.20", "toque_courses"))
        assertEquals("http://192.168.1.20:8123/api/webhook/x", ShoppingList.webhookUrl("192.168.1.20:8123/", "x"))
        assertEquals("https://maison.duckdns.org/api/webhook/x", ShoppingList.webhookUrl("https://maison.duckdns.org", "x"))
        assertNull(ShoppingList.webhookUrl("", "x"))
        assertNull(ShoppingList.webhookUrl("192.168.1.20", "pas valide !"))
    }
}
