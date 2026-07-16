package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Channel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Scrapea los canales 24/7 de la home (div.cards-container > div.card).
 * Si el scrape falla (mirror caído o markup cambiado), cae a [AppConfig.defaultChannels].
 */
class ChannelScraper(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun fetchChannels(): List<Channel> = withContext(ioDispatcher) {
        for (mirror in AppConfig.mirrors) {
            val url = mirror.trimEnd('/') + AppConfig.homePath
            val html = runCatching { SiteHttp.get(url) }.getOrNull() ?: continue
            val channels = parse(html, url)
            if (channels.isNotEmpty()) return@withContext channels
        }
        AppConfig.defaultChannels
    }

    fun parse(html: String, baseUrl: String): List<Channel> {
        val doc = Jsoup.parse(html, baseUrl)
        return doc.select("div.cards-container div.card").mapNotNull { card ->
            runCatching {
                val name = card.selectFirst("h3")?.text()?.trim().orEmpty()
                val link = card.selectFirst("a[href]")?.attr("abs:href").orEmpty()
                val logo = card.selectFirst("img")?.attr("abs:src").orEmpty()
                if (name.isEmpty() || link.isEmpty()) null
                else Channel(name = name, logoUrl = logo, pageUrl = link)
            }.getOrNull()
        }
    }
}
