package app.toque.ui

import android.graphics.Outline
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.json.bool
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str
import app.toque.recipes.HomeServer
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Écran d'ambiance, en veille quand quelqu'un est dans la cuisine. À gauche l'heure, la météo
 * (conditions, heures, jours) et des jauges ; à droite la recette prévue, la musique, les alertes et
 * les actions. L'appli ne connaît aucun capteur de la maison : le serveur maison envoie des blocs
 * génériques, absents s'il n'a rien à montrer. Toucher agit ; le bouton rotatif ramène à l'accueil.
 */
class AmbientView(private val a: MainActivity) : FrameLayout(a) {
    private val main = Handler(Looper.getMainLooper())
    private val clock = Ui.text(a, "", 56f, Ui.TEXT, Ui.THIN)
    private val date = Ui.text(a, "", 20f, Ui.TEXT2, Ui.LIGHT)
    /** Pastille à côté de l'heure (couleur, titre court, valeur), par ex. un tarif d'électricité. */
    private val badge = Ui.horizontal(a, Gravity.CENTER)
    private val weather = Ui.vertical(a)
    private val gauges = Ui.horizontal(a, Gravity.TOP)
    private val right = Ui.vertical(a)
    private var alive = false
    private var lastJson: Map<String, Any?>? = null

    private val tick = object : Runnable {
        override fun run() {
            clock.text = MainActivity.now()
            date.text = SimpleDateFormat(tr("EEEE d MMMM"), I18n.locale).format(Date()).replaceFirstChar { it.uppercaseChar() }
            main.postDelayed(this, 10_000)
        }
    }
    private val reload = object : Runnable {
        override fun run() {
            load()
            main.postDelayed(this, 20_000)
        }
    }

    init {
        setBackgroundColor(Ui.BG)
        isClickable = true
        val row = Ui.horizontal(a, Gravity.TOP)
        row.setPadding(Ui.u(24), Ui.u(18), Ui.u(24), Ui.u(20))

        val left = Ui.vertical(a)
        val top = Ui.horizontal(a, Gravity.BOTTOM)
        top.setPadding(Ui.u(6), 0, Ui.u(6), 0)
        top.addView(clock)
        badge.visibility = View.GONE
        top.addView(badge, LinearLayout.LayoutParams(Ui.WRAP, Ui.u(36)).apply { leftMargin = Ui.u(16); bottomMargin = Ui.u(12) })
        top.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(date, Ui.lp(Ui.WRAP, Ui.WRAP, b = 8))
        left.addView(top)
        weather.background = Ui.round(Ui.SURFACE, 22f)
        weather.setPadding(Ui.u(18), Ui.u(12), Ui.u(18), Ui.u(8))
        left.addView(weather, LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { topMargin = Ui.u(8) })
        gauges.background = Ui.round(Ui.SURFACE, 22f)
        gauges.setPadding(Ui.u(8), Ui.u(6), Ui.u(8), Ui.u(6))
        gauges.visibility = View.GONE
        left.addView(gauges, Ui.lp(t = 12))
        row.addView(left, LinearLayout.LayoutParams(Ui.u(584), Ui.MATCH))

        row.addView(right, LinearLayout.LayoutParams(0, Ui.MATCH, 1f).apply { leftMargin = Ui.u(16) })
        addView(row, Ui.flp())
        addView(Ui.text(a, tr("Tournez le bouton pour cuisiner"), 12f, Ui.TEXT3),
            FrameLayout.LayoutParams(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM or Gravity.END).apply { rightMargin = Ui.u(28); bottomMargin = Ui.u(3) })
    }

    fun start() {
        if (alive) return
        alive = true
        main.post(tick)
        main.post(reload)
    }

    fun stop() {
        alive = false
        main.removeCallbacks(tick)
        main.removeCallbacks(reload)
    }

    private fun load() {
        Thread {
            val d = a.app.home.ambientContent()
            main.post { if (alive && d != null && d != lastJson) { lastJson = d; show(d) } }
        }.start()
    }

    private fun act(id: String) {
        Thread {
            val ok = a.app.home.ambientAction(id)
            main.post {
                if (!ok) a.toast(tr("Action impossible : serveur maison injoignable"), Ui.WARN)
                // Relectures rapprochées : le serveur renvoie tout de suite l'état demandé (volume,
                // pause), Spotify met ensuite une à trois secondes pour changer de morceau.
                for (ms in longArrayOf(150, 1_500, 4_000, 8_000)) main.postDelayed({ if (alive) load() }, ms)
            }
        }.start()
    }

    private fun show(d: Map<String, Any?>) {
        showBadge(d["pastille"].obj())
        showWeather(d["meteo"].obj())
        showGauges(d["jauges"].list().map { it.obj() })
        right.removeAllViews()
        d["menu"].obj().takeIf { it.isNotEmpty() }?.let { right.addView(menuCard(it), Ui.lp(b = 14)) }
        val music = d["musique"].obj()
        if (music.isNotEmpty()) right.addView(musicCard(music, compact = right.childCount > 0), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { bottomMargin = Ui.u(14) })
        else right.addView(View(a), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        right.addView(bottomBar(d["alertes"].list().mapNotNull { it as? String }, d["actions"].list().map { it.obj() }))
    }

    // ------------------------------------------------------------------ pastille
    private fun showBadge(b: Map<String, Any?>) {
        badge.removeAllViews()
        val title = b.str("title").orEmpty()
        val value = b.str("value").orEmpty()
        if (title.isEmpty() && value.isEmpty()) {
            badge.visibility = View.GONE
            return
        }
        fun color(k: String, def: Int) = try { android.graphics.Color.parseColor(b.str(k)) } catch (_: Exception) { def }
        val bg = color("bg", Ui.SURFACE2)
        val fg = color("fg", Ui.TEXT)
        badge.background = Ui.round(bg, 18f)
        badge.setPadding(Ui.u(14), 0, Ui.u(16), 0)
        if (title.isNotEmpty()) badge.addView(Ui.text(a, title, 16f, fg, Ui.BOLD).apply { letterSpacing = 0.06f })
        if (title.isNotEmpty() && value.isNotEmpty()) {
            badge.addView(View(a).apply { setBackgroundColor((fg and 0x00FFFFFF) or 0x55000000) },
                LinearLayout.LayoutParams(Ui.u(1.5f), Ui.u(16)).apply { leftMargin = Ui.u(10); rightMargin = Ui.u(10) })
        }
        if (value.isNotEmpty()) badge.addView(Ui.text(a, value, 16f, fg, Ui.MEDIUM))
        badge.contentDescription = b.str("label")
        badge.visibility = View.VISIBLE
    }

    // ------------------------------------------------------------------ météo
    private fun showWeather(m: Map<String, Any?>) {
        weather.removeAllViews()
        if (m.isEmpty()) return
        val now = Ui.horizontal(a)
        now.addView(WeatherIcon(a).apply { condition = m.str("cond").orEmpty(); night = m.bool("nuit") ?: false },
            LinearLayout.LayoutParams(Ui.u(72), Ui.u(72)).apply { rightMargin = Ui.u(12) })
        val temp = SpannableStringBuilder(m.str("temp").orEmpty())
        val u0 = temp.length
        temp.append(m.str("unite").orEmpty())
        temp.setSpan(AbsoluteSizeSpan(Ui.u(24)), u0, temp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        temp.setSpan(android.text.style.SuperscriptSpan(), u0, temp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        now.addView(Ui.text(a, temp, 56f, Ui.TEXT, Ui.LIGHT), Ui.lp(Ui.WRAP, Ui.WRAP, r = 16))
        val col = Ui.vertical(a)
        col.addView(Ui.ellipsize(Ui.text(a, m.str("texte").orEmpty(), 23f)))
        col.addView(Ui.ellipsize(Ui.text(a, m.str("detail").orEmpty(), 17f, Ui.TEXT2)), Ui.lp(t = 3))
        now.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        val attrs = Ui.vertical(a, Gravity.END)
        for (p in m["attributs"].list()) {
            val kv = p.list().mapNotNull { it as? String }
            if (kv.size < 2) continue
            val s = SpannableStringBuilder(kv[0] + " ")
            val k = s.length
            s.append(kv[1])
            s.setSpan(ForegroundColorSpan(Ui.TEXT), k, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            attrs.addView(Ui.text(a, s, 17f, Ui.TEXT2).apply { gravity = Gravity.END }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 2))
        }
        now.addView(attrs)
        weather.addView(now)
        m.str("resume")?.takeIf { it.isNotEmpty() }?.let {
            weather.addView(Ui.ellipsize(Ui.text(a, it, 18f, if (m.bool("resumeAlerte") == true) Ui.WARN else WeatherIcon.RAIN)), Ui.lp(t = 10))
        }
        weather.addView(View(a), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        val hours = m["heures"].list().map { it.obj() }
        if (hours.isNotEmpty()) {
            weather.addView(Ui.divider(a))
            val r = Ui.horizontal(a, Gravity.TOP)
            for (h in hours) {
                val c = Ui.vertical(a, Gravity.CENTER_HORIZONTAL)
                c.addView(ctext(h.str("h").orEmpty(), 17f, Ui.TEXT2))
                c.addView(WeatherIcon(a).apply { condition = h.str("cond").orEmpty(); night = h.bool("nuit") ?: false },
                    LinearLayout.LayoutParams(Ui.u(46), Ui.u(46)).apply { topMargin = Ui.u(4); bottomMargin = Ui.u(2) })
                c.addView(ctext(h.str("t").orEmpty(), 21f, Ui.TEXT))
                c.addView(ctext(h.str("mm").orEmpty(), 14f, WeatherIcon.RAIN), Ui.lp(Ui.MATCH, Ui.u(19)))
                r.addView(c, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            }
            weather.addView(r, Ui.lp(t = 10, b = 8))
        }
        weather.addView(View(a), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        val days = m["jours"].list().map { it.obj() }
        if (days.isNotEmpty()) {
            weather.addView(Ui.divider(a))
            val r = Ui.horizontal(a, Gravity.TOP)
            for (j in days) {
                val c = Ui.vertical(a, Gravity.CENTER_HORIZONTAL)
                c.addView(ctext(j.str("j").orEmpty(), 17f, Ui.TEXT2))
                c.addView(WeatherIcon(a).apply { condition = j.str("cond").orEmpty() },
                    LinearLayout.LayoutParams(Ui.u(50), Ui.u(50)).apply { topMargin = Ui.u(2) })
                val s = SpannableStringBuilder(j.str("max").orEmpty() + "  ")
                val k = s.length
                s.append(j.str("min").orEmpty())
                s.setSpan(ForegroundColorSpan(Ui.TEXT3), k, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                c.addView(ctext(s, 20f, Ui.TEXT))
                c.addView(ctext(j.str("mm").orEmpty(), 14f, WeatherIcon.RAIN), Ui.lp(Ui.MATCH, Ui.u(19)))
                r.addView(c, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            }
            weather.addView(r, Ui.lp(t = 10))
        }
    }

    // ------------------------------------------------------------------ jauges
    private fun showGauges(list: List<Map<String, Any?>>) {
        gauges.removeAllViews()
        gauges.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        for (g in list) {
            val c = Ui.vertical(a, Gravity.CENTER_HORIZONTAL)
            c.addView(Gauge(a).apply {
                min = (g["min"] as? Number)?.toFloat() ?: 16f
                max = (g["max"] as? Number)?.toFloat() ?: 26f
                value = (g["valeur"] as? Number)?.toFloat() ?: 0f
                label = g.str("texte").orEmpty()
                unit = g.str("unite").orEmpty()
            }, LinearLayout.LayoutParams(Ui.u(86), Ui.u(72)))
            c.addView(Ui.ellipsize(Ui.text(a, g.str("nom").orEmpty(), 16f, Ui.TEXT2).apply { gravity = Gravity.CENTER }),
                LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP).apply { topMargin = -Ui.u(10) })
            gauges.addView(c, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        }
    }

    // ------------------------------------------------------------------ recette
    private fun menuCard(m: Map<String, Any?>): View {
        val card = Ui.horizontal(a)
        card.background = Ui.pressable(Ui.round(Ui.SURFACE, 22f), 22f)
        card.setPadding(Ui.u(12), Ui.u(12), Ui.u(14), Ui.u(12))
        val id = m.str("id").orEmpty()
        val recipe = a.app.recipes.get(id)
        val img = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; rounded(this, 16f); background = Ui.round(Ui.SURFACE2, 16f) }
        ImageLoader.load(img, recipe?.let { a.app.recipes.imageFile(it.image) }, Ui.u(120))
        card.addView(img, LinearLayout.LayoutParams(Ui.u(92), Ui.u(92)).apply { rightMargin = Ui.u(14) })
        val col = Ui.vertical(a)
        col.addView(label(m.str("label").orEmpty()))
        col.addView(Ui.ellipsize(Ui.text(a, m.str("titre") ?: recipe?.title.orEmpty(), 19f), 2), Ui.lp(t = 4))
        m.str("detail")?.takeIf { it.isNotEmpty() }?.let { col.addView(Ui.ellipsize(Ui.text(a, it, 14f, Ui.TEXT2)), Ui.lp(t = 4)) }
        card.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        val go = FrameLayout(a)
        go.background = Ui.round(Ui.ACCENT, 20f)
        go.addView(Icon(a, Icon.Kind.CHEVRON, Ui.BG), FrameLayout.LayoutParams(Ui.u(20), Ui.u(20), Gravity.CENTER))
        card.addView(go, LinearLayout.LayoutParams(Ui.u(40), Ui.u(40)).apply { leftMargin = Ui.u(10) })
        if (recipe != null) {
            card.isClickable = true
            card.setOnClickListener { a.openFromAmbient(recipe) }
        } else {
            go.visibility = View.GONE
        }
        return card
    }

    // ------------------------------------------------------------------ musique
    /** [compact] : une recette du jour occupe déjà le haut de la colonne, tout doit tenir en moins de place. */
    private fun musicCard(m: Map<String, Any?>, compact: Boolean): View {
        val rows = 1
        val base = HomeServer.baseUrl(a.prefs.homeServer).orEmpty()
        fun url(p: String?) = p?.let { if (it.startsWith("/")) base + it else it }
        val card = Ui.vertical(a)
        card.background = Ui.round(Ui.SURFACE, 22f)
        card.setPadding(Ui.u(16), Ui.u(14), Ui.u(16), Ui.u(12))
        val head = Ui.horizontal(a)
        head.addView(label(tr("Vos playlists")), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        val volume = m["volume"] as? Number
        if (volume != null) {
            // Volume toujours à portée, même avant que la lecture ne soit signalée.
            head.addView(round(Icon.Kind.MINUS, 40) { act("musique:volume:-10") })
            head.addView(Ui.text(a, "${volume.toInt()} %", 15f, Ui.TEXT2).apply { gravity = Gravity.CENTER },
                LinearLayout.LayoutParams(Ui.u(52), Ui.WRAP))
            head.addView(round(Icon.Kind.PLUS, 40) { act("musique:volume:10") })
        } else {
            head.addView(Ui.text(a, m.str("source").orEmpty(), 14f, Ui.TEXT3))
        }
        card.addView(head)
        // Pochettes en colonnes de [rows] ; on fait défiler de côté pour voir les suivantes.
        val strip = Ui.horizontal(a, Gravity.TOP)
        val lists = m["listes"].list().map { it.obj() }
        val size = Ui.u(if (compact) 84 else 104)
        var column: LinearLayout? = null
        for ((k, p) in lists.withIndex()) {
            if (k % rows == 0) {
                column = Ui.vertical(a)
                strip.addView(column, Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
            }
            val c = Ui.vertical(a)
            val box = FrameLayout(a)
            val img = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; rounded(this, 12f); background = Ui.round(Ui.SURFACE2, 12f) }
            RemoteImages.load(img, url(p.str("image")))
            box.addView(img, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            val play = FrameLayout(a)
            play.background = Ui.round(SPOTIFY, 16f)
            play.addView(Icon(a, Icon.Kind.PLAY, Ui.BG), FrameLayout.LayoutParams(Ui.u(14), Ui.u(14), Gravity.CENTER))
            box.addView(play, FrameLayout.LayoutParams(Ui.u(32), Ui.u(32), Gravity.BOTTOM or Gravity.END).apply { rightMargin = Ui.u(6); bottomMargin = Ui.u(6) })
            c.addView(box, LinearLayout.LayoutParams(size, size))
            c.addView(Ui.ellipsize(Ui.text(a, p.str("nom").orEmpty(), 16f)), LinearLayout.LayoutParams(size, Ui.WRAP).apply { topMargin = Ui.u(5) })
            val id = p.str("id").orEmpty()
            c.isClickable = true
            c.setOnClickListener { a.toast(tr("Lecture de « %s »", p.str("nom").orEmpty()), Ui.OK); act("musique:jouer:$id") }
            column!!.addView(c, Ui.lp(Ui.WRAP, Ui.WRAP, b = if (rows > 1 && k % rows == 0) 10 else 0))
        }
        val scroller = android.widget.HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER }
        scroller.addView(strip)
        card.addView(scroller, Ui.lp(t = 10))
        val shortcuts = m["raccourcis"].list().map { it.obj() }.take(2)
        if (shortcuts.isNotEmpty()) {
            val row = Ui.horizontal(a)
            for ((k, r) in shortcuts.withIndex()) row.addView(shortcut(r, ::url), LinearLayout.LayoutParams(0, Ui.u(68), 1f).apply { if (k == 0) rightMargin = Ui.u(10) })
            card.addView(row, Ui.lp(t = 12))
        }
        card.addView(View(a), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        val now = m["enCours"].obj()
        if (now.isNotEmpty()) {
            val box = Ui.vertical(a)
            box.background = Ui.round(Ui.SURFACE2, 16f)
            box.setPadding(Ui.u(10), Ui.u(10), Ui.u(10), Ui.u(10))
            if (compact) box.setPadding(Ui.u(8), Ui.u(8), Ui.u(8), Ui.u(8))
            val r = Ui.horizontal(a)
            val img = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; rounded(this, 10f) }
            RemoteImages.load(img, url(now.str("image")))
            r.addView(img, LinearLayout.LayoutParams(Ui.u(if (compact) 48 else 60), Ui.u(if (compact) 48 else 60)).apply { rightMargin = Ui.u(12) })
            val col = Ui.vertical(a)
            col.addView(Ui.ellipsize(Ui.text(a, now.str("titre").orEmpty(), 19f)))
            col.addView(Ui.ellipsize(Ui.text(a, now.str("artiste").orEmpty(), 16f, Ui.TEXT2)))
            col.addView(ProgressLine(a).apply { color = SPOTIFY; progress = (now["progression"] as? Number)?.toFloat() ?: 0f },
                Ui.lp(Ui.MATCH, Ui.u(4), t = 7))
            r.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            box.addView(r)
            val playing = now.bool("lecture") ?: false
            if (compact) {
                // Une seule ligne : précédent / lecture / suivant à droite du titre.
                r.addView(round(Icon.Kind.PREV, 44) { act("musique:precedent") }, LinearLayout.LayoutParams(Ui.u(44), Ui.u(44)).apply { leftMargin = Ui.u(8) })
                r.addView(round(if (playing) Icon.Kind.PAUSE else Icon.Kind.PLAY, 48, SPOTIFY) { act(if (playing) "musique:pause" else "musique:reprendre") },
                    LinearLayout.LayoutParams(Ui.u(48), Ui.u(48)).apply { leftMargin = Ui.u(8) })
                r.addView(round(Icon.Kind.NEXT, 44) { act("musique:suivant") }, LinearLayout.LayoutParams(Ui.u(44), Ui.u(44)).apply { leftMargin = Ui.u(8) })
                card.addView(box)
            }
            val ctl = Ui.horizontal(a, Gravity.CENTER)
            ctl.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
            ctl.addView(round(Icon.Kind.PREV, 52) { act("musique:precedent") })
            ctl.addView(round(if (playing) Icon.Kind.PAUSE else Icon.Kind.PLAY, 60, SPOTIFY) { act(if (playing) "musique:pause" else "musique:reprendre") },
                LinearLayout.LayoutParams(Ui.u(60), Ui.u(60)).apply { leftMargin = Ui.u(14); rightMargin = Ui.u(14) })
            ctl.addView(round(Icon.Kind.NEXT, 52) { act("musique:suivant") })
            ctl.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
            if (!compact) {
                box.addView(ctl, Ui.lp(t = 10))
                card.addView(box)
            }
        }
        val dev = m.str("appareil").orEmpty()
        if (dev.isNotEmpty()) card.addView(Ui.ellipsize(Ui.text(a, dev, 15f, Ui.TEXT2)), Ui.lp(t = 8))
        return card
    }

    /** Raccourci audio (radio en direct, dernier épisode d'un podcast) : vignette et deux lignes. */
    private fun shortcut(r: Map<String, Any?>, url: (String?) -> String?): View {
        val off = r.bool("inactif") ?: false
        val tile = Ui.horizontal(a)
        tile.background = Ui.pressable(Ui.round(Ui.SURFACE2, 16f), 16f)
        tile.setPadding(Ui.u(8), Ui.u(8), Ui.u(10), Ui.u(8))
        val col = try { android.graphics.Color.parseColor(r.str("couleur")) } catch (_: Exception) { Ui.SURFACE3 }
        val thumb = FrameLayout(a)
        thumb.background = Ui.round(col, 10f)
        val image = url(r.str("image"))
        if (image != null) {
            val img = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; rounded(this, 10f) }
            RemoteImages.load(img, image)
            thumb.addView(img, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        } else {
            thumb.addView(Ui.text(a, r.str("sigle").orEmpty(), 15f, android.graphics.Color.WHITE, Ui.BOLD).apply { gravity = Gravity.CENTER },
                FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        }
        thumb.addView(FrameLayout(a).apply {
            background = Ui.round(SPOTIFY, 11f)
            addView(Icon(a, Icon.Kind.PLAY, Ui.BG), FrameLayout.LayoutParams(Ui.u(10), Ui.u(10), Gravity.CENTER))
        }, FrameLayout.LayoutParams(Ui.u(22), Ui.u(22), Gravity.BOTTOM or Gravity.END).apply { rightMargin = -Ui.u(4); bottomMargin = -Ui.u(4) })
        thumb.clipChildren = false
        tile.clipChildren = false
        tile.addView(thumb, LinearLayout.LayoutParams(Ui.u(52), Ui.u(52)).apply { rightMargin = Ui.u(12) })
        val txt = Ui.vertical(a)
        txt.addView(Ui.ellipsize(Ui.text(a, r.str("nom").orEmpty(), 16f, Ui.TEXT, Ui.MEDIUM)))
        txt.addView(Ui.ellipsize(Ui.text(a, r.str("sous").orEmpty(), 13f, if (off) Ui.WARN else Ui.TEXT2)), Ui.lp(t = 2))
        tile.addView(txt, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        if (off) tile.alpha = 0.6f
        val id = r.str("id").orEmpty()
        tile.isClickable = true
        tile.setOnClickListener {
            if (off) a.toast(r.str("sous").orEmpty(), Ui.WARN)
            else { a.toast(tr("Lecture de « %s »", r.str("nom").orEmpty()), Ui.OK); act(id) }
        }
        return tile
    }

    private fun round(kind: Icon.Kind, size: Int, bg: Int = 0x14FFFFFF, onClick: () -> Unit): View {
        val b = FrameLayout(a)
        b.background = Ui.pressable(Ui.round(bg, size / 2f), size / 2f)
        b.addView(Icon(a, kind, if (bg == SPOTIFY) Ui.BG else Ui.TEXT), FrameLayout.LayoutParams(Ui.u(size * 0.45f), Ui.u(size * 0.45f), Gravity.CENTER))
        b.isClickable = true
        b.setOnClickListener { onClick() }
        b.layoutParams = LinearLayout.LayoutParams(Ui.u(size), Ui.u(size))
        return b
    }

    // ------------------------------------------------------------------ alertes et actions
    private fun bottomBar(alerts: List<String>, actions: List<Map<String, Any?>>): View {
        val bar = Ui.horizontal(a)
        for (al in alerts.take(2)) {
            val chip = Ui.chip(a, al, Icon.Kind.WARNING, Ui.WARN, 0x33FFC14D, 15f)
            chip.minimumHeight = Ui.u(48)
            bar.addView(chip, Ui.lp(Ui.WRAP, Ui.WRAP, r = 8))
        }
        bar.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        for (ac in actions) {
            val id = ac.str("id") ?: continue
            val on = ac.bool("actif") ?: false
            val kind = try { Icon.Kind.valueOf(ac.str("icone").orEmpty()) } catch (_: Exception) { Icon.Kind.BULB }
            val b = Ui.button(a, ac.str("label").orEmpty(), if (on) Ui.Style.PRIMARY else Ui.Style.SECONDARY, kind, height = 48, textSize = 15f) { act(id) }
            b.setPadding(Ui.u(14), 0, Ui.u(16), 0)
            bar.addView(b, Ui.lp(Ui.WRAP, Ui.WRAP, l = 8))
        }
        return bar
    }

    private fun ctext(s: CharSequence, size: Float, color: Int) = Ui.text(a, s, size, color).apply { gravity = Gravity.CENTER_HORIZONTAL }

    private fun label(s: String) = Ui.text(a, s.uppercase(), 14f, Ui.TEXT2, Ui.MEDIUM).apply { letterSpacing = 0.09f }

    private fun rounded(v: View, r: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, Ui.uf(r))
        }
        v.clipToOutline = true
    }

    companion object {
        const val SPOTIFY = 0xFF1ED760.toInt()
    }
}
