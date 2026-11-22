package app.toque.recipes

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import app.toque.Logbook
import app.toque.Prefs
import app.toque.core.i18n.tr
import app.toque.core.json.Json
import app.toque.core.json.int
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Serveur maison (add-on Home Assistant « Toque », sur le réseau local) : catalogue de recettes,
 * favoris et liste « À cuisiner » communs avec le téléphone, liste de courses.
 * Le robot ne fait que lire et écrire ces listes : rien ne démarre le moteur à distance.
 */
object HomeServer {
    const val DEFAULT_PORT = 8793

    /** « 192.168.1.20 » → « http://192.168.1.20:8793 » ; null si vide. */
    fun baseUrl(address: String): String? {
        var a = address.trim().trimEnd('/')
        if (a.isEmpty()) return null
        if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://$a"
        val host = a.substringAfter("://")
        if (!host.contains(':')) a = "$a:$DEFAULT_PORT"
        return a
    }

    fun configured(prefs: Prefs): Boolean = baseUrl(prefs.homeServer) != null

    /** Requête JSON ; renvoie le corps de la réponse. */
    fun call(prefs: Prefs, method: String, path: String, body: String? = null, timeoutMs: Int = 10_000,
             maxBytes: Int = 4 * 1024 * 1024): String {
        val base = baseUrl(prefs.homeServer) ?: throw IOException(tr("Serveur maison non réglé"))
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs * 3
            c.requestMethod = method
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw IOException(tr("réponse %d du serveur", code))
            c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw IOException(tr("réponse trop grande"))
                }
                return String(out.toByteArray(), Charsets.UTF_8)
            }
        } finally {
            c.disconnect()
        }
    }

    fun path(id: String): String = URLEncoder.encode(id, "UTF-8").replace("+", "%20")
}

/**
 * Favoris et « À cuisiner » partagés : relus sur le serveur maison toutes les minutes ; un
 * changement fait sur le robot part tout de suite (et est réessayé tant que le serveur ne l'a pas reçu).
 */
class HomeLink(private val prefs: Prefs, private val repo: RecipeRepo, private val log: Logbook) {
    /** Recette prévue : [servings] choisi sur le téléphone (0 : celui de la recette). */
    data class Planned(val id: String, val servings: Int, val addedMs: Long)

    @Volatile var planned: List<Planned> = emptyList()
        private set
    @Volatile var reachable = false
        private set
    /** Recette envoyée depuis le téléphone, pas encore ouverte ([at] : heure de l'envoi, ms). */
    data class Sent(val id: String, val servings: Int, val at: Long)
    @Volatile var sent: Sent? = null
        private set
    private val thread = HandlerThread("toque-maison").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<() -> Unit>()
    /** Changements faits sur le robot pas encore reçus par le serveur : id → présent ou non. */
    private val pendingFav = LinkedHashMap<String, Boolean>()
    private val pendingPlan = LinkedHashMap<String, Int?>()
    private val tick = object : Runnable {
        override fun run() {
            refresh()
            worker.postDelayed(this, PERIOD_MS)
        }
    }

    fun start() {
        worker.removeCallbacks(tick)
        worker.post(tick)
    }

    /** Relecture immédiate (après un changement de réglage, ou à l'affichage d'un écran). */
    fun refreshSoon() = worker.post { refresh() }

    fun onChange(l: () -> Unit): () -> Unit {
        synchronized(listeners) { listeners += l }
        return { synchronized(listeners) { listeners -= l } }
    }

    private fun notifyChanged() = main.post { for (l in synchronized(listeners) { ArrayList(listeners) }) l() }

    fun isPlanned(id: String): Boolean = planned.any { it.id == id }

    fun plannedServings(id: String): Int = planned.firstOrNull { it.id == id }?.servings ?: 0

    fun setFavorite(id: String, favorite: Boolean) {
        repo.setFavorite(id, favorite)
        if (!HomeServer.configured(prefs)) return
        worker.post {
            synchronized(pendingFav) { pendingFav[id] = favorite }
            flush()
        }
    }

    /** Ajoute ([servings] non nul) ou retire (null) une recette de « À cuisiner ». */
    fun setPlanned(id: String, servings: Int?) {
        val now = System.currentTimeMillis()
        planned = if (servings == null) planned.filter { it.id != id }
        else planned.filter { it.id != id } + Planned(id, servings, now)
        notifyChanged()
        worker.post {
            synchronized(pendingPlan) { pendingPlan[id] = servings }
            flush()
        }
    }

    /** La recette envoyée a été ouverte sur le robot : le serveur l'oublie. */
    fun ackSent(at: Long) {
        sent = null
        worker.post {
            try {
                HomeServer.call(prefs, "DELETE", "/api/envoyer?at=$at")
            } catch (e: Exception) {
                log.write("maison", "envoi non acquitté : ${e.message}")
            }
        }
    }

    /** Recette cuisinée jusqu'au bout : elle quitte « À cuisiner ». */
    fun cooked(id: String) {
        if (isPlanned(id)) setPlanned(id, null)
    }

    /** Liste de courses vers Home Assistant, par le serveur maison. */
    fun sendShopping(recipe: String, servings: Int, items: List<String>, done: (String?) -> Unit) {
        worker.post {
            val err = try {
                HomeServer.call(prefs, "POST", "/api/courses",
                    Json.write(linkedMapOf("recette" to recipe, "portions" to servings, "articles" to items)))
                null
            } catch (e: Exception) {
                tr("Serveur maison injoignable : %s", e.message ?: e.javaClass.simpleName)
            }
            main.post { done(err) }
        }
    }

    private fun flush(): Boolean {
        if (!HomeServer.configured(prefs)) return false
        try {
            for ((id, fav) in synchronized(pendingFav) { LinkedHashMap(pendingFav) }) {
                HomeServer.call(prefs, if (fav) "PUT" else "DELETE", "/api/favoris/" + HomeServer.path(id))
                synchronized(pendingFav) { if (pendingFav[id] == fav) pendingFav.remove(id) }
            }
            for ((id, servings) in synchronized(pendingPlan) { LinkedHashMap(pendingPlan) }) {
                if (servings == null) HomeServer.call(prefs, "DELETE", "/api/a-cuisiner/" + HomeServer.path(id))
                else HomeServer.call(prefs, "PUT", "/api/a-cuisiner/" + HomeServer.path(id), Json.write(mapOf("portions" to servings)))
                synchronized(pendingPlan) { if (pendingPlan[id] == servings) pendingPlan.remove(id) }
            }
            return true
        } catch (e: Exception) {
            log.write("maison", "envoi différé : ${e.message}")
            return false
        }
    }

    private fun refresh() {
        if (!HomeServer.configured(prefs)) {
            if (planned.isNotEmpty() || reachable) {
                planned = emptyList()
                reachable = false
                notifyChanged()
            }
            return
        }
        try {
            if (!flush()) throw IOException("envoi en attente")
            if (!prefs.homeMerged) {
                // Première connexion : les favoris du robot rejoignent ceux du foyer (rien n'est retiré).
                val mine = repo.index.entries.filter { it.favorite }.map { it.id }
                HomeServer.call(prefs, "POST", "/api/favoris", Json.write(mapOf("ids" to mine)))
                prefs.homeMerged = true
            }
            val state = Json.parse(HomeServer.call(prefs, "GET", "/api/etat")).obj()
            val favs = state["favoris"].list().mapNotNull { it as? String }.toHashSet()
            val plan = state["aCuisiner"].list().mapNotNull { e ->
                val m = e.obj()
                val id = m.str("id") ?: return@mapNotNull null
                Planned(id, m.int("portions") ?: 0, (m["ajout"] as? Number)?.toLong() ?: 0L)
            }
            // Un changement local pas encore parti l'emporte sur l'état relu.
            synchronized(pendingFav) { for ((id, f) in pendingFav) if (f) favs += id else favs -= id }
            repo.replaceFavorites(favs)
            val sentNow = state["envoi"]?.obj()?.let { e ->
                val id = e.str("id") ?: return@let null
                Sent(id, e.int("portions") ?: 0, (e["at"] as? Number)?.toLong() ?: 0L)
            }
            // Une recette envoyée en attente (cuisson en cours) est reproposée à chaque relecture.
            val changed = plan != planned || !reachable || sentNow != sent || sentNow != null
            sent = sentNow
            planned = plan
            reachable = true
            if (changed) notifyChanged()
            val maj = (state["maj"] as? Number)?.toLong() ?: 0L
            if (maj > prefs.lastUpdateAt) onUpdateRequested?.invoke(maj)
            val version = state.str("catalogue").orEmpty()
            if (version.isNotEmpty() && version != prefs.homeCatalog && prefs.autoSync) onNewCatalog?.invoke()
        } catch (e: Exception) {
            if (reachable) {
                reachable = false
                notifyChanged()
            }
            log.write("maison", "serveur injoignable : ${e.message}")
        }
    }

    /** Appelé (fil du lien) quand le serveur demande une mise à jour de Toque ([Long] : heure de la demande). */
    @Volatile var onUpdateRequested: ((Long) -> Unit)? = null

    /** Appelé (fil du lien) quand le serveur annonce un nouveau catalogue. */
    @Volatile var onNewCatalog: (() -> Unit)? = null

    companion object {
        /** Relecture : assez souvent pour qu'une recette envoyée du téléphone s'ouvre vite. */
        const val PERIOD_MS = 20_000L
    }
}
