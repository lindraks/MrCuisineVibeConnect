package app.toque.ui

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.Step
import app.toque.core.recipes.StepText

/** Texte d'une étape, quantités des ingrédients en couleur (elles suivent le nombre de personnes). */
fun stepText(step: Step, recipe: Recipe): CharSequence {
    val parts = StepText.parts(step, recipe)
    if (parts.none { it.quantity }) return parts.joinToString("") { it.text }
    val b = SpannableStringBuilder()
    for (p in parts) {
        val start = b.length
        b.append(p.text)
        if (p.quantity) {
            b.setSpan(ForegroundColorSpan(Ui.ACCENT), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            b.setSpan(StyleSpan(Typeface.BOLD), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return b
}
