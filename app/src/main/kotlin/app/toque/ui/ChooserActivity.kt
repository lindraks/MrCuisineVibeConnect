package app.toque.ui

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import app.toque.ToqueApp
import app.toque.core.i18n.tr

/**
 * Menu de démarrage : deux gros boutons, « Appli Lidl » (l'appli d'origine) et « Toque ». Le robot
 * n'a pas de bouton Accueil : on choisit au toucher, ou au bouton rotatif (tourner, puis appuyer).
 *
 * Tant que rien n'est choisi, le processus machine garde le port série libre ([app.toque.Prefs.chooserPending]) :
 * « Toque » le lui rend, « Appli Lidl » le laisse à l'appli d'origine.
 */
class ChooserActivity : Activity() {
    private lateinit var app: ToqueApp
    private val cards = ArrayList<LinearLayout>()
    private var selected = 1
    private var manual = false
    private var done = false
    private var lastKnobMs = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = ToqueApp.of(this)
        manual = intent.getBooleanExtra(EXTRA_MANUAL, false)
        // Relancé (alarme, système) alors que le choix est déjà fait : rien à montrer.
        if (!manual && !app.prefs.chooserPending) {
            finish()
            return
        }
        val dm = resources.displayMetrics
        Ui.init(dm.widthPixels, dm.heightPixels)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(ColorDrawable(Ui.BG))

        val root = FrameLayout(this)
        root.setBackgroundColor(Ui.BG)
        val column = Ui.vertical(this, Gravity.CENTER_HORIZONTAL)
        column.setPadding(Ui.u(40), 0, Ui.u(40), 0)
        column.addView(Ui.text(this, tr("Que voulez-vous utiliser ?"), 32f, Ui.TEXT, Ui.MEDIUM), Ui.lp(Ui.WRAP, Ui.WRAP))

        val row = Ui.horizontal(this, Gravity.CENTER)
        row.addView(card(Icon.Kind.LINK, tr("Appli Lidl"), tr("L'appli Monsieur Cuisine d'origine"), 0),
            LinearLayout.LayoutParams(0, Ui.u(250), 1f).apply { setMargins(Ui.u(14), 0, Ui.u(14), 0) })
        row.addView(card(Icon.Kind.POT, tr("Toque"), tr("Recettes, cuisson manuelle, programmes"), 1),
            LinearLayout.LayoutParams(0, Ui.u(250), 1f).apply { setMargins(Ui.u(14), 0, Ui.u(14), 0) })
        column.addView(row, Ui.lp(t = 36))

        column.addView(Ui.text(this, tr("Tournez le bouton pour choisir, appuyez pour valider."), 17f, Ui.TEXT3), Ui.lp(Ui.WRAP, Ui.WRAP, t = 28))
        root.addView(column, Ui.flp(Ui.MATCH, Ui.MATCH, Gravity.CENTER))
        setContentView(root)
        paint()
    }

    private fun card(icon: Icon.Kind, title: String, subtitle: String, index: Int): LinearLayout {
        val c = Ui.vertical(this, Gravity.CENTER_HORIZONTAL)
        c.gravity = Gravity.CENTER
        c.setPadding(Ui.u(24), Ui.u(24), Ui.u(24), Ui.u(24))
        c.addView(Icon(this, icon, Ui.ACCENT), LinearLayout.LayoutParams(Ui.u(56), Ui.u(56)))
        c.addView(Ui.text(this, title, 32f, Ui.TEXT, Ui.MEDIUM), Ui.lp(Ui.WRAP, Ui.WRAP, t = 16))
        c.addView(Ui.text(this, subtitle, 17f, Ui.TEXT2).apply { gravity = Gravity.CENTER }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 8))
        c.isClickable = true
        c.setOnClickListener { choose(index) }
        cards += c
        return c
    }

    /** La carte sélectionnée (au bouton rotatif) a un liseré orange. */
    private fun paint() {
        cards.forEachIndexed { i, c ->
            val on = i == selected
            c.background = Ui.pressable(Ui.round(Ui.SURFACE2, 28f, if (on) Ui.ACCENT else Ui.STROKE, if (on) 3f else 1.5f), 28f)
        }
    }

    private fun select(i: Int) {
        if (i == selected) return
        selected = i
        paint()
    }

    private fun choose(i: Int) {
        if (done) return
        done = true
        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i == 0) open.putExtra(MainActivity.EXTRA_OPEN_VENDOR, true)
        startActivity(open)
        finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val k = event.keyCode
            val p = app.prefs
            val left = p.knobLeft.let { if (it >= 0) it == k else k == KeyEvent.KEYCODE_DPAD_LEFT }
            val right = p.knobRight.let { if (it >= 0) it == k else k == KeyEvent.KEYCODE_DPAD_RIGHT }
            val press = p.knobPress.let { if (it >= 0) it == k else k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER }
            when {
                left -> { select(0); return true }
                right -> { select(1); return true }
                press -> { choose(selected); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Encodeur rotatif (AXIS_SCROLL) ou molette (AXIS_VSCROLL), comme dans l'écran principal. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL) {
            val v = event.getAxisValue(AXIS_SCROLL).let { if (it == 0f) -event.getAxisValue(MotionEvent.AXIS_VSCROLL) else it }
            val now = System.currentTimeMillis()
            if (v != 0f && now - lastKnobMs > 40) {
                lastKnobMs = now
                select(if (v > 0) 1 else 0)
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    /** Pas de retour possible depuis le menu de démarrage : on y choisit. (Ouvert depuis les Réglages : on le ferme.) */
    override fun onBackPressed() {
        if (manual) finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    companion object {
        /** Ouvert à la main (Réglages) plutôt qu'au démarrage : s'affiche même sans choix en attente. */
        const val EXTRA_MANUAL = "manual"

        /** MotionEvent.AXIS_SCROLL (API 26), valeur fixe pour Android 6. */
        private const val AXIS_SCROLL = 26
    }
}
