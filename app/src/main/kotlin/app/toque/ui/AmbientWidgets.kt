package app.toque.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Icône météo dessinée (grille 48×48), d'après les conditions de Home Assistant :
 * sunny, clear-night, partlycloudy, cloudy, rainy, pouring, snowy, snowy-rainy, hail, lightning,
 * lightning-rainy, fog, windy, windy-variant, exceptional.
 */
class WeatherIcon(context: Context) : View(context) {
    var condition: String = ""
        set(v) { field = v; invalidate() }
    var night: Boolean = false
        set(v) { field = v; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val path = Path()
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height) / 48f
        if (s <= 0f) return
        canvas.save()
        canvas.translate((width - 48 * s) / 2f, (height - 48 * s) / 2f)
        canvas.scale(s, s)
        val c = condition
        when {
            c == "sunny" && !night -> sun(canvas, 24f, 24f, 9f, true)
            c == "sunny" || c == "clear-night" -> moon(canvas, 24f, 24f, 12f)
            c == "partlycloudy" -> {
                if (night) moon(canvas, 18f, 16f, 9f) else sun(canvas, 17f, 16f, 7f, true)
                cloud(canvas, 26f, 31f, 0.85f, CLOUD)
            }
            c == "cloudy" -> { cloud(canvas, 30f, 20f, 0.6f, CLOUD_DARK); cloud(canvas, 22f, 28f, 0.9f, CLOUD) }
            c == "fog" -> {
                cloud(canvas, 24f, 20f, 0.8f, CLOUD)
                line.color = CLOUD; line.strokeWidth = 3f
                canvas.drawLine(10f, 34f, 38f, 34f, line); canvas.drawLine(14f, 40f, 34f, 40f, line)
            }
            c == "windy" || c == "windy-variant" -> wind(canvas)
            c == "rainy" || c == "pouring" || c == "snowy" || c == "snowy-rainy" || c == "hail" ||
                c == "lightning" || c == "lightning-rainy" -> {
                cloud(canvas, 24f, 21f, 0.85f, CLOUD)
                when (c) {
                    "rainy" -> drops(canvas, floatArrayOf(17f, 25f, 33f), false)
                    "pouring" -> drops(canvas, floatArrayOf(14f, 20f, 26f, 32f, 38f), true)
                    "snowy" -> flakes(canvas, floatArrayOf(16f, 24f, 32f))
                    "snowy-rainy" -> { drops(canvas, floatArrayOf(18f, 32f), false); flakes(canvas, floatArrayOf(25f)) }
                    "hail" -> { fill.color = 0xFFE6EDF7.toInt(); for (x in floatArrayOf(17f, 25f, 33f)) canvas.drawCircle(x, 38f, 2.4f, fill) }
                    "lightning" -> bolt(canvas)
                    else -> { bolt(canvas); drops(canvas, floatArrayOf(15f, 35f), false) }
                }
            }
            else -> cloud(canvas, 24f, 26f, 0.9f, CLOUD)
        }
        canvas.restore()
    }

    private fun sun(canvas: Canvas, cx: Float, cy: Float, r: Float, rays: Boolean) {
        fill.color = SUN
        canvas.drawCircle(cx, cy, r, fill)
        if (!rays) return
        line.color = SUN; line.strokeWidth = r / 3.2f
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val r1 = r * 1.45f
            val r2 = r * 1.95f
            canvas.drawLine(cx + (r1 * Math.cos(a)).toFloat(), cy + (r1 * Math.sin(a)).toFloat(),
                cx + (r2 * Math.cos(a)).toFloat(), cy + (r2 * Math.sin(a)).toFloat(), line)
        }
    }

    private fun moon(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        path.reset()
        path.addCircle(cx, cy, r, Path.Direction.CW)
        val cut = Path().apply { addCircle(cx + r * 0.75f, cy - r * 0.45f, r * 0.85f, Path.Direction.CW) }
        path.op(cut, Path.Op.DIFFERENCE)
        fill.color = MOON
        canvas.drawPath(path, fill)
    }

    /** Nuage centré sur (cx, cy) ; k = taille (1 ≈ 34 de large). */
    private fun cloud(canvas: Canvas, cx: Float, cy: Float, k: Float, color: Int) {
        fill.color = color
        canvas.drawCircle(cx - 7f * k, cy + 1f * k, 7f * k, fill)
        canvas.drawCircle(cx + 1.5f * k, cy - 3.5f * k, 9.5f * k, fill)
        canvas.drawCircle(cx + 10f * k, cy + 2f * k, 6.5f * k, fill)
        rect.set(cx - 14f * k, cy + 1f * k, cx + 16.5f * k, cy + 8.5f * k)
        canvas.drawRoundRect(rect, 3.75f * k, 3.75f * k, fill)
    }

    private fun drops(canvas: Canvas, xs: FloatArray, heavy: Boolean) {
        line.color = RAIN; line.strokeWidth = 3f
        for (x in xs) canvas.drawLine(x, 34f, x - 2.5f, if (heavy) 44f else 41f, line)
    }

    private fun flakes(canvas: Canvas, xs: FloatArray) {
        line.color = 0xFFE6EDF7.toInt(); line.strokeWidth = 2f
        for (x in xs) {
            canvas.drawLine(x - 3f, 38f, x + 3f, 38f, line)
            canvas.drawLine(x - 1.5f, 35.4f, x + 1.5f, 40.6f, line)
            canvas.drawLine(x + 1.5f, 35.4f, x - 1.5f, 40.6f, line)
        }
    }

    private fun bolt(canvas: Canvas) {
        path.reset()
        path.moveTo(26f, 30f); path.lineTo(19f, 39f); path.lineTo(24f, 39f); path.lineTo(21f, 46f); path.lineTo(30f, 35f)
        path.lineTo(25f, 35f); path.close()
        fill.color = SUN
        canvas.drawPath(path, fill)
    }

    private fun wind(canvas: Canvas) {
        line.color = CLOUD; line.strokeWidth = 3f
        path.reset()
        path.moveTo(6f, 18f); path.lineTo(30f, 18f); path.cubicTo(36f, 18f, 36f, 10f, 31f, 10f)
        path.moveTo(6f, 26f); path.lineTo(38f, 26f); path.cubicTo(44f, 26f, 44f, 18f, 39f, 18f)
        path.moveTo(10f, 34f); path.lineTo(28f, 34f); path.cubicTo(34f, 34f, 34f, 42f, 29f, 42f)
        canvas.drawPath(path, line)
    }

    companion object {
        const val SUN = 0xFFFFC14D.toInt()
        const val MOON = 0xFFB9C4F0.toInt()
        const val CLOUD = 0xFFC7D0DE.toInt()
        const val CLOUD_DARK = 0xFF8592A8.toInt()
        const val RAIN = 0xFF5AA9FF.toInt()
    }
}

/** Jauge en arc (240°) : la valeur au centre, la couleur suit la température. */
class Gauge(context: Context) : View(context) {
    var min = 16f
    var max = 26f
    var value = 20f
    var label = ""
    var unit = "°"

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0x14FFFFFF }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Ui.SURFACE }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT; textAlign = Paint.Align.CENTER; typeface = Ui.REGULAR }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.TEXT2; typeface = Ui.REGULAR }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val sw = w * 0.07f
        val r = w / 2f - sw * 1.3f
        val cx = w / 2f
        val cy = r + sw * 1.3f
        rect.set(cx - r, cy - r, cx + r, cy + r)
        track.strokeWidth = sw
        canvas.drawArc(rect, 150f, 240f, false, track)
        val f = ((value - min) / (max - min)).coerceIn(0f, 1f)
        val col = color(value)
        arc.color = col
        arc.strokeWidth = sw
        if (f > 0.01f) canvas.drawArc(rect, 150f, 240f * f, false, arc)
        val a = Math.toRadians(150.0 + 240.0 * f)
        val dx = cx + (r * Math.cos(a)).toFloat()
        val dy = cy + (r * Math.sin(a)).toFloat()
        dot.color = col
        ring.strokeWidth = sw * 0.6f
        canvas.drawCircle(dx, dy, sw * 0.95f, dot)
        canvas.drawCircle(dx, dy, sw * 0.95f + ring.strokeWidth / 2f, ring)
        big.textSize = w * 0.225f
        val tw = big.measureText(label)
        small.textSize = w * 0.14f
        val base = cy + big.textSize * 0.36f
        canvas.drawText(label, cx, base, big)
        canvas.drawText(unit, cx + tw / 2f + w * 0.01f, base - big.textSize * 0.45f, small)
    }

    companion object {
        private val STOPS = floatArrayOf(17f, 20f, 22.5f, 25f)
        private val COLORS = intArrayOf(0xFF5AA9FF.toInt(), 0xFF78D6AA.toInt(), 0xFFFFC14D.toInt(), 0xFFFF8A3D.toInt())

        /** Bleu (froid) → vert (confort) → jaune → orange (chaud). */
        fun color(t: Float): Int {
            if (t <= STOPS[0]) return COLORS[0]
            for (i in 1 until STOPS.size) {
                if (t <= STOPS[i]) {
                    val k = (t - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1])
                    return mix(COLORS[i - 1], COLORS[i], k)
                }
            }
            return COLORS.last()
        }

        private fun mix(a: Int, b: Int, k: Float): Int {
            fun ch(sh: Int) = (((a shr sh) and 0xFF) + (((b shr sh) and 0xFF) - ((a shr sh) and 0xFF)) * k).toInt() and 0xFF
            return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}

/** Images servies par le serveur maison (pochettes) : petit cache en mémoire. */
object RemoteImages {
    private val cache = LruCache<String, Bitmap>(24)
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "toque-pochettes").apply { priority = Thread.MIN_PRIORITY } }
    private val main = Handler(Looper.getMainLooper())

    fun load(view: ImageView, url: String?) {
        view.tag = url
        if (url == null) { view.setImageDrawable(null); return }
        cache.get(url)?.let { view.setImageBitmap(it); return }
        exec.execute {
            val bmp = try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 5_000
                c.readTimeout = 10_000
                try {
                    if (c.responseCode == 200) c.inputStream.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }) } else null
                } finally {
                    c.disconnect()
                }
            } catch (_: Throwable) {
                null
            } ?: return@execute
            cache.put(url, bmp)
            main.post { if (view.tag == url) view.setImageBitmap(bmp) }
        }
    }
}
