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
        top.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(date, Ui.lp(Ui.WRAP, Ui.WRAP, b = 8))
        left.addView(top)
        weather.background = Ui.round(Ui.SURFACE, 22f)
        weather.setPadding(Ui.u(18), Ui.u(14), Ui.u(18), Ui.u(10))
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
                main.postDelayed({ if (alive) load() }, 700)
            }
        }.start()
    }

    private fun show(d: Map<String, Any?>) {
        showWeather(d["meteo"].obj())
        showGauges(d["jauges"].list().map { it.obj() })
        right.removeAllViews()
        d["menu"].obj().takeIf { it.isNotEmpty() }?.let { right.addView(menuCard(it), Ui.lp(b = 14)) }
        val music = d["musique"].obj()
        if (music.isNotEmpty()) right.addView(musicCard(music), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f).apply { bottomMargin = Ui.u(14) })
        else right.addView(View(a), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        right.addView(bottomBar(d["alertes"].list().mapNotNull { it as? String }, d["actions"].list().map { it.obj() }))
    }

    // ------------------------------------------------------------------ météo
    private fun showWeather(m: Map<String, Any?>) {
        weather.removeAllViews()
        if (m.isEmpty()) return
        val now = Ui.horizontal(a)
        now.addView(WeatherIcon(a).apply { condition = m.str("cond").orEmpty(); night = m.bool("nuit") ?: false },
            LinearLayout.LayoutParams(Ui.u(64), Ui.u(64)).apply { rightMargin = Ui.u(12) })
        val temp = SpannableStringBuilder(m.str("temp").orEmpty())
        val u0 = temp.length
        temp.append(m.str("unite").orEmpty())
        temp.setSpan(AbsoluteSizeSpan(Ui.u(22)), u0, temp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        temp.setSpan(android.text.style.SuperscriptSpan(), u0, temp.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        now.addView(Ui.text(a, temp, 50f, Ui.TEXT, Ui.LIGHT), Ui.lp(Ui.WRAP, Ui.WRAP, r = 16))
        val col = Ui.vertical(a)
        col.addView(Ui.ellipsize(Ui.text(a, m.str("texte").orEmpty(), 20f)))
        col.addView(Ui.ellipsize(Ui.text(a, m.str("detail").orEmpty(), 15f, Ui.TEXT2)), Ui.lp(t = 3))
        now.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        val attrs = Ui.vertical(a, Gravity.END)
        for (p in m["attributs"].list()) {
            val kv = p.list().mapNotNull { it as? String }
            if (kv.size < 2) continue
            val s = SpannableStringBuilder(kv[0] + " ")
            val k = s.length
            s.append(kv[1])
            s.setSpan(ForegroundColorSpan(Ui.TEXT), k, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            attrs.addView(Ui.text(a, s, 15f, Ui.TEXT2).apply { gravity = Gravity.END }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 2))
        }
        now.addView(attrs)
        weather.addView(now)
        m.str("resume")?.takeIf { it.isNotEmpty() }?.let {
            weather.addView(Ui.ellipsize(Ui.text(a, it, 15f, if (m.bool("resumeAlerte") == true) Ui.WARN else WeatherIcon.RAIN)), Ui.lp(t = 10))
        }
        weather.addView(View(a), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        val hours = m["heures"].list().map { it.obj() }
        if (hours.isNotEmpty()) {
            weather.addView(Ui.divider(a))
            val r = Ui.horizontal(a, Gravity.TOP)
            for (h in hours) {
                val c = Ui.vertical(a, Gravity.CENTER_HORIZONTAL)
                c.addView(Ui.text(a, h.str("h").orEmpty(), 14f, Ui.TEXT2))
                c.addView(WeatherIcon(a).apply { condition = h.str("cond").orEmpty(); night = h.bool("nuit") ?: false },
                    LinearLayout.LayoutParams(Ui.u(32), Ui.u(32)).apply { topMargin = Ui.u(3); bottomMargin = Ui.u(2) })
                c.addView(Ui.text(a, h.str("t").orEmpty(), 17f))
                c.addView(Ui.text(a, h.str("mm").orEmpty(), 12f, WeatherIcon.RAIN), Ui.lp(Ui.WRAP, Ui.u(16)))
                r.addView(c, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            }
            weather.addView(r, Ui.lp(t = 8, b = 6))
        }
        weather.addView(View(a), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        val days = m["jours"].list().map { it.obj() }
        if (days.isNotEmpty()) {
            weather.addView(Ui.divider(a))
            val r = Ui.horizontal(a, Gravity.TOP)
            for (j in days) {
                val c = Ui.vertical(a, Gravity.CENTER_HORIZONTAL)
                c.addView(Ui.text(a, j.str("j").orEmpty(), 14f, Ui.TEXT2))
                c.addView(WeatherIcon(a).apply { condition = j.str("cond").orEmpty() },
                    LinearLayout.LayoutParams(Ui.u(34), Ui.u(34)).apply { topMargin = Ui.u(2) })
                val s = SpannableStringBuilder(j.str("max").orEmpty() + "  ")
                val k = s.length
                s.append(j.str("min").orEmpty())
                s.setSpan(ForegroundColorSpan(Ui.TEXT3), k, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                c.addView(Ui.text(a, s, 16f))
                c.addView(Ui.text(a, j.str("mm").orEmpty(), 12f, WeatherIcon.RAIN), Ui.lp(Ui.WRAP, Ui.u(16)))
                r.addView(c, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            }
            weather.addView(r, Ui.lp(t = 8))
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
            }, LinearLayout.LayoutParams(Ui.u(80), Ui.u(68)))
            c.addView(Ui.ellipsize(Ui.text(a, g.str("nom").orEmpty(), 14f, Ui.TEXT2).apply { gravity = Gravity.CENTER }),
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
    private fun musicCard(m: Map<String, Any?>): View {
        val base = HomeServer.baseUrl(a.prefs.homeServer).orEmpty()
        fun url(p: String?) = p?.let { if (it.startsWith("/")) base + it else it }
        val card = Ui.vertical(a)
        card.background = Ui.round(Ui.SURFACE, 22f)
        card.setPadding(Ui.u(18), Ui.u(16), Ui.u(18), Ui.u(12))
        val head = Ui.horizontal(a)
        head.addView(label(tr("Vos playlists")), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        head.addView(Ui.text(a, m.str("source").orEmpty(), 13f, Ui.TEXT3))
        card.addView(head)
        val grid = Ui.horizontal(a, Gravity.TOP)
        val lists = m["listes"].list().map { it.obj() }.take(3)
        for ((k, p) in lists.withIndex()) {
            val c = Ui.vertical(a)
            val box = FrameLayout(a)
            val img = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; rounded(this, 12f); background = Ui.round(Ui.SURFACE2, 12f) }
            RemoteImages.load(img, url(p.str("image")))
            box.addView(img, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
            val play = FrameLayout(a)
            play.background = Ui.round(SPOTIFY, 15f)
            play.addView(Icon(a, Icon.Kind.PLAY, Ui.BG), FrameLayout.LayoutParams(Ui.u(13), Ui.u(13), Gravity.CENTER))
            box.addView(play, FrameLayout.LayoutParams(Ui.u(30), Ui.u(30), Gravity.BOTTOM or Gravity.END).apply { rightMargin = Ui.u(6); bottomMargin = Ui.u(6) })
            c.addView(box, LinearLayout.LayoutParams(Ui.u(104), Ui.u(104)))
            c.addView(Ui.ellipsize(Ui.text(a, p.str("nom").orEmpty(), 14f)), Ui.lp(Ui.u(104), Ui.WRAP, t = 6))
            val id = p.str("id").orEmpty()
            c.isClickable = true
            c.setOnClickListener { a.toast(tr("Lecture de « %s »", p.str("nom").orEmpty()), Ui.OK); act("musique:jouer:$id") }
            grid.addView(c, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { if (k < 2) rightMargin = Ui.u(10) })
        }
        card.addView(grid, Ui.lp(t = 12))
        card.addView(View(a), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        val now = m["enCours"].obj()
        if (now.isNotEmpty()) {
            val r = Ui.horizontal(a)
            r.background = Ui.round(Ui.SURFACE2, 14f)
            r.setPadding(Ui.u(8), Ui.u(8), Ui.u(8), Ui.u(8))
            val img = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; rounded(this, 8f) }
            RemoteImages.load(img, url(now.str("image")))
            r.addView(img, LinearLayout.LayoutParams(Ui.u(48), Ui.u(48)).apply { rightMargin = Ui.u(12) })
            val col = Ui.vertical(a)
            col.addView(Ui.ellipsize(Ui.text(a, now.str("titre").orEmpty(), 15f)))
            col.addView(Ui.ellipsize(Ui.text(a, now.str("artiste").orEmpty(), 13f, Ui.TEXT2)))
            col.addView(ProgressLine(a).apply { color = SPOTIFY; progress = (now["progression"] as? Number)?.toFloat() ?: 0f },
                Ui.lp(Ui.MATCH, Ui.u(3), t = 6))
            r.addView(col, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            val playing = now.bool("lecture") ?: false
            r.addView(round(if (playing) Icon.Kind.PAUSE else Icon.Kind.PLAY) { act(if (playing) "musique:pause" else "musique:reprendre") },
                LinearLayout.LayoutParams(Ui.u(44), Ui.u(44)).apply { leftMargin = Ui.u(10) })
            r.addView(round(Icon.Kind.NEXT) { act("musique:suivant") }, LinearLayout.LayoutParams(Ui.u(44), Ui.u(44)).apply { leftMargin = Ui.u(10) })
            card.addView(r)
        }
        m.str("appareil")?.takeIf { it.isNotEmpty() }?.let { card.addView(Ui.ellipsize(Ui.text(a, it, 13f, Ui.TEXT2)), Ui.lp(t = 8)) }
        return card
    }

    private fun round(kind: Icon.Kind, onClick: () -> Unit): View {
        val b = FrameLayout(a)
        b.background = Ui.pressable(Ui.round(0x10FFFFFF, 22f), 22f)
        b.addView(Icon(a, kind, Ui.TEXT), FrameLayout.LayoutParams(Ui.u(22), Ui.u(22), Gravity.CENTER))
        b.isClickable = true
        b.setOnClickListener { onClick() }
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

    private fun label(s: String) = Ui.text(a, s.uppercase(), 13f, Ui.TEXT2, Ui.MEDIUM).apply { letterSpacing = 0.09f }

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
