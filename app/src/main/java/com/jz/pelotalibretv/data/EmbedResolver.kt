package com.jz.pelotalibretv.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Resuelve la URL del reproductor (embed) de una página de canal.
 * La página del canal trae un <iframe> al player, ej:
 *   https://latamvidz1.com/canal.php?stream=tycsports
 * Elegimos el iframe que parece player (canal.php / stream=) y descartamos los de ads.
 */
object EmbedResolver {

    private val playerMarkers = listOf("canal.php", "stream=", "/embed", "reproductor", "player")
    private val adHints = listOf("ads", "aclib", "acscdn", "doubleclick", "pop")

    suspend fun resolveChannel(
        channelPageUrl: String,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): String? = withContext(ioDispatcher) {
        val html = runCatching { SiteHttp.get(channelPageUrl) }.getOrNull() ?: return@withContext null
        val doc = Jsoup.parse(html, channelPageUrl)
        val iframes = doc.select("iframe[src]")
            .map { it.attr("abs:src") }
            .filter { it.isNotBlank() && adHints.none { hint -> it.contains(hint, ignoreCase = true) } }

        iframes.firstOrNull { src -> playerMarkers.any { src.contains(it, ignoreCase = true) } }
            ?: iframes.firstOrNull()
    }
}
