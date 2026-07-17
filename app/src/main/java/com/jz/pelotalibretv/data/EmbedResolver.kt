package com.jz.pelotalibretv.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Resuelve la URL del embed (reproductor) a partir del link de un canal. Auto-detecta 2 patrones:
 *  1) el link YA es un `?r=BASE64` (canal directo, ej Rústico) -> se decodifica.
 *  2) el link es una página de canal (ej PelotaLibre `/es/espn-1/`) -> se baja y se saca el iframe.
 */
object EmbedResolver {

    private val playerMarkers = listOf(
        "canal.php", "global1.php", "live1.php", "canales.php",
        "embed.php", "embedhd", "playcapo", "rodrixtv", "/tv/",
        "stream=", "/embed", "reproductor", "player"
    )
    private val adHints = listOf("ads", "aclib", "acscdn", "doubleclick", "pop", "banner")

    suspend fun resolveChannel(
        pageUrl: String,
        userAgent: String = AppConfig.BROWSER_UA,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): String? = withContext(ioDispatcher) {
        // 1) link ?r= directo
        EmbedDecoder.fromHref(pageUrl)?.let { return@withContext it }
        // 2) página de canal -> iframe del player
        val html = runCatching { SiteHttp.get(pageUrl, userAgent) }.getOrNull() ?: return@withContext null
        val iframes = Jsoup.parse(html, pageUrl).select("iframe[src]")
            .map { it.attr("abs:src") }
            .filter { it.isNotBlank() && adHints.none { h -> it.contains(h, ignoreCase = true) } }
        iframes.firstOrNull { src -> playerMarkers.any { src.contains(it, ignoreCase = true) } }
            ?: iframes.firstOrNull()
    }
}
