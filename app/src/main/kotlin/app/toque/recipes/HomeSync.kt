package app.toque.recipes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.toque.Logbook
import app.toque.Prefs
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.json.Json
import app.toque.core.json.int
import app.toque.core.json.obj
import app.toque.core.json.str
import app.toque.core.recipes.RecipeCodec
import app.toque.core.recipes.RecipeSource
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Catalogue du serveur maison (recettes Monsieur Cuisine et Cookomix converties, dédoublonnées et
 * classées) : remplace les recettes Lidl du robot. Page par page, puis les photos manquantes,
 * déjà réduites par le serveur. Favoris, notes et quantités corrigées sont gardés.
 */
class HomeSync(private val prefs: Prefs, private val repo: RecipeRepo, private val log: Logbook) {

    fun sync(progress: (String) -> Unit = {}): LidlSync.Report {
        if (!running.compareAndSet(false, true)) return LidlSync.Report(false, tr("Synchronisation déjà en cours"))
        try {
            return finish(run(progress))
        } catch (e: Exception) {
            log.crash("synchro maison", e)
            return finish(LidlSync.Report(false, tr("Synchronisation impossible : %s", e.message ?: e.toString())))
        } finally {
            running.set(false)
        }
    }

    private fun run(progress: (String) -> Unit): LidlSync.Report {
        progress(tr("Recettes du serveur maison…"))
        val keep = HashSet<String>()
        var page = 0
        var pages = 1
        var version = ""
        var bad = 0
        while (page < pages) {
            val text = try {
                HomeServer.call(prefs, "GET", "/api/robot/catalogue?page=$page&taille=$PAGE", timeoutMs = 20_000, maxBytes = 24 * 1024 * 1024)
            } catch (e: IOException) {
                return LidlSync.Report(false, tr("Serveur maison injoignable : %s", e.message ?: e.toString()))
            }
            val head = Json.parse(text).obj()
            pages = (head.int("pages") ?: 1).coerceIn(1, 1000)
            version = head.str("version").orEmpty()
            val (recipes, unreadable) = RecipeCodec.decodeFile(text, RecipeSource.IMPORTED)
            bad += unreadable
            keep += recipes.map { it.id }
            repo.upsertAll(recipes, rebuild = false)
            page++
            progress(tr("Recettes : %d / %d", minOf(keep.size, page * PAGE), keep.size + (pages - page) * PAGE))
        }
        if (keep.isEmpty()) return LidlSync.Report(false, tr("Aucune recette reçue du serveur"))
        // Le catalogue suit celui du serveur (recettes retirées supprimées, favoris reportés par titre).
        repo.removeAllExcept(keep, rebuild = false)
        repo.refresh()
        prefs.homeCatalog = version
        photos(keep, progress)
        purge()
        val n = repo.count
        val note = if (bad > 0) " " + tr("(%d illisibles)", bad) else ""
        return LidlSync.Report(true, tr("%d recettes du serveur maison", n) + note, n)
    }

    /**
     * Plus de recettes Lidl en double : photos qui ne servent plus à aucune recette (anciennes
     * recettes Lidl ou retirées du serveur) et index de l'ancienne synchro monsieur-cuisine.com.
     */
    private fun purge() {
        try {
            val used = repo.imageNames()
            var freed = 0L
            var count = 0
            for (f in repo.imagesDir.listFiles().orEmpty()) {
                val base = f.name.removePrefix("large-").removePrefix("small-")
                if (f.isFile && base !in used && !f.name.endsWith(".part")) {
                    freed += f.length()
                    if (f.delete()) count++
                }
            }
            File(repo.imagesDir.parentFile, "recettes-en-ligne.json").delete()
            if (count > 0) log.write("synchro maison", "purge : $count photos inutilisées (${freed / 1_048_576} Mo)")
        } catch (e: Exception) {
            log.write("synchro maison", "purge impossible : ${e.message}")
        }
    }

    private fun photos(ids: Collection<String>, progress: (String) -> Unit) {
        val missing = ArrayList<String>()
        for (id in ids) {
            val url = repo.get(id)?.image ?: continue
            if (url.startsWith("http") && repo.imageFile(url) == null) missing += url
        }
        var inARow = 0
        for ((i, url) in missing.withIndex()) {
            if (i % 10 == 0) progress(tr("Photos : %d / %d", i + 1, missing.size))
            try {
                savePhoto(url)
                inARow = 0
            } catch (e: IOException) {
                log.write("synchro maison", "photo $url : ${e.message}")
                if (++inARow >= 5) return
            }
        }
    }

    /** Photo de 800 px de large au plus (réduite par le serveur), sous le nom de fichier de l'URL. */
    private fun savePhoto(url: String) {
        val sized = if (url.contains('?')) url else "$url?w=$MAX_WIDTH"
        val bytes = download(sized)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) throw IOException(tr("image illisible"))
        val name = url.substringAfterLast('/').substringBefore('?')
        val tmp = File(repo.imagesDir, "$name.part")
        if (bounds.outWidth <= MAX_WIDTH) {
            FileOutputStream(tmp).use { it.write(bytes) }
        } else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2
            var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: throw IOException(tr("image illisible"))
            if (bmp.width > MAX_WIDTH) {
                val scaled = Bitmap.createScaledBitmap(bmp, MAX_WIDTH, bmp.height * MAX_WIDTH / bmp.width, true)
                if (scaled !== bmp) bmp.recycle()
                bmp = scaled
            }
            FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
        }
        if (!tmp.renameTo(File(repo.imagesDir, name))) tmp.delete()
    }

    private fun download(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            val code = c.responseCode
            if (code != 200) throw IOException(tr("réponse %d du serveur", code))
            c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > 8 * 1024 * 1024) throw IOException(tr("réponse trop grande"))
                }
                return out.toByteArray()
            }
        } finally {
            c.disconnect()
        }
    }

    private fun finish(r: LidlSync.Report): LidlSync.Report {
        val stamp = SimpleDateFormat(tr("dd/MM à HH:mm"), I18n.locale).format(Date())
        prefs.lastSyncMs = System.currentTimeMillis()
        prefs.lastSyncResult = "$stamp : ${r.message}"
        log.write("synchro maison", r.message)
        return r
    }

    companion object {
        private const val PAGE = 150
        private const val MAX_WIDTH = 800
        private val running = AtomicBoolean(false)
    }
}
