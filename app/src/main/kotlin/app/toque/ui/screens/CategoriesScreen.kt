package app.toque.ui.screens

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import app.toque.core.i18n.tr
import app.toque.recipes.HomeServer
import app.toque.ui.CategoryIcon
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui

/**
 * Recettes par rayons : un panneau de tuiles (icône, nom, nombre de recettes) regroupées comme sur
 * l'appli téléphone. Toucher une tuile ouvre la liste de cette catégorie.
 */
class CategoriesScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Recettes")
    private lateinit var body: LinearLayout
    private lateinit var scroller: ScrollView
    private var unsub: (() -> Unit)? = null
    private var unsubHome: (() -> Unit)? = null

    override fun build(): View {
        val root = Ui.vertical(c)
        root.setPadding(Ui.u(24), Ui.u(6), Ui.u(24), 0)
        val search = Ui.horizontal(c)
        search.setPadding(Ui.u(20), 0, Ui.u(20), 0)
        search.background = Ui.pressable(Ui.round(Ui.SURFACE, 32f, Ui.STROKE, 1f), 32f)
        search.addView(Icon(c, Icon.Kind.SEARCH, Ui.TEXT2), LinearLayout.LayoutParams(Ui.u(26), Ui.u(26)).apply { rightMargin = Ui.u(12) })
        search.addView(Ui.text(c, tr("Rechercher une recette…"), 21f, Ui.TEXT3))
        search.isClickable = true
        search.setOnClickListener { a.push(RecipesScreen(a, focusSearch = true)) }
        root.addView(search, Ui.lp(Ui.MATCH, Ui.u(64)))
        body = Ui.vertical(c)
        body.setPadding(0, 0, 0, Ui.u(20))
        scroller = Ui.scroll(c, body)
        root.addView(scroller, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = Ui.u(6) })
        return root
    }

    private fun render() {
        body.removeAllViews()
        val repo = a.app.recipes
        val counts = HashMap<String, Int>()
        for (e in repo.index.entries) {
            for (cat in (listOf(e.category) + e.categories).filter { it.isNotBlank() }.distinct()) counts[cat] = (counts[cat] ?: 0) + 1
        }
        // Raccourcis
        val top = ArrayList<Tile>()
        top += Tile("Toutes", tr("Toutes les recettes"), repo.count) { a.push(RecipesScreen(a)) }
        top += Tile(null, tr("Favoris"), repo.index.entries.count { it.favorite }, Icon.Kind.STAR) { a.push(RecipesScreen(a, favorites = true)) }
        if (HomeServer.configured(a.prefs)) {
            val known = repo.index.entries.mapTo(HashSet()) { it.id }
            top += Tile("À cuisiner", tr("À cuisiner"), a.app.home.planned.count { it.id in known }) {
                a.push(RecipesScreen(a, initialUsage = RecipesScreen.Usage.PLANNED))
            }
        }
        top += Tile(null, tr("Récentes"), null, Icon.Kind.CLOCK) { a.push(RecipesScreen(a, initialUsage = RecipesScreen.Usage.RECENT)) }
        top += Tile(null, tr("Fréquentes"), null, Icon.Kind.FLAME) { a.push(RecipesScreen(a, initialUsage = RecipesScreen.Usage.MOST)) }
        grid(top)

        // Rayons, groupés ; les groupes d'une seule catégorie sont réunis à la fin.
        val placed = HashSet<String>()
        val single = ArrayList<String>()
        for ((group, label, cats) in GROUPS) {
            val present = cats.filter { (counts[it] ?: 0) > 0 }
            placed += cats
            if (present.isEmpty()) continue
            if (cats.size == 1) { single += present; continue }
            header(group, label)
            grid(present.map { cat -> Tile(cat, cat, counts[cat]) { a.push(RecipesScreen(a, category = cat)) } })
        }
        // Catégories inconnues du classement (autre source de recettes).
        val others = counts.keys.filter { it !in placed }.sorted()
        if (single.isNotEmpty() || others.isNotEmpty()) {
            header("Bases", tr("Et aussi"))
            grid((single + others).map { cat -> Tile(cat, cat, counts[cat]) { a.push(RecipesScreen(a, category = cat)) } })
        }
    }

    private class Tile(val icon: String?, val label: String, val count: Int?, val kind: Icon.Kind = Icon.Kind.BOOK, val open: () -> Unit)

    private fun header(icon: String, label: String) {
        val h = Ui.horizontal(c)
        h.addView(CategoryIcon(c, icon, Ui.TEXT3), LinearLayout.LayoutParams(Ui.u(22), Ui.u(22)).apply { rightMargin = Ui.u(10) })
        h.addView(Ui.text(c, label.uppercase(), 15f, Ui.TEXT2, Ui.MEDIUM).apply { letterSpacing = 0.08f })
        body.addView(h, Ui.lp(t = 22, b = 10))
    }

    private fun grid(tiles: List<Tile>) {
        var row: LinearLayout? = null
        for ((k, t) in tiles.withIndex()) {
            if (k % COLUMNS == 0) {
                row = Ui.horizontal(c, Gravity.TOP)
                body.addView(row, Ui.lp(t = if (k == 0) 12 else 12))
            }
            row!!.addView(tile(t), LinearLayout.LayoutParams(0, Ui.u(150), 1f).apply { if (k % COLUMNS < COLUMNS - 1) rightMargin = Ui.u(12) })
        }
        // Dernière rangée incomplète : cases vides pour garder la largeur des tuiles.
        val missing = (COLUMNS - tiles.size % COLUMNS) % COLUMNS
        for (k in 0 until missing) row?.addView(View(c), LinearLayout.LayoutParams(0, 1, 1f).apply { if (k < missing - 1) rightMargin = Ui.u(12) })
    }

    private fun tile(t: Tile): View {
        val v = Ui.vertical(c, Gravity.CENTER_HORIZONTAL)
        v.setPadding(Ui.u(8), Ui.u(14), Ui.u(8), Ui.u(10))
        v.background = Ui.pressable(Ui.round(Ui.SURFACE, 22f), 22f)
        val badge = FrameLayout(c)
        badge.background = Ui.round(Ui.ACCENT_DIM, 20f)
        val icon: View = if (t.icon != null) CategoryIcon(c, t.icon, Ui.ACCENT) else Icon(c, t.kind, Ui.ACCENT)
        badge.addView(icon, Ui.flp(Ui.u(if (t.icon != null) 36 else 30), Ui.u(if (t.icon != null) 36 else 30), Gravity.CENTER))
        v.addView(badge, LinearLayout.LayoutParams(Ui.u(60), Ui.u(60)))
        v.addView(Ui.ellipsize(Ui.text(c, t.label, 16f, Ui.TEXT, Ui.MEDIUM).apply { gravity = Gravity.CENTER }, 2),
            Ui.lp(Ui.MATCH, Ui.WRAP, t = 8))
        if (t.count != null) v.addView(Ui.text(c, t.count.toString(), 13f, Ui.TEXT3).apply { gravity = Gravity.CENTER }, Ui.lp(Ui.MATCH, Ui.WRAP, t = 2))
        v.isClickable = true
        v.setOnClickListener { t.open() }
        return v
    }

    override fun onShow() {
        render()
        unsub = a.app.recipes.onChange { render() }
        unsubHome = a.app.home.onChange { render() }
    }

    override fun onHide() {
        unsub?.invoke()
        unsubHome?.invoke()
    }

    override fun onKnob(delta: Int): Boolean {
        scroller.smoothScrollBy(0, delta * Ui.u(160))
        return true
    }

    private data class Group(val icon: String, val label: String, val categories: List<String>)

    private val GROUPS = listOf(
        Group("Salé", tr("Salé"), listOf("Soupes et veloutés", "Entrées et salades", "Apéritif", "Sauces, dips et condiments")),
        Group("Plats", tr("Plats"), listOf("Viandes", "Volailles", "Poissons et fruits de mer", "Plats végétariens",
            "Pâtes, riz et céréales", "Gratins, quiches et tartes salées", "Mijotés et plats complets", "Accompagnements et légumes")),
        Group("Boulangerie", tr("Boulangerie"), listOf("Pains et pizzas", "Viennoiseries et brioches")),
        Group("Sucré", tr("Sucré"), listOf("Gâteaux et tartes", "Biscuits et petits gâteaux", "Crèmes, mousses et entremets",
            "Fruits et compotes", "Glaces et sorbets", "Confitures et pâtes à tartiner", "Confiseries et chocolats", "Petit-déjeuner et goûter")),
        Group("Boissons", tr("Boissons"), listOf("Boissons")),
        Group("Bases", tr("Bases"), listOf("Bases et préparations")),
        Group("Bébé", tr("Bébé"), listOf("Bébé")),
    )

    private companion object {
        const val COLUMNS = 6
    }
}
