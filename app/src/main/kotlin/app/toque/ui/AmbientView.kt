package app.toque.ui

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.json.bool
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Écran d'ambiance, en veille quand quelqu'un est dans la cuisine : l'heure, puis ce que le serveur
 * maison envoie (en-tête météo, cartes, alertes, actions). L'appli ne connaît aucun capteur de la
 * maison : elle affiche des cartes génériques. Toucher une action la lance ; tourner ou appuyer sur
 * le bouton rotatif ramène à l'accueil (géré par [Standby]).
 */
class AmbientView(private val a: MainActivity) : FrameLayout(a) {
    private val main = Handler(Looper.getMainLooper())
    private val clock = Ui.text(a, "", 120f, Ui.TEXT, Ui.THIN)
    private val date = Ui.text(a, "", 22f, Ui.TEXT2, Ui.LIGHT)
    private val big = Ui.text(a, "", 64f, Ui.TEXT, Ui.THIN)
    private val sky = Ui.text(a, "", 22f, Ui.TEXT, Ui.LIGHT)
    private val detail = Ui.text(a, "", 18f, Ui.TEXT2)
    private val right = Ui.vertical(a)
    private val actions = Ui.horizontal(a)
    private var alive = false

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
            main.postDelayed(this, 60_000)
        }
    }

    init {
        setBackgroundColor(Ui.BG)
        isClickable = true
        val row = Ui.horizontal(a, Gravity.TOP)
        row.setPadding(Ui.u(36), Ui.u(26), Ui.u(32), Ui.u(18))
        val left = Ui.vertical(a)
        left.addView(clock)
        left.addView(date, Ui.lp(t = 0))
        left.addView(View(a), LinearLayout.LayoutParams(1, 0, 1f))
        left.addView(big)
        left.addView(sky, Ui.lp(t = 2))
        left.addView(detail, Ui.lp(t = 4))
        row.addView(left, LinearLayout.LayoutParams(0, Ui.MATCH, 0.4f).apply { rightMargin = Ui.u(28) })
        val col = Ui.vertical(a)
        col.addView(Ui.scroll(a, right), LinearLayout.LayoutParams(Ui.MATCH, 0, 1f))
        col.addView(actions, Ui.lp(t = 12))
        col.addView(Ui.text(a, tr("Tournez le bouton pour cuisiner"), 15f, Ui.TEXT3).apply { gravity = Gravity.END },
            Ui.lp(Ui.MATCH, Ui.WRAP, t = 10))
        row.addView(col, LinearLayout.LayoutParams(0, Ui.MATCH, 0.6f))
        addView(row, Ui.flp())
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
            main.post { if (alive && d != null) show(d) }
        }.start()
    }

    private fun show(d: Map<String, Any?>) {
        val head = d["entete"].obj()
        big.text = head.str("grand").orEmpty()
        sky.text = head.str("texte").orEmpty()
        detail.text = head.str("detail").orEmpty()

        right.removeAllViews()
        for (al in d["alertes"].list().mapNotNull { it as? String }) {
            val chip = Ui.chip(a, al, Icon.Kind.WARNING, Ui.WARN, 0x33FFC14D, 17f)
            right.addView(chip, Ui.lp(Ui.WRAP, Ui.WRAP, b = 8))
        }
        val cards = d["cartes"].list().map { it.obj() }
        var line: LinearLayout? = null
        for ((k, c) in cards.withIndex()) {
            if (k % 2 == 0) {
                line = Ui.horizontal(a, Gravity.TOP)
                right.addView(line, Ui.lp(b = 12))
            }
            line!!.addView(card(c), LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { if (k % 2 == 0) rightMargin = Ui.u(12) })
        }
        if (cards.size % 2 == 1) line?.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f))

        actions.removeAllViews()
        for (act in d["actions"].list().map { it.obj() }) {
            val id = act.str("id") ?: continue
            val on = act.bool("actif") ?: false
            val kind = try { Icon.Kind.valueOf(act.str("icone").orEmpty()) } catch (_: Exception) { Icon.Kind.BULB }
            val b = Ui.button(a, act.str("label").orEmpty(), if (on) Ui.Style.PRIMARY else Ui.Style.SECONDARY, kind, height = 56, textSize = 17f) {
                Thread {
                    val ok = a.app.home.ambientAction(id)
                    main.post { if (!ok) a.toast(tr("Action impossible : serveur maison injoignable"), Ui.WARN); load() }
                }.start()
            }
            b.setPadding(Ui.u(16), 0, Ui.u(18), 0)
            actions.addView(b, Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
        }
    }

    private fun card(c: Map<String, Any?>): View {
        val alert = c.bool("alerte") ?: false
        val v = Ui.vertical(a)
        v.background = Ui.round(if (alert) 0x33FFC14D else Ui.SURFACE, 20f)
        v.setPadding(Ui.u(16), Ui.u(12), Ui.u(16), Ui.u(12))
        v.addView(Ui.text(a, c.str("titre").orEmpty().uppercase(), 14f, if (alert) Ui.WARN else Ui.TEXT2, Ui.MEDIUM).apply { letterSpacing = 0.08f })
        for (l in c["lignes"].list().mapNotNull { it as? String }) {
            v.addView(Ui.ellipsize(Ui.text(a, l, 17f, Ui.TEXT)), Ui.lp(t = 4))
        }
        return v
    }
}
