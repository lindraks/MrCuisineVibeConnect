package app.toque.ui.screens

import android.content.Context
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.machine.Snapshot
import app.toque.core.recipes.McOnline
import app.toque.core.recipes.Proportions
import app.toque.core.recipes.ShoppingList
import app.toque.HomeAssistant
import app.toque.core.recipes.QuantityLinks
import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.RecipeAdjustments
import app.toque.core.recipes.Step
import app.toque.core.recipes.StepText
import app.toque.core.recipes.Weigh
import app.toque.core.recipes.adjusted
import app.toque.core.recipes.unlistedWeighings
import app.toque.recipes.HomeServer
import app.toque.recipes.Reviews
import app.toque.ui.Icon
import app.toque.ui.ImageLoader
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui
import app.toque.ui.stepText

/**
 * Fiche recette : ingrédients à gauche, étapes à droite, « Cuisiner » pour le pas-à-pas.
 * Une quantité se corrige en la touchant dans la liste d'ingrédients et les étapes qui
 * l'utilisent suivent ; la note et les corrections sont à l'utilisateur et restent en place quand le catalogue est synchronisé.
 */
class RecipeScreen(a: MainActivity, private val recipe: Recipe, private val initialServings: Int = 0) : Screen(a) {
    override val title = recipe.title
    private lateinit var favIcon: Icon
    private lateinit var favLabel: TextView
    private var planIcon: Icon? = null
    private var adjustments = a.app.recipes.adjustmentsOf(recipe.id)
    /**
     * Prorata de la balance (un ingrédient pesé, les autres suivent) : adaptations pour cette fois,
     * jamais enregistrées ; null sans prorata. [scaleNote] le résume.
     */
    private var scaled: RecipeAdjustments? = null
    private var scaleNote = ""
    /** Nombre de personnes choisi (celui de la recette sans prorata). */
    private var servings = recipe.servings
    /** La recette telle qu'elle est affichée et cuisinée : celle de la source + les corrections. */
    private var shown = recipe.adjusted(adjustments)
    /** Carte de pesée ouverte : ingrédient de référence et affichage du poids lu. */
    private var weighRef = -1
    private var weighValue: TextView? = null
    private var weighInfo: TextView? = null
    private val ingredientsBox by lazy { Ui.vertical(c) }
    /** Barre sous les actions : « − n pers. + », Peser, Courses (toujours visible, sans défiler). */
    private val toolsBar by lazy { Ui.horizontal(c).apply {
        background = Ui.round(Ui.SURFACE, 20f)
        setPadding(Ui.u(6), Ui.u(6), Ui.u(6), Ui.u(6))
    } }
    private val stepsList by lazy { Ui.vertical(c) }

    override fun build(): View {
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(24), Ui.u(6), Ui.u(24), Ui.u(18))

        // ---- colonne gauche : vignette + infos, actions, barre portions/peser/courses, puis ingrédients.
        // (Avant : photo pleine largeur de 170 et outils sous la liste ; ils sortaient de l'écran de 600.)
        val left = Ui.vertical(c)
        val head = Ui.horizontal(c, Gravity.CENTER_VERTICAL)
        val header = FrameLayout(c)
        header.background = Ui.gradient(Ui.placeholderColors(recipe.title), 20f)
        header.addView(Ui.text(c, Ui.initials(recipe.title), 44f, 0x44FFFFFF, Ui.THIN), Ui.flp(Ui.WRAP, Ui.WRAP, Gravity.CENTER))
        val img = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        header.addView(img, Ui.flp())
        header.clipToOutline = true
        header.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, Ui.uf(20f))
        }
        ImageLoader.load(img, a.app.recipes.imageFile(recipe.image), Ui.u(300))
        head.addView(header, LinearLayout.LayoutParams(Ui.u(150), Ui.u(112)).apply { rightMargin = Ui.u(14) })
        val info = Ui.vertical(c)
        if (recipe.ratingCount > 0) info.addView(Ui.ellipsize(Ui.text(c, "★ " + tr("%s/5 · %d notes", rating(recipe.rating), recipe.ratingCount), 17f, Ui.TEXT)))
        if (recipe.author.isNotBlank()) info.addView(Ui.ellipsize(Ui.text(c, tr("Par %s", authorName(recipe.author)), 16f, Ui.TEXT2)), Ui.lp(t = 4))
        if (recipe.totalMinutes > 0) info.addView(Ui.chip(c, "${recipe.totalMinutes} min", Icon.Kind.CLOCK), Ui.lp(Ui.WRAP, Ui.WRAP, t = 8))
        head.addView(info, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        left.addView(head)

        val actions = Ui.horizontal(c)
        actions.addView(Ui.button(c, tr("Cuisiner"), Ui.Style.PRIMARY, Icon.Kind.PLAY, height = 60, textSize = 22f) {
            cook()
        }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { rightMargin = Ui.u(10) })
        val fav = Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.STAR, height = 60) { toggleFavorite() }
        favIcon = fav.getChildAt(0) as Icon
        favLabel = Ui.text(c, "", 18f, Ui.TEXT, Ui.MEDIUM)
        fav.addView(favLabel, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { leftMargin = Ui.u(8) })
        actions.addView(fav)
        if (HomeServer.configured(a.prefs)) {
            // « À cuisiner » (partagé avec le téléphone) : icône seule, faute de place à côté de « Cuisiner ».
            val plan = Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.POT, height = 60) { togglePlanned() }
            plan.contentDescription = tr("À cuisiner")
            planIcon = plan.getChildAt(0) as? Icon
            actions.addView(plan, LinearLayout.LayoutParams(Ui.WRAP, Ui.WRAP).apply { leftMargin = Ui.u(10) })
        }
        left.addView(actions, Ui.lp(t = 12))
        left.addView(toolsBar, Ui.lp(t = 10))

        renderIngredients()
        left.addView(Ui.scroll(c, ingredientsBox), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = Ui.u(6) })
        root.addView(left, LinearLayout.LayoutParams(0, Ui.MATCH, 0.42f).apply { rightMargin = Ui.u(24) })

        // ---- colonne droite
        val steps = Ui.vertical(c)
        steps.addView(section(tr("Étapes")))
        steps.addView(stepsList)
        renderSteps()
        // Avis : site Monsieur Cuisine (recettes web-…), ou commentaires Cookomix par le serveur maison.
        val apiId = McOnline.apiIdOf(recipe.id)
        val source: ((Int) -> McOnline.ReviewPage)? = when {
            apiId != null -> { p -> Reviews.load(apiId, p) }
            recipe.id.startsWith("ckx-") && HomeServer.configured(a.prefs) -> { p -> Reviews.loadHome(a.prefs, recipe.id, p) }
            else -> null
        }
        source?.let { src ->
            steps.addView(reviewsBox, Ui.lp(t = 22))
            reviewsBox.addView(section(if (recipe.ratingCount > 0)
                tr("Avis · %s/5 (%d notes)", rating(recipe.rating), recipe.ratingCount) else tr("Avis")))
            reviewsBox.addView(reviewsState, Ui.lp(t = 10))
            loadReviews(src, 1)
        }
        val stepsBox = Ui.vertical(c)
        stepsBox.background = Ui.round(Ui.SURFACE, 24f)
        stepsBox.setPadding(Ui.u(22), Ui.u(10), Ui.u(22), Ui.u(18))
        stepsBox.addView(Ui.scroll(c, steps), Ui.lp(Ui.MATCH, Ui.MATCH))
        root.addView(stepsBox, LinearLayout.LayoutParams(0, Ui.MATCH, 0.58f))
        refreshFavorite()
        // Ouverte depuis « À cuisiner » : le nombre de personnes choisi sur le téléphone.
        if (initialServings > 0 && recipe.servings > 0 && initialServings != recipe.servings) {
            setServings(initialServings.coerceIn(1, maxOf(recipe.servings * 3, recipe.servings + 2)), warn = false)
        }
        return root
    }

    // ------------------------------------------------------------------ note et quantités adaptées

    private fun renderTools() {
        val bar = toolsBar
        bar.removeAllViews()
        if (recipe.servings > 0) {
            val max = maxOf(recipe.servings * 3, recipe.servings + 2)
            bar.addView(Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.MINUS, height = 48) { setServings(servings - 1) }.apply {
                setPadding(0, 0, 0, 0); alpha = if (servings > 1) 1f else 0.4f
            }, LinearLayout.LayoutParams(Ui.u(48), Ui.u(48)))
            bar.addView(Ui.text(c, tr("%d pers.", servings), 19f, if (servings != recipe.servings) Ui.ACCENT else Ui.TEXT, Ui.MEDIUM).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(Ui.u(72), Ui.WRAP))
            bar.addView(Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.PLUS, height = 48) { setServings(servings + 1) }.apply {
                setPadding(0, 0, 0, 0); alpha = if (servings < max) 1f else 0.4f
            }, LinearLayout.LayoutParams(Ui.u(48), Ui.u(48)))
        }
        bar.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f))
        if (scaled == null && references().isNotEmpty()) {
            bar.addView(Ui.button(c, tr("Peser"), Ui.Style.SECONDARY, Icon.Kind.SCALE, height = 48, textSize = 16f) { chooseReference() }.apply {
                setPadding(Ui.u(14), 0, Ui.u(14), 0)
            }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 8))
        }
        if (shown.allIngredients.isNotEmpty()) {
            bar.addView(Ui.button(c, tr("Courses"), Ui.Style.SECONDARY, Icon.Kind.HOME, height = 48, textSize = 16f) { shoppingList() }.apply {
                setPadding(Ui.u(14), 0, Ui.u(14), 0)
            })
        }
        bar.visibility = if (bar.childCount > 1) View.VISIBLE else View.GONE
    }

    private fun renderIngredients() {
        renderTools()
        val ing = ingredientsBox
        ing.removeAllViews()
        ing.addView(section(tr("Ingrédients")))
        if (shown.allIngredients.any { it.qty.isNotBlank() } || shown.steps.any { it.weigh != null }) {
            ing.addView(Ui.text(c, tr("Touchez une quantité pour l'adapter : les étapes suivent."), 14f, Ui.TEXT3), Ui.lp(t = 2))
        }
        if (scaled != null) {
            val box = Ui.vertical(c)
            box.background = Ui.round(Ui.ACCENT_DIM, 16f)
            box.setPadding(Ui.u(14), Ui.u(10), Ui.u(14), Ui.u(6))
            box.addView(Ui.text(c, scaleNote, 16f, Ui.TEXT).apply { setLineSpacing(0f, 1.1f) })
            box.addView(Ui.button(c, tr("Revenir aux quantités de la recette"), Ui.Style.GHOST, Icon.Kind.CLOSE, height = 46, textSize = 15f) {
                scaled = null
                servings = recipe.servings
                shown = recipe.adjusted(adjustments)
                renderIngredients()
                renderSteps()
            }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 2))
            ing.addView(box, Ui.lp(t = 8))
        }
        for (g in shown.ingredients) {
            if (g.title.isNotBlank()) ing.addView(Ui.text(c, g.title, 17f, Ui.ACCENT, Ui.MEDIUM), Ui.lp(t = 12, b = 2))
            for (i in g.items) {
                val original = if (i.originalQty.isNotBlank()) quantity(i.originalQty, i.unit) else null
                ing.addView(ingredientRow(i.display, original, if (i.qty.isNotBlank()) ({ editQuantity(i) }) else null), Ui.lp(t = 4))
            }
        }
        if (shown.ingredients.isEmpty()) ing.addView(Ui.text(c, tr("Pas de liste d'ingrédients."), 17f, Ui.TEXT3), Ui.lp(t = 8))
        // Quantités pesées dans les étapes mais absentes de la liste (souvent l'eau).
        val unlisted = recipe.unlistedWeighings().mapNotNull { (si, name) -> shown.steps.getOrNull(si)?.weigh?.let { Triple(si, name, it) } }
        if (unlisted.isNotEmpty()) {
            ing.addView(Ui.text(c, tr("Aussi dans les étapes"), 17f, Ui.ACCENT, Ui.MEDIUM), Ui.lp(t = 14, b = 2))
            for ((si, name, w) in unlisted) {
                val original = if (w.originalGrams > 0) "${w.originalGrams} g" else null
                ing.addView(ingredientRow("${w.grams} g $name", original) { editWeigh(si, name, w) }, Ui.lp(t = 4))
            }
        }
        // Ma note : après la liste (elle prenait la place des ingrédients en haut).
        ing.addView(section(tr("Ma note")), Ui.lp(t = 10))
        if (adjustments.note.isBlank()) {
            ing.addView(Ui.button(c, tr("Ajouter une note"), Ui.Style.GHOST, Icon.Kind.PLUS, height = 46, textSize = 16f) { editNote() },
                Ui.lp(Ui.WRAP, Ui.WRAP, t = 8, b = 8))
        } else {
            val note = Ui.text(c, adjustments.note, 17f, Ui.TEXT).apply { setLineSpacing(0f, 1.15f) }
            note.background = Ui.pressable(Ui.round(Ui.ACCENT_DIM, 16f), 16f)
            note.setPadding(Ui.u(14), Ui.u(10), Ui.u(14), Ui.u(10))
            note.setOnClickListener { editNote() }
            ing.addView(note, Ui.lp(t = 8, b = 8))
        }
    }

    /** Ligne d'ingrédient ; [original] : valeur de la recette quand elle a été adaptée. */
    private fun ingredientRow(label: String, original: String?, onClick: (() -> Unit)?): View {
        val row = Ui.horizontal(c, Gravity.TOP)
        val changed = original != null
        row.addView(Ui.text(c, "•", 18f, if (changed) Ui.ACCENT else Ui.TEXT3), Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
        val col = Ui.vertical(c)
        col.addView(Ui.text(c, label, 18f, if (changed) Ui.ACCENT else Ui.TEXT).apply { setLineSpacing(0f, 1.1f) })
        if (original != null) col.addView(Ui.text(c, tr("Recette : %s", original), 14f, Ui.TEXT3), Ui.lp(t = 2))
        row.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        if (onClick != null) {
            row.background = Ui.pressable(Ui.round(0, 12f), 12f)
            row.setOnClickListener { onClick() }
        }
        row.setPadding(0, Ui.u(4), 0, Ui.u(4))
        return row
    }

    private fun renderSteps() {
        stepsList.removeAllViews()
        for ((i, s) in shown.steps.withIndex()) stepsList.addView(stepRow(i, s), Ui.lp(t = 12))
    }

    private fun quantity(qty: String, unit: String): String = listOf(qty.trim(), unit.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    private fun save(next: RecipeAdjustments) {
        if (scaled != null) {
            // Prorata en cours : la retouche vaut aussi pour cette fois seulement.
            scaled = next
        } else {
            adjustments = next
            a.app.recipes.setAdjustments(recipe.id, next)
        }
        shown = recipe.adjusted(next)
        renderIngredients()
        renderSteps()
    }

    /** Adaptations affichées : celles de cette fois (prorata) ou celles enregistrées. */
    private val currentAdj: RecipeAdjustments get() = scaled ?: adjustments

    private fun cook() {
        a.prefs.guidedScale = scaled?.let { recipe.id + "\n" + it.encode() } ?: ""
        a.push(CookScreen(a, shown, 0))
    }

    private fun editNote() = editor(
        title = tr("Ma note sur cette recette"),
        detail = tr("Elle reste sur ce robot, même après une synchronisation des recettes."),
        value = adjustments.note,
        hint = tr("Ex. : moins d'eau la prochaine fois"),
        lines = 2,
        reset = if (adjustments.note.isBlank()) null else tr("Effacer"),
    ) { text ->
        // La note est toujours enregistrée, prorata ou pas.
        val t = text.orEmpty()
        adjustments = adjustments.withNote(t)
        a.app.recipes.setAdjustments(recipe.id, adjustments)
        scaled = scaled?.withNote(t)
        shown = recipe.adjusted(currentAdj)
        renderIngredients()
        true
    }

    private fun editQuantity(i: Ingredient) {
        val original = i.originalQty.ifBlank { i.qty }
        // Pavé numérique dès que la quantité de la recette est un nombre, fractions comprises
        // (« 1/2 », « ½ », « 1 000 ») : la valeur proposée est alors écrite en décimal (« 0,5 »),
        // tapable sur le pavé.
        val number = QuantityLinks.parse(original) != null
        val current = if (number) QuantityLinks.parse(i.qty)?.let(::decimal) ?: i.qty else i.qty
        editor(
            title = tr("Adapter la quantité"),
            detail = listOf(i.unit.trim(), i.name.trim()).filter { it.isNotEmpty() }.joinToString(" ") + "\n" +
                tr("Recette : %s", quantity(original, i.unit)),
            value = current,
            hint = original,
            number = number,
            reset = if (i.originalQty.isBlank()) null else tr("Valeur de la recette"),
        ) { text -> save(currentAdj.withQty(i, text.orEmpty())); true }
    }

    /** 0.5 → « 0,5 », 2.0 → « 2 », 0.333… → « 0,33 ». */
    private fun decimal(v: Double): String {
        val r = Math.round(v * 100) / 100.0
        return if (r == Math.floor(r)) r.toLong().toString() else r.toString().replace('.', ',')
    }

    private fun editWeigh(index: Int, name: String, w: Weigh) {
        val original = if (w.originalGrams > 0) w.originalGrams else w.grams
        editor(
            title = tr("Adapter la quantité"),
            detail = tr("%s · étape %d", name, index + 1) + "\n" + tr("Recette : %s", "$original g"),
            value = w.grams.toString(),
            hint = original.toString(),
            number = true,
            reset = if (w.originalGrams > 0) tr("Valeur de la recette") else null,
        ) { text ->
            val grams = if (text == null) 0 else text.trim().toIntOrNull() ?: -1
            if (grams < 0 || grams > RecipeAdjustments.MAX_GRAMS || (text != null && grams == 0)) {
                a.toast(tr("Entrez un poids entre 1 et %d g", RecipeAdjustments.MAX_GRAMS), Ui.WARN)
                false
            } else {
                save(currentAdj.withWeigh(index, w, grams))
                true
            }
        }
    }

    /**
     * Carte de saisie. [onDone] reçoit le texte saisi, ou null pour « revenir à la valeur de la
     * recette » ; il renvoie false pour garder la carte ouverte (saisie refusée).
     */
    private fun editor(title: String, detail: String, value: String, hint: String, lines: Int = 1, number: Boolean = false,
                       reset: String?, onDone: (String?) -> Boolean) {
        val card = Ui.card(c, 28f, Ui.SURFACE2)
        card.setPadding(Ui.u(32), Ui.u(26), Ui.u(32), Ui.u(24))
        card.addView(Ui.text(c, title, 24f, Ui.TEXT, Ui.MEDIUM))
        if (detail.isNotBlank()) card.addView(Ui.text(c, detail, 17f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }, Ui.lp(t = 8))
        val input = EditText(c).apply {
            setText(value)
            setSelection(text.length)
            this.hint = hint
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.uf(22f))
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.TEXT3)
            background = Ui.round(Ui.SURFACE, 16f, Ui.STROKE, 1f)
            setPadding(Ui.u(16), Ui.u(12), Ui.u(16), Ui.u(12))
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or (if (lines == 1) EditorInfo.IME_ACTION_DONE else 0)
            if (lines == 1) {
                setSingleLine()
                inputType = if (number) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                // Pavé numérique, mais la virgule décimale française reste acceptée.
                // (setRawInputType après coup : le clavier reste le pavé décimal sans remplacer ce filtre.)
                if (number) {
                    keyListener = android.text.method.DigitsKeyListener.getInstance("0123456789.,")
                    setRawInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
                }
            } else {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = lines
                maxLines = lines
                gravity = Gravity.TOP
            }
        }
        card.addView(input, Ui.lp(t = 16, b = 20))
        val imm = c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        fun finish(text: String?) {
            if (!onDone(text)) return
            imm.hideSoftInputFromWindow(input.windowToken, 0)
            a.closeOverlay()
        }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { finish(input.text.toString()); true } else false
        }
        val row = Ui.horizontal(c, Gravity.END or Gravity.CENTER_VERTICAL)
        if (reset != null) {
            row.addView(Ui.button(c, reset, Ui.Style.GHOST, height = 56, textSize = 17f) { finish(null) })
            row.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(0, 1, 1f))
        }
        row.addView(Ui.button(c, tr("Annuler"), Ui.Style.GHOST, height = 56, textSize = 17f) {
            imm.hideSoftInputFromWindow(input.windowToken, 0)
            a.closeOverlay()
        })
        row.addView(Ui.spacer(c, w = 12))
        row.addView(Ui.button(c, tr("Enregistrer"), Ui.Style.PRIMARY, Icon.Kind.CHECK, height = 56, textSize = 17f) { finish(input.text.toString()) })
        card.addView(row)
        a.showOverlay(card, 680, dismissOnScrim = false)
        // En haut de l'écran : le clavier occupe la moitié basse.
        (card.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = Ui.u(14)
        }
        card.requestLayout()
        input.requestFocus()
        input.post { imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    // ------------------------------------------------------------------ prorata par la balance

    /** Ingrédients qui se pèsent (indice dans allIngredients), candidats pour servir de référence. */
    private fun references(): List<Int> {
        val items = recipe.adjusted(adjustments).allIngredients
        return items.indices.filter { Proportions.factor(items[it], 1) != null }
    }

    // ------------------------------------------------------------------ liste de courses

    /**
     * Les ingrédients tels qu'affichés (nombre de personnes et prorata compris), à cocher avant
     * l'envoi à Home Assistant : eau, sel et poivre décochés d'office.
     */
    private fun shoppingList() {
        val viaHome = HomeServer.configured(a.prefs)
        if (!viaHome && !HomeAssistant.configured(a.prefs)) {
            a.toast(tr("Réglez d'abord l'adresse de Home Assistant dans Réglages."), Ui.WARN)
            return
        }
        var items = ShoppingList.items(shown)
        if (items.isEmpty()) return
        // Cases cochées : gardées quand le nombre de personnes change (seules les quantités bougent).
        val on = items.map { it.checked }.toBooleanArray()
        val card = Ui.card(c, 28f, Ui.SURFACE2)
        card.setPadding(Ui.u(32), Ui.u(26), Ui.u(32), Ui.u(22))
        val head = Ui.horizontal(c, Gravity.CENTER_VERTICAL)
        val titles = Ui.vertical(c)
        titles.addView(Ui.text(c, tr("Liste de courses"), 24f, Ui.TEXT, Ui.MEDIUM))
        val who = Ui.ellipsize(Ui.text(c, "", 16f, Ui.TEXT2))
        titles.addView(who, Ui.lp(t = 4))
        head.addView(titles, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        card.addView(head)

        lateinit var send: LinearLayout
        val list = Ui.vertical(c)
        val scroller = Ui.scroll(c, list)
        fun sendLabel() = tr("Envoyer (%d)", on.count { it })
        fun refreshSend() {
            (send.getChildAt(1) as? TextView)?.text = sendLabel()
            Ui.setButtonEnabled(send, on.any { it })
        }
        fun fill() {
            who.text = when {
                scaled != null && servings == recipe.servings -> scaleNote
                servings > 0 -> tr("%s · %d personnes", recipe.title, servings)
                else -> recipe.title
            }
            list.removeAllViews()
            for ((k, item) in items.withIndex()) {
                val row = Ui.horizontal(c, Gravity.CENTER_VERTICAL)
                row.setPadding(Ui.u(12), Ui.u(8), Ui.u(12), Ui.u(8))
                val box = Icon(c, Icon.Kind.CHECK, Ui.ACCENT)
                val label = Ui.text(c, item.text, 18f, Ui.TEXT)
                fun paint() {
                    row.background = if (on[k]) Ui.round(Ui.SURFACE, 14f) else null
                    box.alpha = if (on[k]) 1f else 0.15f
                    label.setTextColor(if (on[k]) Ui.TEXT else Ui.TEXT3)
                }
                row.addView(box, LinearLayout.LayoutParams(Ui.u(26), Ui.u(26)).apply { rightMargin = Ui.u(14) })
                row.addView(label, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
                row.setOnClickListener {
                    on[k] = !on[k]
                    paint()
                    refreshSend()
                }
                paint()
                list.addView(row, Ui.lp(t = 4))
            }
        }

        // Nombre de personnes, modifiable ici : la liste et la page de la recette suivent.
        if (recipe.servings > 0) {
            val max = maxOf(recipe.servings * 3, recipe.servings + 2)
            val minus = Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.MINUS, height = 48) {}
            val plus = Ui.button(c, "", Ui.Style.SECONDARY, Icon.Kind.PLUS, height = 48) {}
            val value = Ui.text(c, "", 24f, Ui.TEXT, Ui.MEDIUM).apply { gravity = Gravity.CENTER }
            fun paintCount() {
                value.text = servings.toString()
                value.setTextColor(if (servings != recipe.servings) Ui.ACCENT else Ui.TEXT)
                minus.alpha = if (servings > 1) 1f else 0.4f
                plus.alpha = if (servings < max) 1f else 0.4f
            }
            fun change(n: Int) {
                if (n < 1 || n > max || n == servings) return
                setServings(n, warn = false)
                val next = ShoppingList.items(shown)
                if (next.size != items.size) {
                    // Liste différente (ne devrait pas arriver) : on repart des cases par défaut.
                    for (k in on.indices) on[k] = next.getOrNull(k)?.checked ?: false
                }
                items = next
                paintCount()
                fill()
                refreshSend()
            }
            minus.setOnClickListener { change(servings - 1) }
            plus.setOnClickListener { change(servings + 1) }
            head.addView(Ui.text(c, tr("Personnes"), 17f, Ui.TEXT2), Ui.lp(Ui.WRAP, Ui.WRAP, r = 12))
            head.addView(minus)
            head.addView(value, LinearLayout.LayoutParams(Ui.u(56), Ui.WRAP))
            head.addView(plus)
            paintCount()
        }

        fill()
        card.addView(scroller, LinearLayout.LayoutParams(Ui.MATCH, Ui.u(minOf(300, 52 * items.size + 8))).apply { topMargin = Ui.u(10) })
        val row = Ui.horizontal(c, Gravity.END or Gravity.CENTER_VERTICAL)
        row.addView(Ui.button(c, tr("Annuler"), Ui.Style.GHOST, height = 56, textSize = 17f) { a.closeOverlay() })
        row.addView(Ui.spacer(c, w = 12))
        send = Ui.button(c, sendLabel(), Ui.Style.PRIMARY, Icon.Kind.HOME, height = 56, textSize = 17f) {
            val chosen = items.filterIndexed { k, _ -> on[k] }.map { it.text }
            if (chosen.isEmpty()) return@button
            a.closeOverlay()
            if (viaHome) {
                a.toast(tr("Envoi à la liste de courses…"))
                a.app.home.sendShopping(recipe.title, servings, chosen) { err ->
                    if (err == null) a.toast(tr("%d articles ajoutés à la liste de courses.", chosen.size), Ui.OK) else a.toast(err, Ui.WARN)
                }
                return@button
            }
            a.toast(tr("Envoi à Home Assistant…"))
            HomeAssistant.send(a.prefs, ShoppingList.payload(recipe.title, servings, chosen)) { err ->
                if (err == null) a.toast(tr("%d articles envoyés à Home Assistant.", chosen.size), Ui.OK) else a.toast(err, Ui.WARN)
            }
        }
        row.addView(send)
        card.addView(row, Ui.lp(t = 14))
        refreshSend()
        a.showOverlay(card, 760)
    }

    /** [warn] : rappel sur la contenance du bol au-delà des quantités prévues (pas dans la liste de courses). */
    private fun setServings(n: Int, warn: Boolean = true) {
        val max = maxOf(recipe.servings * 3, recipe.servings + 2)
        if (n < 1 || n > max || n == servings) return
        val limit = bowlLimit()
        val wasAbove = servings > limit
        servings = n
        if (n == recipe.servings) {
            scaled = null
        } else {
            scaled = Proportions.byFactor(recipe, adjustments, n / recipe.servings.toDouble())
            scaleNote = tr("Recette pour %d personnes au lieu de %d, pour cette fois.", n, recipe.servings)
        }
        shown = recipe.adjusted(currentAdj)
        renderIngredients()
        renderSteps()
        // Avertissement seulement au-delà de ce que le bol accepte (calcul du serveur maison), une fois.
        if (warn && n > limit && !wasAbove) {
            a.toast(tr("Au-delà de %d personnes, le bol risque d'être trop plein.", limit), Ui.WARN)
        }
    }

    /** Personnes au plus sans trop remplir le bol : estimation du serveur, sinon la recette telle quelle. */
    private fun bowlLimit(): Int = if (recipe.maxServings > recipe.servings) recipe.maxServings else recipe.servings

    private fun shortName(i: Ingredient): String =
        QuantityLinks.keyName(i.name).substringBefore(',').trim().ifEmpty { i.name.trim() }

    private fun chooseReference() {
        val items = recipe.adjusted(adjustments).allIngredients
        val card = Ui.card(c, 28f, Ui.SURFACE2)
        card.setPadding(Ui.u(32), Ui.u(26), Ui.u(32), Ui.u(24))
        card.addView(Ui.text(c, tr("Adapter à la balance"), 24f, Ui.TEXT, Ui.MEDIUM))
        card.addView(Ui.text(c, tr("Choisissez l'ingrédient que vous allez peser : les autres quantités suivront en proportion, pour cette fois."),
            17f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }, Ui.lp(t = 8))
        val list = Ui.vertical(c)
        for (k in references()) {
            list.addView(Ui.button(c, items[k].display, Ui.Style.SECONDARY, Icon.Kind.SCALE, height = 56, textSize = 17f) {
                a.closeOverlay()
                weigh(k)
            }, Ui.lp(Ui.MATCH, Ui.WRAP, t = 8))
        }
        card.addView(Ui.scroll(c, list), LinearLayout.LayoutParams(Ui.MATCH, Ui.u(minOf(300, 64 * references().size + 8))).apply { topMargin = Ui.u(8) })
        val row = Ui.horizontal(c, Gravity.END or Gravity.CENTER_VERTICAL)
        row.addView(Ui.button(c, tr("Annuler"), Ui.Style.GHOST, height = 56, textSize = 17f) { a.closeOverlay() })
        card.addView(row, Ui.lp(t = 12))
        a.showOverlay(card, 680)
    }

    private fun weigh(k: Int) {
        val i = recipe.adjusted(adjustments).allIngredients.getOrNull(k) ?: return
        val planned = QuantityLinks.currentGrams(i)?.let { Math.round(it).toInt() } ?: return
        weighRef = k
        val card = Ui.card(c, 28f, Ui.SURFACE2)
        card.setPadding(Ui.u(32), Ui.u(26), Ui.u(32), Ui.u(24))
        card.addView(Ui.ellipsize(Ui.text(c, tr("Peser : %s", shortName(i)), 24f, Ui.TEXT, Ui.MEDIUM)))
        // Balance remise à zéro tout de suite : il n'y a plus qu'à poser l'ingrédient.
        a.machine.tare()
        card.addView(Ui.text(c, tr("Recette : %d g. Balance remise à zéro : mettez l'ingrédient dans le bol (Tare pour recommencer).", planned),
            17f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }, Ui.lp(t = 8))
        val value = Ui.text(c, "—", 64f, Ui.TEXT, Ui.THIN)
        card.addView(value, Ui.lp(Ui.WRAP, Ui.WRAP, t = 10).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val info = Ui.text(c, "", 18f, Ui.ACCENT, Ui.MEDIUM)
        card.addView(info, Ui.lp(Ui.WRAP, Ui.WRAP, b = 14).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val row = Ui.horizontal(c, Gravity.END or Gravity.CENTER_VERTICAL)
        row.addView(Ui.button(c, tr("Tare"), Ui.Style.SECONDARY, Icon.Kind.SCALE, height = 56, textSize = 17f) { a.machine.tare() })
        row.addView(Ui.spacer(c, weight = 1f), LinearLayout.LayoutParams(0, 1, 1f))
        row.addView(Ui.button(c, tr("Annuler"), Ui.Style.GHOST, height = 56, textSize = 17f) { closeWeigh() })
        row.addView(Ui.spacer(c, w = 12))
        row.addView(Ui.button(c, tr("Valider"), Ui.Style.PRIMARY, Icon.Kind.CHECK, height = 56, textSize = 17f) { applyWeight(k) })
        card.addView(row)
        weighValue = value
        weighInfo = info
        a.showOverlay(card, 680, dismissOnScrim = false)
        onSnapshot(a.snapshot)
    }

    private fun closeWeigh() {
        weighRef = -1
        weighValue = null
        weighInfo = null
        a.closeOverlay()
    }

    private fun applyWeight(k: Int) {
        val s = a.snapshot
        val i = recipe.adjusted(adjustments).allIngredients.getOrNull(k) ?: return closeWeigh()
        val planned = QuantityLinks.currentGrams(i) ?: return closeWeigh()
        val grams = s.weightG
        val f = if (s.hasSample) Proportions.factor(i, grams) else null
        if (f == null || f < Proportions.MIN_FACTOR || f > Proportions.MAX_FACTOR) {
            a.toast(tr("Poids hors limites : entre %d et %d g pour cet ingrédient",
                Math.ceil(planned * Proportions.MIN_FACTOR).toInt(), Math.floor(planned * Proportions.MAX_FACTOR).toInt()), Ui.WARN)
            return
        }
        val next = Proportions.scale(recipe, adjustments, k, grams) ?: return closeWeigh()
        val percent = Math.round(f * 100).toInt()
        scaled = next
        servings = recipe.servings
        scaleNote = tr("Recette à %d %% d'après la balance (%s : %d g), pour cette fois.", percent, shortName(i), grams)
        shown = recipe.adjusted(next)
        closeWeigh()
        renderIngredients()
        renderSteps()
        val maxPercent = if (recipe.servings > 0) bowlLimit() * 100 / recipe.servings else 100
        if (percent > maxPercent) a.toast(tr("Recette à %d %% : le bol risque d'être trop plein.", percent), Ui.WARN)
    }

    override fun onSnapshot(s: Snapshot) {
        val value = weighValue ?: return
        val info = weighInfo ?: return
        if (!s.hasSample) {
            value.text = "—"
            info.text = tr("Base non connectée")
            return
        }
        value.text = "${s.weightG} g"
        val i = recipe.adjusted(adjustments).allIngredients.getOrNull(weighRef)
        val f = if (i != null && s.weightG > 0) Proportions.factor(i, s.weightG) else null
        info.text = if (f == null) "" else tr("Recette à %d %%", Math.round(f * 100).toInt())
    }

    override fun onHide() {
        weighRef = -1
        weighValue = null
        weighInfo = null
    }

    // ------------------------------------------------------------------ avis

    private val reviewsBox by lazy { Ui.vertical(c) }
    private val reviewsState by lazy { Ui.text(c, "", 16f, Ui.TEXT3) }
    private var moreButton: View? = null
    private var loading = false

    private fun loadReviews(src: (Int) -> McOnline.ReviewPage, page: Int) {
        if (loading) return
        loading = true
        reviewsState.visibility = View.VISIBLE
        reviewsState.text = tr("Chargement des avis…")
        Thread {
            val result = try {
                src(page)
            } catch (e: Exception) {
                null
            }
            reviewsBox.post {
                loading = false
                if (result == null) {
                    reviewsState.text = tr("Avis indisponibles (pas de connexion ?)")
                    return@post
                }
                showReviews(src, result)
            }
        }.start()
    }

    private fun showReviews(src: (Int) -> McOnline.ReviewPage, p: McOnline.ReviewPage) {
        moreButton?.let { reviewsBox.removeView(it) }
        moreButton = null
        if (p.page == 1 && p.reviews.isEmpty()) {
            reviewsState.text = tr("Pas encore de commentaire sur cette recette.")
            return
        }
        reviewsState.visibility = View.GONE
        for (r in p.reviews) reviewsBox.addView(reviewRow(r), Ui.lp(t = 14))
        if (p.page < p.totalPages) {
            val shown = reviewsBox.childCount - 2
            val more = Ui.button(c, tr("Voir plus d'avis (%d sur %d)", shown, p.total), Ui.Style.GHOST, height = 50, textSize = 16f) {
                loadReviews(src, p.page + 1)
            }
            moreButton = more
            reviewsBox.addView(more, Ui.lp(Ui.WRAP, Ui.WRAP, t = 12))
        }
    }

    private fun reviewRow(r: McOnline.Review): View {
        val box = Ui.vertical(c)
        box.background = Ui.round(Ui.SURFACE2, 16f)
        box.setPadding(Ui.u(16), Ui.u(12), Ui.u(16), Ui.u(12))
        val head = Ui.horizontal(c)
        // Commentaires Cookomix : pas de note, pas d'étoiles.
        if (r.rating > 0) head.addView(Ui.text(c, "★".repeat(r.rating) + "☆".repeat(5 - r.rating), 17f, Ui.ACCENT), Ui.lp(Ui.WRAP, Ui.WRAP, r = 12))
        val who = listOf(r.author.ifBlank { tr("Anonyme") }, r.date).filter { it.isNotBlank() }.joinToString(" · ")
        head.addView(Ui.ellipsize(Ui.text(c, who, 15f, Ui.TEXT3)), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        box.addView(head)
        if (r.message.isNotBlank()) {
            box.addView(Ui.text(c, r.message, 17f, Ui.TEXT).apply { setLineSpacing(0f, 1.12f) }, Ui.lp(t = 6))
        }
        if (r.reply.isNotBlank()) {
            box.addView(Ui.text(c, tr("Réponse de Monsieur Cuisine : %s", r.reply), 15f, Ui.TEXT2)
                .apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 8))
        }
        return box
    }

    private fun rating(v: Double): String = String.format(I18n.locale, "%.1f", v)

    private fun authorName(name: String): String =
        if (name.equals("Monsieur Cuisine Official", ignoreCase = true)) tr("Monsieur Cuisine (recette officielle)") else name

    private fun section(t: String): TextView =
        Ui.text(c, t.uppercase(), 15f, Ui.TEXT2, Ui.MEDIUM).apply { letterSpacing = 0.08f; setPadding(0, Ui.u(10), 0, 0) }

    private fun stepRow(i: Int, s: Step): View {
        val row = Ui.horizontal(c, Gravity.TOP)
        val num = Ui.text(c, "${i + 1}", 17f, Ui.ACCENT, Ui.MEDIUM).apply { gravity = Gravity.CENTER }
        num.background = Ui.round(Ui.ACCENT_DIM, 18f)
        row.addView(num, LinearLayout.LayoutParams(Ui.u(36), Ui.u(36)).apply { rightMargin = Ui.u(14) })
        val col = Ui.vertical(c)
        if (s.text.isNotBlank()) col.addView(Ui.text(c, stepText(s, shown), 18f, Ui.TEXT).apply { setLineSpacing(0f, 1.15f) })
        val chips = Ui.horizontal(c)
        s.weigh?.let { w ->
            val changed = w.originalGrams > 0
            val chip = Ui.chip(c, if (changed) tr("Peser %d g (recette : %d g)", w.grams, w.originalGrams) else tr("Peser %d g", w.grams),
                Icon.Kind.SCALE, if (changed) Ui.ACCENT else Ui.TEXT2, if (changed) Ui.ACCENT_DIM else Ui.SURFACE2, 15f)
            chips.addView(chip, Ui.lp(Ui.WRAP, Ui.WRAP, r = 8))
        }
        s.machine?.let { m ->
            val ok = m.valid
            chips.addView(Ui.chip(c, m.summary, if (ok) Icon.Kind.POT else Icon.Kind.WARNING,
                if (ok) Ui.ACCENT else Ui.WARN, if (ok) Ui.ACCENT_DIM else 0x33FFC14D, 15f))
        }
        if (chips.childCount > 0) col.addView(chips, Ui.lp(t = 8))
        row.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        return row
    }

    private fun togglePlanned() {
        val now = !a.app.home.isPlanned(recipe.id)
        a.app.home.setPlanned(recipe.id, if (now) servings.coerceAtLeast(0) else null)
        refreshFavorite()
        a.toast(if (now) tr("Ajoutée à « À cuisiner » (aussi sur le téléphone)") else tr("Retirée de « À cuisiner »"))
    }

    private fun toggleFavorite() {
        val now = !a.app.recipes.isFavorite(recipe.id)
        a.app.home.setFavorite(recipe.id, now)
        refreshFavorite()
        a.toast(if (now) tr("Ajoutée aux favoris") else tr("Retirée des favoris"))
    }

    private fun refreshFavorite() {
        val f = a.app.recipes.isFavorite(recipe.id)
        favIcon.set(if (f) Icon.Kind.STAR_FILLED else Icon.Kind.STAR)
        favIcon.color = if (f) Ui.ACCENT else Ui.TEXT
        favLabel.text = tr("Favori")
        planIcon?.color = if (a.app.home.isPlanned(recipe.id)) Ui.ACCENT else Ui.TEXT
    }

    override fun onKnobPress(): Boolean {
        cook()
        return true
    }
}
