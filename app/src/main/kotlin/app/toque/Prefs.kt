package app.toque

import android.content.Context
import android.content.SharedPreferences
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.i18n.trKey

/**
 * Réglages persistants, lisibles par les deux processus (interface et machine).
 * Écriture synchrone (commit) : le processus machine relit le fichier juste après.
 */
class Prefs(context: Context) {
    @Suppress("DEPRECATION")
    private val p: SharedPreferences = context.getSharedPreferences("toque", Context.MODE_MULTI_PROCESS)

    enum class Mode(val key: String, private val fr: String) {
        DEMO("demo", trKey("Démo (base simulée)")),
        TWIN("twin", trKey("Jumeau numérique (PC)")),
        HARDWARE("hardware", trKey("Machine réelle"));

        val label: String get() = tr(fr)

        companion object {
            fun of(k: String?) = values().firstOrNull { it.key == k } ?: DEMO
        }
    }

    var mode: Mode
        get() = Mode.of(p.getString("mode", null))
        set(v) {
            p.edit().putString("mode", v.key).commit()
        }

    /** Langue de l'interface : « auto » (celle du système), « fr » ou « en ». */
    var language: String
        get() = p.getString("language", I18n.AUTO) ?: I18n.AUTO
        set(v) {
            p.edit().putString("language", v).commit()
        }

    var twinHost: String
        get() = p.getString("twinHost", "127.0.0.1") ?: "127.0.0.1"
        set(v) {
            p.edit().putString("twinHost", v).commit()
        }

    var twinPort: Int
        get() = p.getInt("twinPort", 8765)
        set(v) {
            p.edit().putInt("twinPort", v).commit()
        }

    var serialDevice: String
        get() = p.getString("serialDevice", "/dev/ttyMT0") ?: "/dev/ttyMT0"
        set(v) {
            p.edit().putString("serialDevice", v).commit()
        }

    var vendorPackage: String
        get() = p.getString("vendorPackage", "de.silpion.mc2") ?: "de.silpion.mc2"
        set(v) {
            p.edit().putString("vendorPackage", v).commit()
        }

    var requireBowl: Boolean
        get() = p.getBoolean("requireBowl", false)
        set(v) {
            p.edit().putBoolean("requireBowl", v).commit()
        }

    /** Capteur du bol lu à l'envers (bases qui signalent « bol » quand il est retiré). */
    var invertBowl: Boolean
        get() = p.getBoolean("invertBowl", false)
        set(v) {
            p.edit().putBoolean("invertBowl", v).commit()
        }

    /** Faire confirmer chaque réglage machine importé d'une recette avant de pouvoir le lancer. */
    var reviewImported: Boolean
        get() = p.getBoolean("reviewImported", true)
        set(v) {
            p.edit().putBoolean("reviewImported", v).commit()
        }

    /** Montée progressive de la vitesse quand le contenu est chaud (≥ 60 °C). */
    var hotRamp: Boolean
        get() = p.getBoolean("hotRamp", true)
        set(v) {
            p.edit().putBoolean("hotRamp", v).commit()
        }

    /** Recettes : enchaîner sans appui les étapes machine qui se suivent sans geste à faire. */
    var chainSteps: Boolean
        get() = p.getBoolean("chainSteps", true)
        set(v) {
            p.edit().putBoolean("chainSteps", v).commit()
        }

    var sound: Boolean
        get() = p.getBoolean("sound", true)
        set(v) {
            p.edit().putBoolean("sound", v).commit()
        }

    /** Mise en veille de l'écran après ce nombre de minutes sans geste (0 : jamais). */
    var standbyMinutes: Int
        get() = p.getInt("standbyMinutes", 5)
        set(v) {
            p.edit().putInt("standbyMinutes", v).commit()
        }

    /**
     * Molette pendant la veille : « normal » (inchangée), « dim » (veilleuse blanche faible) ou
     * « off » (éteinte, par défaut). Reprend l'ancien interrupteur « standbyKnobLight » s'il a été réglé.
     */
    var standbyKnob: String
        get() = p.getString("standbyKnob", null)
            ?: if (!p.contains("standbyKnobLight")) "off" else if (p.getBoolean("standbyKnobLight", true)) "dim" else "normal"
        set(v) {
            p.edit().putString("standbyKnob", v).commit()
        }

    /** Fuseau horaire : « auto » (d'après la connexion Internet) ou un identifiant IANA choisi à la main. */
    var timeZone: String
        get() = p.getString("timeZone", "auto") ?: "auto"
        set(v) {
            p.edit().putString("timeZone", v).commit()
        }

    /** Dernier fuseau appliqué avec succès (affiché dans les réglages). */
    var lastZone: String
        get() = p.getString("lastZone", "") ?: ""
        set(v) {
            p.edit().putString("lastZone", v).commit()
        }

    var autoSync: Boolean
        get() = p.getBoolean("autoSync", true)
        set(v) {
            p.edit().putBoolean("autoSync", v).commit()
        }

    /** Home Assistant : adresse (« 192.168.1.20 » ou « http://…:8123 ») et identifiant du webhook des courses. */
    var haUrl: String
        get() = p.getString("haUrl", "") ?: ""
        set(v) {
            p.edit().putString("haUrl", v).commit()
        }

    var haWebhook: String
        get() = p.getString("haWebhook", "toque_courses") ?: "toque_courses"
        set(v) {
            p.edit().putString("haWebhook", v).commit()
        }

    /** Serveur maison (add-on Home Assistant « Toque ») : « 192.168.1.20 » ou « http://…:8793 » ; vide : recettes Lidl. */
    var homeServer: String
        get() = p.getString("homeServer", "") ?: ""
        set(v) {
            p.edit().putString("homeServer", v).commit()
        }

    /** Favoris du robot déjà versés au serveur maison (une seule fois, à la première connexion). */
    var homeMerged: Boolean
        get() = p.getBoolean("homeMerged", false)
        set(v) {
            p.edit().putBoolean("homeMerged", v).commit()
        }

    /** Version du catalogue du serveur maison déjà téléchargée. */
    var homeCatalog: String
        get() = p.getString("homeCatalog", "") ?: ""
        set(v) {
            p.edit().putString("homeCatalog", v).commit()
        }

    /** Une seule recette par groupe de doublons (même titre, mêmes ingrédients) dans les listes. */
    var hideDuplicates: Boolean
        get() = p.getBoolean("hideDuplicates", true)
        set(v) {
            p.edit().putBoolean("hideDuplicates", v).commit()
        }

    var lastSyncMs: Long
        get() = p.getLong("lastSyncMs", 0)
        set(v) {
            p.edit().putLong("lastSyncMs", v).commit()
        }

    var lastSyncResult: String
        get() = p.getString("lastSyncResult", "") ?: ""
        set(v) {
            p.edit().putString("lastSyncResult", v).commit()
        }

    var vendorSession: Boolean
        get() = p.getBoolean("vendorSession", false)
        set(v) {
            p.edit().putBoolean("vendorSession", v).commit()
        }

    /** Bouton « Retour à Toque » à afficher : Toque a ouvert une autre appli (robot sans bouton Accueil). */
    var returnBubble: Boolean
        get() = p.getBoolean("returnBubble", false)
        set(v) {
            p.edit().putBoolean("returnBubble", v).commit()
        }

    /** Ouvrir Toque au démarrage du robot (désactivé par « Revenir à l'appli d'origine »). */
    var bootLaunch: Boolean
        get() = p.getBoolean("bootLaunch", true)
        set(v) {
            p.edit().putBoolean("bootLaunch", v).commit()
        }

    var knobLeft: Int
        get() = p.getInt("knobLeft", -1)
        set(v) {
            p.edit().putInt("knobLeft", v).commit()
        }

    var knobRight: Int
        get() = p.getInt("knobRight", -1)
        set(v) {
            p.edit().putInt("knobRight", v).commit()
        }

    var knobPress: Int
        get() = p.getInt("knobPress", -1)
        set(v) {
            p.edit().putInt("knobPress", v).commit()
        }

    /** Vrai une fois le bouton rotatif réglé, vu en marche, ou déclaré absent de l'appareil. */
    var knobSetupDone: Boolean
        get() = p.getBoolean("knobSetupDone", false)
        set(v) {
            p.edit().putBoolean("knobSetupDone", v).commit()
        }

    /** Le réglage guidé n'est proposé d'office qu'une fois ; ensuite, l'accueil le rappelle. */
    var knobSetupShown: Boolean
        get() = p.getBoolean("knobSetupShown", false)
        set(v) {
            p.edit().putBoolean("knobSetupShown", v).commit()
        }

    /** Faux tant que rien ne dit que le bouton rotatif fonctionne : l'accueil affiche alors un rappel. */
    val knobReady: Boolean get() = knobSetupDone || knobLeft >= 0 || knobRight >= 0 || knobPress >= 0

    var lastCrashMs: Long
        get() = p.getLong("lastCrashMs", 0)
        set(v) {
            p.edit().putLong("lastCrashMs", v).commit()
        }

    var welcomeDone: Boolean
        get() = p.getBoolean("welcomeDone", false)
        set(v) {
            p.edit().putBoolean("welcomeDone", v).commit()
        }

    var builtinVersion: Int
        get() = p.getInt("builtinVersion", 0)
        set(v) {
            p.edit().putInt("builtinVersion", v).commit()
        }

    /** Recette guidée en cours (id + étape), pour reprendre après un redémarrage. */
    var guidedRecipe: String
        get() = p.getString("guidedRecipe", "") ?: ""
        set(v) {
            p.edit().putString("guidedRecipe", v).commit()
        }

    var guidedStep: Int
        get() = p.getInt("guidedStep", 0)
        set(v) {
            p.edit().putInt("guidedStep", v).commit()
        }

    /** Prorata de la balance de la recette en cours (« id\nadaptations ») : pour cette fois, vide sinon. */
    var guidedScale: String
        get() = p.getString("guidedScale", "") ?: ""
        set(v) {
            p.edit().putString("guidedScale", v).commit()
        }

    /** Réglages importés déjà vérifiés (« J'ai vérifié ce réglage »), clé = recette, étape et réglage. */
    var reviewedSteps: Set<String>
        get() = HashSet(p.getStringSet("reviewedSteps", emptySet()) ?: emptySet())
        set(v) {
            p.edit().putStringSet("reviewedSteps", HashSet(v)).commit()
        }

    /** Son joué quand la quantité à peser est atteinte (voir Sounds.WEIGH_SOUNDS). */
    var weighSound: String
        get() = p.getString("weighSound", "ding") ?: "ding"
        set(v) {
            p.edit().putString("weighSound", v).commit()
        }
}
