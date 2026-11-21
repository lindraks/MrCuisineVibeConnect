package app.toque

import android.os.Handler
import android.os.Looper
import app.toque.core.i18n.tr
import app.toque.core.recipes.ShoppingList
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Envoi vers Home Assistant par un webhook (POST JSON sur /api/webhook/<id>) : aucun jeton
 * n'est stocké sur le robot, et l'automatisation côté Home Assistant décide quoi en faire
 * (ajouter les articles à une liste de courses).
 */
object HomeAssistant {
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun configured(prefs: Prefs): Boolean = ShoppingList.webhookUrl(prefs.haUrl, prefs.haWebhook) != null

    /** Envoie [json] ; [done] reçoit null si Home Assistant l'a accepté, sinon le message d'erreur (fil principal). */
    fun send(prefs: Prefs, json: String, done: (String?) -> Unit) {
        val url = ShoppingList.webhookUrl(prefs.haUrl, prefs.haWebhook)
        if (url == null) {
            done(tr("Réglez d'abord l'adresse de Home Assistant dans Réglages."))
            return
        }
        exec.execute {
            val error = try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 6_000
                c.readTimeout = 8_000
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                c.disconnect()
                if (code in 200..299) null else tr("Home Assistant a répondu %d.", code)
            } catch (e: Exception) {
                tr("Home Assistant injoignable : %s", e.message ?: e.javaClass.simpleName)
            }
            main.post { done(error) }
        }
    }
}
