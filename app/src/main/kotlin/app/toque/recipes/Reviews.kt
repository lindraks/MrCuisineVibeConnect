package app.toque.recipes

import app.toque.Http
import app.toque.Prefs
import app.toque.core.i18n.I18n
import app.toque.core.recipes.McOnline

/**
 * Avis publics des recettes de monsieur-cuisine.com, lus à l'ouverture de la fiche (ils changent
 * souvent : pas de copie locale). Les pages déjà lues restent en mémoire pendant la session.
 */
object Reviews {
    private val cache = object : LinkedHashMap<String, McOnline.ReviewPage>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, McOnline.ReviewPage>?) = size > 40
    }

    /** Page [page] des avis ; lève une exception si le site est injoignable. */
    fun load(apiId: Long, page: Int): McOnline.ReviewPage {
        val language = McOnline.languageFor(I18n.code)
        val key = "$language/$apiId/$page"
        synchronized(cache) { cache[key] }?.let { return it }
        val r = McOnline.parseReviews(Http.get(McOnline.reviewsUrl(apiId, page), McOnline.reviewHeaders(language)).body)
        synchronized(cache) { cache[key] = r }
        return r
    }

    /** Avis par le serveur maison (même format) : commentaires Cookomix, ou avis du site relayés. */
    fun loadHome(prefs: Prefs, id: String, page: Int): McOnline.ReviewPage {
        val key = "maison/$id/$page"
        synchronized(cache) { cache[key] }?.let { return it }
        val r = McOnline.parseReviews(HomeServer.call(prefs, "GET", "/api/avis/" + HomeServer.path(id) + "?page=$page"))
        synchronized(cache) { cache[key] = r }
        return r
    }
}
