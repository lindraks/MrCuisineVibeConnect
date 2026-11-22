package app.toque.ui.screens

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.Prefs
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.recipes.RecipeFilter
import app.toque.core.recipes.SearchIndex
import app.toque.ui.Icon
import app.toque.ui.ImageLoader
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Ui
import java.util.concurrent.Executors

/**
 * Catalogue des recettes Lidl : recherche instantanée tolérante (accents, pluriels, fautes de
 * frappe), filtres, et recherche par ingrédients (séparés par des virgules).
 */
class RecipesScreen(
    a: MainActivity,
    private val focusSearch: Boolean = false,
    favorites: Boolean = false,
    initialUsage: Usage = Usage.NONE,
    /** Rayon choisi dans le panneau des catégories : la liste et ses filtres restent dans ce rayon. */
    private val category: String = "",
) : Screen(a) {
    /** Listes tirées des cuissons (dernières, plus cuisinées) ou des recettes prévues (« À cuisiner »). */
    enum class Usage { NONE, RECENT, MOST, PLANNED }

    override val title = when {
        favorites -> tr("Favoris")
        initialUsage == Usage.RECENT -> tr("Récentes")
        initialUsage == Usage.MOST -> tr("Fréquentes")
        initialUsage == Usage.PLANNED -> tr("À cuisiner")
        category.isNotEmpty() -> category
        else -> tr("Recettes")
    }

    private var filter = RecipeFilter(favoritesOnly = favorites, category = category)
    private var usage = initialUsage
    /** Recherche par ingrédients (« butternut, lardons ») : les recettes qui en ont le plus d'abord. */
    private var query = ""
    private var results: List<SearchIndex.Hit> = emptyList()
    private var generation = 0
    private lateinit var input: EditText
    /** Tri à droite de la recherche : « ★ Note » / « A → Z » (listes ordinaires seulement). */
    private lateinit var sortBox: LinearLayout
    private lateinit var grid: GridView
    private lateinit var count: TextView
    private lateinit var empty: LinearLayout
    private lateinit var emptyText: TextView
    private val adapter = Adapter()
    private var unsub: (() -> Unit)? = null
    private var unsubHome: (() -> Unit)? = null
    private val runSearch = Runnable { search() }

    override fun build(): View {
        val root = Ui.vertical(c)
        root.setPadding(Ui.u(24), Ui.u(6), Ui.u(24), 0)

        val bar = Ui.horizontal(c)
        val field = Ui.horizontal(c)
        field.setPadding(Ui.u(18), 0, Ui.u(8), 0)
        field.background = Ui.round(Ui.SURFACE, 26f, Ui.STROKE, 1f)
        field.addView(Icon(c, Icon.Kind.SEARCH, Ui.TEXT2), LinearLayout.LayoutParams(Ui.u(22), Ui.u(22)).apply { rightMargin = Ui.u(10) })
        input = EditText(c).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.uf(19f))
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.TEXT3)
            hint = searchHint()
            background = null
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c1: Int, a1: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c1: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    view.removeCallbacks(runSearch)
                    view.postDelayed(runSearch, 120)
                }
            })
            setOnEditorActionListener { _, _, _ -> a.hideKeyboard(); true }
        }
        field.addView(input, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        val clear = Icon(c, Icon.Kind.CLOSE, Ui.TEXT2)
        clear.setPadding(Ui.u(10), Ui.u(10), Ui.u(10), Ui.u(10))
        clear.setOnClickListener { input.setText("") }
        field.addView(clear, LinearLayout.LayoutParams(Ui.u(44), Ui.u(44)))
        // Recherche 20 % plus basse qu'avant (64 → 51) et plus de bandeau de puces : place pour les recettes.
        bar.addView(field, LinearLayout.LayoutParams(0, Ui.u(51), 1f))
        count = Ui.text(c, "", 16f, Ui.TEXT2)
        bar.addView(count, Ui.lp(Ui.WRAP, Ui.WRAP, l = 14))
        sortBox = Ui.horizontal(c)
        sortBox.background = Ui.round(Ui.SURFACE, 26f, Ui.STROKE, 1f)
        sortBox.setPadding(Ui.u(4), Ui.u(4), Ui.u(4), Ui.u(4))
        bar.addView(sortBox, Ui.lp(Ui.WRAP, Ui.WRAP, l = 14))
        root.addView(bar)
        root.addView(View(c), Ui.lp(Ui.MATCH, Ui.u(12)))

        val body = FrameLayout(c)
        grid = GridView(c).apply {
            numColumns = 4
            horizontalSpacing = Ui.u(14)
            verticalSpacing = Ui.u(14)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = android.graphics.drawable.ColorDrawable(0)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setPadding(0, 0, 0, Ui.u(20))
            clipToPadding = false
            adapter = this@RecipesScreen.adapter
            setOnItemClickListener { _, _, pos, _ ->
                val hit = results.getOrNull(pos) ?: return@setOnItemClickListener
                val r = a.app.recipes.get(hit.entry.id)
                if (r != null) a.push(RecipeScreen(a, r, initialServings = if (usage == Usage.PLANNED) a.app.home.plannedServings(r.id) else 0))
            }
            setOnScrollListener(object : AbsListView.OnScrollListener {
                override fun onScrollStateChanged(v: AbsListView?, state: Int) {
                    if (state != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) a.hideKeyboard()
                }
                override fun onScroll(v: AbsListView?, first: Int, visible: Int, total: Int) {}
            })
        }
        body.addView(grid, Ui.flp())
        empty = Ui.vertical(c, Gravity.CENTER)
        empty.addView(Icon(c, Icon.Kind.BOOK, Ui.TEXT3), LinearLayout.LayoutParams(Ui.u(64), Ui.u(64)))
        emptyText = Ui.text(c, "", 21f, Ui.TEXT2).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.2f) }
        empty.addView(emptyText, Ui.lp(Ui.u(640), Ui.WRAP, t = 16))
        body.addView(empty, Ui.flp())
        root.addView(body, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        return root
    }

    private fun buildChips() {
        sortBox.removeAllViews()
        // Ordre imposé (récentes, fréquentes, à cuisiner) : pas de tri à choisir.
        sortBox.visibility = if (usage == Usage.NONE) View.VISIBLE else View.GONE
        fun option(label: String, alphabetical: Boolean) {
            val selected = filter.alphabetical == alphabetical
            val t = Ui.text(c, label, 16f, if (selected) 0xFF1A0E05.toInt() else Ui.TEXT2, Ui.MEDIUM)
            t.gravity = android.view.Gravity.CENTER
            t.setPadding(Ui.u(16), 0, Ui.u(16), 0)
            t.background = if (selected) Ui.round(Ui.ACCENT, 22f) else null
            t.setOnClickListener { filter = filter.copy(alphabetical = alphabetical); buildChips(); search() }
            sortBox.addView(t, LinearLayout.LayoutParams(Ui.WRAP, Ui.u(41)))
        }
        option(tr("★ Note"), false)
        option(tr("A → Z"), true)
    }

    private fun search() {
        val gen = ++generation
        val q = query
        val f = filter
        val u = usage
        // Plusieurs ingrédients séparés par des virgules : recherche « avec ce que j'ai ».
        val ingredients = q.contains(',')
        val repo = a.app.recipes
        val index = repo.index
        searchExec.execute {
            val found = when {
                ingredients && q.isNotBlank() -> index.withIngredients(index.ingredientsOf(q), f)
                else -> index.search(q, f)
            }
            val hits = if (u == Usage.NONE) found else {
                // Ordre des cuissons ; la recherche tapée, s'il y en a une, ne fait que filtrer.
                val ids = when (u) {
                    Usage.RECENT -> repo.recentIds()
                    Usage.PLANNED -> a.app.home.planned.map { it.id }
                    else -> repo.mostCookedIds()
                }
                val byId = index.entries.associateBy { it.id }
                val keep = if (q.isBlank()) null else found.mapTo(HashSet()) { it.entry.id }
                ids.filter { keep == null || it in keep }.mapNotNull { id -> byId[id]?.let { SearchIndex.Hit(it, 0.0, 0) } }
            }
            view.post {
                if (gen != generation) return@post
                results = hits
                adapter.notifyDataSetChanged()
                refreshEmpty()
            }
        }
    }

    private fun refreshEmpty() {
        val repo = a.app.recipes
        val none = repo.loaded && repo.count == 0
        count.text = if (results.isEmpty()) "" else if (results.size == 1) tr("1 recette") else tr("%d recettes", results.size)
        empty.visibility = if (results.isEmpty() && repo.loaded) View.VISIBLE else View.GONE
        emptyText.text = when {
            none -> tr("Les recettes apparaîtront après la première synchronisation.\nRéglages › Recettes › Enregistrer et synchroniser.")
            usage == Usage.PLANNED && query.isBlank() -> tr("Rien à cuisiner pour l'instant.\nAjoutez des recettes depuis le téléphone (appli Toque) ou avec le bouton « À cuisiner » d'une recette.")
            usage != Usage.NONE && query.isBlank() -> tr("Aucune recette cuisinée pour l'instant.\nLes recettes apparaissent ici dès leur première cuisson.")
            filter.favoritesOnly && query.isBlank() -> tr("Aucun favori pour l'instant.\nTouchez l'étoile d'une recette pour l'ajouter.")
            else -> tr("Aucune recette ne correspond.\nAstuce : séparez plusieurs ingrédients par des virgules.")
        }
    }

    private fun searchHint(): String =
        if (category.isNotEmpty()) tr("Chercher dans « %s »…", category) else tr("Recette, ingrédient… (ex. : poulet, curry)")

    override fun onShow() {
        buildChips()
        search()
        unsub = a.app.recipes.onChange { buildChips(); search() }
        unsubHome = a.app.home.onChange { if (usage == Usage.PLANNED) search() }
        if (usage == Usage.PLANNED) a.app.home.refreshSoon()
        if (focusSearch) view.postDelayed({
            input.requestFocus()
            (c.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    override fun onHide() {
        unsub?.invoke()
        unsubHome?.invoke()
    }

    override fun onKnob(delta: Int): Boolean {
        grid.smoothScrollBy(delta * Ui.u(240), 150)
        return true
    }

    /** « Aujourd'hui », « Hier », « Il y a 3 jours », puis la date. */
    private fun ago(ms: Long): String {
        val day = 86_400_000L
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        val midnight = cal.timeInMillis
        return when {
            ms >= midnight -> tr("Aujourd'hui")
            ms >= midnight - day -> tr("Hier")
            ms >= midnight - 6 * day -> tr("Il y a %d jours", ((midnight - ms) / day + 1).toInt())
            else -> java.text.SimpleDateFormat(tr("d MMM"), I18n.locale).format(java.util.Date(ms))
        }
    }

    private companion object {
        /** Un seul fil de recherche partagé par tous les écrans Recettes. */
        val searchExec = Executors.newSingleThreadExecutor()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = results.size
        override fun getItem(position: Int) = results[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder = (convertView?.tag as? Holder) ?: Holder(c)
            holder.bind(results[position])
            return holder.root
        }
    }

    private inner class Holder(c: Context) {
        val root = Ui.vertical(c)
        val imageBox = FrameLayout(c)
        val image = ImageView(c)
        val initials = Ui.text(c, "", 44f, 0x55FFFFFF, Ui.THIN)
        val title = Ui.ellipsize(Ui.text(c, "", 18f, Ui.TEXT, Ui.MEDIUM), 2)
        val meta = Ui.ellipsize(Ui.text(c, "", 15f, Ui.TEXT2))
        val star = Icon(c, Icon.Kind.STAR_FILLED, Ui.ACCENT)

        init {
            root.tag = this
            root.background = Ui.round(Ui.SURFACE, 22f)
            root.layoutParams = AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, Ui.u(232))
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            imageBox.addView(initials, Ui.flp(Ui.WRAP, Ui.WRAP, Gravity.CENTER))
            imageBox.addView(image, Ui.flp())
            imageBox.addView(star, Ui.flp(Ui.u(26), Ui.u(26), Gravity.TOP or Gravity.END).apply { setMargins(0, Ui.u(10), Ui.u(10), 0) })
            imageBox.clipToOutline = true
            imageBox.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height + Ui.u(22), Ui.uf(22f))
                }
            }
            root.addView(imageBox, LinearLayout.LayoutParams(Ui.MATCH, Ui.u(128)))
            root.addView(title, Ui.lp(l = 14, t = 10, r = 12))
            root.addView(meta, Ui.lp(l = 14, t = 4, r = 12))
        }

        fun bind(h: SearchIndex.Hit) {
            val e = h.entry
            title.text = e.title
            val parts = ArrayList<String>()
            if (query.contains(',') && query.isNotBlank() && h.matched > 0) {
                val wanted = a.app.recipes.index.ingredientsOf(query).size
                parts += tr("%d/%d ingrédients", h.matched, wanted)
            }
            val u = a.app.recipes.usage(e.id)
            if (u != null && usage == Usage.MOST) parts += if (u.count == 1) tr("1 fois") else tr("%d fois", u.count)
            if (u != null && usage == Usage.RECENT) parts += ago(u.lastMs)
            if (usage == Usage.PLANNED) a.app.home.plannedServings(e.id).takeIf { it > 0 }?.let { parts += tr("pour %d pers.", it) }
            if (e.ratingCount > 0) parts += "★ " + String.format(I18n.locale, "%.1f", e.rating) + " (${e.ratingCount})"
            if (e.totalMinutes > 0) parts += "${e.totalMinutes} min"
            if (e.category.isNotBlank()) parts += e.category
            meta.text = parts.joinToString(" · ")
            star.visibility = if (a.app.recipes.isFavorite(e.id)) View.VISIBLE else View.GONE
            imageBox.background = Ui.gradient(Ui.placeholderColors(e.title), 0f)
            initials.text = Ui.initials(e.title)
            ImageLoader.load(image, a.app.recipes.imageFile(e.image), Ui.u(260))
        }
    }
}
