package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Event
import com.jz.pelotalibretv.domain.model.Server
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

/**
 * Baja y parsea la agenda. Prueba los mirrors de [AppConfig] en orden.
 * Parseo DEFENSIVO: se apoya en el patrón de link "?r=" (estable) más que en las
 * clases CSS. Cada evento/servidor se parsea aislado: si un <li> viene roto, se saltea.
 *
 * Devuelve:
 *  - lista con eventos  -> hay partidos
 *  - lista vacía        -> se alcanzó la fuente pero no hay partidos ahora
 *  - null               -> no se pudo alcanzar ningún mirror
 */
class AgendaScraper(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun fetchAgenda(): List<Event>? = withContext(ioDispatcher) {
        var reachedAny = false
        for (mirror in AppConfig.mirrors) {
            val url = mirror.trimEnd('/') + AppConfig.agendaPath
            val html = runCatching { SiteHttp.get(url) }.getOrNull() ?: continue
            reachedAny = true
            val events = parse(html, url)
            if (events.isNotEmpty()) return@withContext events
        }
        if (reachedAny) emptyList() else null
    }

    /** Parseo público (se puede testear con un HTML de ejemplo, sin red). */
    fun parse(html: String, baseUrl: String): List<Event> {
        val doc = Jsoup.parse(html, baseUrl)
        val events = mutableListOf<Event>()
        for (li in doc.select("ul.menu > li")) {
            runCatching {
                val header = li.selectFirst("a") ?: return@runCatching
                val title = header.ownText().trim()
                if (title.isEmpty()) return@runCatching
                val rawTime = header.selectFirst("span")?.text().orEmpty().trim()
                val time = TimeConverter.toLocal(rawTime) // UTC+1 de la fuente -> hora local
                val category = li.className().trim()

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

                // Incluimos también eventos SIN señal (se muestran como "sin señal aún"
                // y aparecen jugables solos cuando les asignan el stream, vía auto-refresh).
                events += Event(title = title, time = time, category = category, servers = servers)
            }
        }
        return events
    }
}
