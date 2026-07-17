package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Channel
import com.jz.pelotalibretv.domain.model.Source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Scrapea las tarjetas de canales de la home de una [Source], usando SUS selectores.
 * Descarta tarjetas con link vacío o "#" (canales rotos, ej AlÁngulo). Si la fuente no tiene
 * canales usables, devuelve lista vacía. El link (pageUrl) se resuelve al reproducir.
 */
class ChannelScraper(
    private val source: Source,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun fetchChannels(): List<Channel> = withContext(ioDispatcher) {
        if (!source.channelsEnabled) return@withContext emptyList()
        for (mirror in source.mirrors) {
            val url = mirror.trimEnd('/') + source.homePath
            val html = runCatching { SiteHttp.get(url, source.userAgent) }.getOrNull() ?: continue
            val channels = parse(html, url)
            if (channels.isNotEmpty()) return@withContext channels
        }
        emptyList()
    }

    fun parse(html: String, baseUrl: String): List<Channel> {
        val doc = Jsoup.parse(html, baseUrl)
        return doc.select(source.channelCardSelector).mapNotNull { card ->
            runCatching {
                val a = card.selectFirst(source.channelLinkSelector) ?: return@runCatching null
                val rawHref = a.attr("href")
                if (rawHref.isBlank() || rawHref == "#") return@runCatching null
                val name = card.selectFirst(source.channelNameSelector)?.text()?.trim().orEmpty()
                if (name.isEmpty()) return@runCatching null
                Channel(
                    name = name,
                    logoUrl = card.selectFirst(source.channelLogoSelector)?.attr("abs:src").orEmpty(),
                    pageUrl = a.attr("abs:href").ifEmpty { rawHref }
                )
            }.getOrNull()
        }
    }
}
