package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Event
import com.jz.pelotalibretv.domain.model.Server
import com.jz.pelotalibretv.domain.model.Source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Baja y parsea la agenda de una [Source]. Dos estrategias:
 *  - "menuR" (Familia A): `ul.menu > li` + link `?r=BASE64` (embed final directo).
 *  - "rows"  (Familia B): filas de partido -> links a páginas de detalle (needsResolve=true),
 *    que se resuelven a un iframe al reproducir. Agrupa señales por título.
 */
class AgendaScraper(
    private val source: Source,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun fetchAgenda(): List<Event>? = withContext(ioDispatcher) {
        var reachedAny = false
        for (mirror in source.mirrors) {
            val url = mirror.trimEnd('/') + source.agendaPath
            val html = runCatching { SiteHttp.get(url, source.userAgent) }.getOrNull() ?: continue
            reachedAny = true
            val events = parse(html, url)
            if (events.isNotEmpty()) return@withContext events
        }
        if (reachedAny) emptyList() else null
    }

    fun parse(html: String, baseUrl: String): List<Event> =
        if (source.strategy == "rows") parseRows(html, baseUrl) else parseMenu(html, baseUrl)

    /** Familia A: ul.menu + ?r=. Cada servidor ya es un embed final. */
    private fun parseMenu(html: String, baseUrl: String): List<Event> {
        val doc = Jsoup.parse(html, baseUrl)
        val events = mutableListOf<Event>()
        for (li in doc.select("ul.menu > li")) {
            runCatching {
                val header = li.selectFirst("a") ?: return@runCatching
                val title = header.ownText().trim()
                if (title.isEmpty()) return@runCatching
                val time = TimeConverter.toLocal(header.selectFirst("span")?.text().orEmpty().trim(), source)
                val servers = li.select("a[href]")
                    .filter { it.attr("href").contains("?r=") }
                    .mapNotNull { a ->
                        val href = a.attr("abs:href").ifEmpty { a.attr("href") }
                        val embed = EmbedDecoder.fromHref(href) ?: return@mapNotNull null
                        Server(
                            name = a.ownText().trim().ifEmpty { a.text().trim() },
                            quality = a.selectFirst("span")?.text().orEmpty().trim(),
                            embedUrl = embed
                        )
                    }
                events += Event(title, time, li.className().trim(), servers)
            }
        }
        return events
    }

    /** Familia B (RojaDirecta): filas -> links a detalle. Agrupa señales por título. */
    private fun parseRows(html: String, baseUrl: String): List<Event> {
        val doc = Jsoup.parse(html, baseUrl)
        val serversByTitle = LinkedHashMap<String, MutableList<Server>>()
        val timeByTitle = HashMap<String, String>()

        for (row in doc.select(source.eventRowSelector)) {
            runCatching {
                val name = row.selectFirst(source.eventNameSelector)?.text().orEmpty().trim()
                if (name.isEmpty()) return@runCatching
                val rawTime = row.selectFirst(source.eventTimeSelector)?.text().orEmpty().trim()

                val servers = row.select(source.eventLinkSelector).mapIndexedNotNull { i, a ->
                    val href = a.attr("abs:href").ifEmpty { a.attr("href") }
                    if (href.isBlank() || href == "#") return@mapIndexedNotNull null
                    Server(
                        name = a.text().trim().ifEmpty { "Canal ${i + 1}" },
                        quality = "",
                        embedUrl = href,
                        needsResolve = true
                    )
                }
                if (servers.isEmpty()) return@runCatching

                serversByTitle.getOrPut(name) { mutableListOf() } += servers
                timeByTitle.getOrPut(name) { TimeConverter.toLocal(rawTime, source) }
            }
        }

        return serversByTitle.map { (title, servers) ->
            Event(
                title = title,
                time = timeByTitle[title].orEmpty(),
                category = "",
                servers = servers.distinctBy { it.embedUrl }
            )
        }
    }
}
