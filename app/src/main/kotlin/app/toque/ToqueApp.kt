package app.toque

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import app.toque.core.i18n.I18n
import app.toque.machine.MachineClient
import app.toque.recipes.HomeLink
import app.toque.recipes.HomeServer
import app.toque.recipes.HomeSync
import app.toque.recipes.RecipeRepo
import app.toque.ui.MainActivity
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

class ToqueApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var log: Logbook
        private set
    val machine: MachineClient by lazy { MachineClient(this) }
    val recipes: RecipeRepo by lazy { RecipeRepo(this) }
    /** Favoris et « À cuisiner » partagés avec le serveur maison (et le téléphone). */
    val home: HomeLink by lazy { HomeLink(prefs, recipes, log) }
    @Volatile private var lastAutoSyncMs = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        log = Logbook(this)
        LanguageFiles.install(this, log)
        I18n.select(I18n.resolve(prefs.language, Locale.getDefault().language).code)
        // Racines récentes absentes d'Android 6 (voir ExtraRoots), pour toutes les connexions HTTPS.
        try {
            HttpsURLConnection.setDefaultSSLSocketFactory(ExtraRoots.socketFactory(this))
        } catch (e: Exception) {
            log.write("tls", "racines supplémentaires non chargées : $e")
        }
        if (!isMainProcess()) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                log.crash("interface", error)
                val now = System.currentTimeMillis()
                val recent = now - prefs.lastCrashMs < 10_000
                prefs.lastCrashMs = now
                if (recent) throw error // évite une boucle de relances
                // Relance discrète de l'interface ; le processus machine, lui, continue.
                val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val pi = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_ONE_SHOT)
                (getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, System.currentTimeMillis() + 800, pi)
                Process.killProcess(Process.myPid())
                System.exit(10)
            } catch (_: Throwable) {
                previous?.uncaughtException(thread, error)
            }
        }
        log.write("interface", "démarrage")
        TimeSync.start(this, prefs, log)
        // Nouveau catalogue annoncé par le serveur maison : téléchargé en arrière-plan.
        // Après un échec, nouvel essai au plus tôt 10 min plus tard (pas de synchro en boucle).
        home.onNewCatalog = {
            val now = System.currentTimeMillis()
            if (now - lastAutoSyncMs > 10 * 60_000L) {
                lastAutoSyncMs = now
                Thread { HomeSync(prefs, recipes, log).sync() }.start()
            }
        }
        // Mise à jour demandée à distance (serveur maison) : installée seulement si rien ne tourne.
        home.onUpdateRequested = { at ->
            if (!machine.snapshot.busy) {
                prefs.lastUpdateAt = at
                Thread { remoteUpdate() }.start()
            }
        }
        home.start()
    }

    /** Dernière version publiée sur GitHub (signature vérifiée), installée sans écran ; résultat noté au serveur. */
    private fun remoteUpdate() {
        val result = try {
            HomeServer.call(prefs, "DELETE", "/api/robot/maj")
            val u = app.toque.update.Updater(this)
            val r = u.latest()
            if (!u.isNewer(r)) "déjà à jour (${u.installedVersion})"
            else {
                report(true, "mise à jour vers ${r.version}…")
                u.install(u.download(r) {})
                "installée : ${r.version}"
            }
        } catch (e: Exception) {
            log.crash("maj à distance", e)
            "échec : ${e.message ?: e}"
        }
        report(!result.startsWith("échec"), "mise à jour à distance — $result")
    }

    private fun report(ok: Boolean, message: String) {
        try {
            HomeServer.call(prefs, "POST", "/api/robot/rapport", app.toque.core.json.Json.write(mapOf("ok" to ok, "message" to message)))
        } catch (_: Exception) {
        }
    }

    private fun isMainProcess(): Boolean {
        val pid = Process.myPid()
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val name = am.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName ?: return true
        return name == packageName
    }

    companion object {
        fun of(c: Context): ToqueApp = c.applicationContext as ToqueApp
    }
}
