package app.toque.ui

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.core.i18n.tr
import app.toque.core.machine.Snapshot
import kotlin.random.Random

/**
 * Mise en veille, comme l'appli d'origine : après quelques minutes sans geste et sans programme
 * en cours, l'écran passe au noir avec l'heure, rétroéclairage au minimum. Le premier geste
 * (écran ou bouton rotatif) réveille sans rien déclencher ; le couvercle, la balance et les
 * évènements de la base réveillent aussi.
 */
class Standby(private val a: MainActivity, private val root: FrameLayout) {
    private val main = Handler(Looper.getMainLooper())
    private var lastInputMs = SystemClock.uptimeMillis()
    private var lastWeight = Int.MIN_VALUE
    private var lastLid: Boolean? = null
    private var view: FrameLayout? = null
    private var box: LinearLayout? = null
    private var clock: TextView? = null
    private var hot: TextView? = null
    private var running = false
    private var wokeMs = 0L
    /** Écran d'ambiance (présence dans la cuisine, demandé par le serveur maison), par-dessus la veille. */
    private var ambient: AmbientView? = null
    private var ambientWanted = false
    /** Plus personne : retour au noir après 2 min (on ne clignote pas à chaque pas hors du champ). */
    private val ambientOff = Runnable { hideAmbient() }
    private val ambientDim = Runnable { if (ambient != null) brightness(AMBIENT_BRIGHTNESS) }

    val asleep: Boolean get() = view != null

    private val check = object : Runnable {
        override fun run() {
            if (shouldSleep()) sleep()
            main.postDelayed(this, 10_000)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            move()
            main.postDelayed(this, 60_000)
        }
    }

    fun start() {
        if (running) return
        running = true
        lastInputMs = SystemClock.uptimeMillis()
        main.postDelayed(check, 10_000)
    }

    fun stop() {
        running = false
        main.removeCallbacks(check)
        wake()
    }

    /** Présence dans la cuisine : en veille, l'écran d'ambiance s'affiche (luminosité réduite). */
    fun setAmbient(on: Boolean) {
        ambientWanted = on
        if (on) {
            main.removeCallbacks(ambientOff)
            if (asleep) showAmbient()
        } else if (ambient != null) {
            main.removeCallbacks(ambientOff)
            main.postDelayed(ambientOff, 120_000)
        }
    }

    private fun showAmbient() {
        if (ambient != null) return
        val v = AmbientView(a)
        root.addView(v, Ui.flp())
        ambient = v
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(600).start()
        v.start()
        brightness(AMBIENT_BRIGHTNESS)
    }

    private fun hideAmbient() {
        main.removeCallbacks(ambientDim)
        val v = ambient ?: return
        v.stop()
        v.animate().cancel()
        root.removeView(v)
        ambient = null
        if (asleep) brightness(0.02f)
    }

    /** Un geste de la personne : repousse la veille. Vrai si ce geste a servi à réveiller (ne pas le transmettre). */
    fun onInput(): Boolean {
        lastInputMs = SystemClock.uptimeMillis()
        // Écran d'ambiance : le toucher sert à ses actions (et rallume l'écran à fond 30 s) ;
        // c'est le bouton rotatif qui ramène à l'accueil.
        if (ambient != null) {
            brightness(1f)
            main.removeCallbacks(ambientDim)
            main.postDelayed(ambientDim, 30_000)
            return false
        }
        if (!asleep) return false
        wake()
        wokeMs = SystemClock.uptimeMillis()
        return true
    }

    /** Bouton rotatif : un tour envoie plusieurs crans, on ignore aussi ceux qui suivent le réveil. */
    fun onKnob(): Boolean {
        if (ambient != null) {
            lastInputMs = SystemClock.uptimeMillis()
            wake()
            wokeMs = SystemClock.uptimeMillis()
            return true
        }
        if (onInput()) return true
        return SystemClock.uptimeMillis() - wokeMs < 800
    }

    /** Quitter l'écran d'ambiance (une recette touchée) : comme un tour de bouton. */
    fun leaveAmbient() {
        lastInputMs = SystemClock.uptimeMillis()
        wake()
        wokeMs = SystemClock.uptimeMillis()
    }

    /** Quelque chose se passe sur le robot : on réveille et on repousse la veille. */
    fun poke() {
        lastInputMs = SystemClock.uptimeMillis()
        wake()
    }

    fun onSnapshot(s: Snapshot) {
        val weightMoved = lastWeight != Int.MIN_VALUE && Math.abs(s.weightG - lastWeight) >= 20
        val lidMoved = lastLid != null && lastLid != s.lidOpen
        lastWeight = s.weightG
        lastLid = s.lidOpen
        if (s.busy || weightMoved || lidMoved) poke()
        else if (asleep) refresh()
    }

    private fun shouldSleep(): Boolean {
        val minutes = a.prefs.standbyMinutes
        if (minutes <= 0 || asleep || !running) return false
        if (a.snapshot.busy || a.current?.keepAwake == true) return false
        // Minuteur en cours : le compte à rebours reste à l'écran.
        if (a.timerRemaining > 0) return false
        return SystemClock.uptimeMillis() - lastInputMs >= minutes * 60_000L
    }

    private fun sleep() {
        a.hideKeyboard()
        val v = FrameLayout(a)
        v.setBackgroundColor(0xFF000000.toInt())
        v.isClickable = true
        val b = Ui.vertical(a, Gravity.CENTER_HORIZONTAL)
        val t = Ui.text(a, "", 150f, 0xFF7C828B.toInt(), Ui.THIN)
        b.addView(t, Ui.lp(Ui.WRAP, Ui.WRAP))
        val h = Ui.text(a, "", 22f, Ui.WARN, Ui.MEDIUM)
        b.addView(h, Ui.lp(Ui.WRAP, Ui.WRAP, t = 6))
        b.addView(Ui.text(a, tr("Touchez l'écran pour reprendre"), 17f, 0xFF3E434A.toInt()), Ui.lp(Ui.WRAP, Ui.WRAP, t = 18))
        v.addView(b, Ui.flp(Ui.WRAP, Ui.WRAP, Gravity.CENTER))
        root.addView(v, Ui.flp())
        view = v
        box = b
        clock = t
        hot = h
        refresh()
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(800).start()
        brightness(0.02f)
        when (a.prefs.standbyKnob) {
            "dim" -> KnobLight.start(KnobLight.NIGHT_LEVEL)
            "off" -> KnobLight.start(0)
        }
        main.postDelayed(tick, 60_000)
        if (ambientWanted) showAmbient()
    }

    private fun wake() {
        main.removeCallbacks(ambientOff)
        hideAmbient()
        val v = view ?: return
        main.removeCallbacks(tick)
        v.animate().cancel()
        root.removeView(v)
        view = null
        box = null
        clock = null
        hot = null
        brightness(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        KnobLight.stop()
    }

    private fun refresh() {
        clock?.text = MainActivity.now()
        val temp = a.snapshot.actualTempC
        hot?.let {
            it.text = if (temp >= 50) tr("Bol chaud : %d °C", temp) else ""
            it.visibility = if (temp >= 50) View.VISIBLE else View.GONE
        }
    }

    /** Déplace un peu l'heure chaque minute, pour ne pas marquer l'écran. */
    private fun move() {
        val v = view ?: return
        val b = box ?: return
        val dx = ((v.width - b.width) / 2 - Ui.u(40)).coerceAtLeast(0)
        val dy = ((v.height - b.height) / 2 - Ui.u(40)).coerceAtLeast(0)
        b.translationX = if (dx > 0) Random.nextInt(-dx, dx + 1).toFloat() else 0f
        b.translationY = if (dy > 0) Random.nextInt(-dy, dy + 1).toFloat() else 0f
    }

    private companion object {
        /** Luminosité de l'écran d'ambiance : lisible sans éblouir. */
        const val AMBIENT_BRIGHTNESS = 0.4f
    }

    private fun brightness(value: Float) {
        val lp = a.window.attributes
        lp.screenBrightness = value
        a.window.attributes = lp
    }
}
