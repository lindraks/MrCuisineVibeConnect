package app.toque.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * Icône d'une catégorie de recettes ([CategoryIcons], grille 24×24, au trait) : chemins (M, L, C, Q, Z
 * en coordonnées absolues) et cercles (« o cx cy r »), comme sur l'appli téléphone.
 */
class CategoryIcon(context: Context, name: String, color: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.8f
        this.color = color
    }
    private val paths = ArrayList<Path>()
    private val circles = ArrayList<FloatArray>()

    init {
        for (e in CategoryIcons.ALL[name] ?: CategoryIcons.ALL["Toutes"].orEmpty()) {
            if (e.startsWith("o ")) {
                val v = e.substring(2).trim().split(' ').mapNotNull { it.toFloatOrNull() }
                if (v.size == 3) circles += v.toFloatArray()
            } else {
                paths += parse(e)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) / 24f
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - 24 * s) / 2f, (height - 24 * s) / 2f)
        canvas.scale(s, s)
        for (p in paths) canvas.drawPath(p, paint)
        for (c in circles) canvas.drawCircle(c[0], c[1], c[2], paint)
        canvas.restore()
    }

    companion object {
        private val TOKEN = Regex("[MLCQZ]|-?\\d*\\.?\\d+")

        fun parse(d: String): Path {
            val p = Path()
            val t = TOKEN.findAll(d).map { it.value }.toList()
            var i = 0
            var cmd = 'M'
            fun n(): Float = t[i++].toFloat()
            while (i < t.size) {
                val tok = t[i]
                if (tok.length == 1 && tok[0].isLetter()) {
                    cmd = tok[0]
                    i++
                    if (cmd == 'Z') p.close()
                    continue
                }
                when (cmd) {
                    'M' -> { p.moveTo(n(), n()); cmd = 'L' }
                    'L' -> p.lineTo(n(), n())
                    'C' -> p.cubicTo(n(), n(), n(), n(), n(), n())
                    'Q' -> p.quadTo(n(), n(), n(), n())
                    else -> i++
                }
            }
            return p
        }
    }
}
