package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Event
import com.jz.pelotalibretv.domain.model.Server
import com.jz.pelotalibretv.domain.model.Source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
        var recognizedEmpty = false
        var mismatch: Exception? = null
        val deadline = System.nanoTime() + 40_000_000_000
        for (mirror in source.mirrors) {
            if (System.nanoTime() >= deadline) break
            val url = mirror.trimEnd('/') + source.agendaPath
            val page = try {
                source.agendaRecipe?.let { RecipeEngine.fetch(source, JSONObject(it), source.agendaPath, mirror, deadline) }
                    ?: SiteHttp.getPage(url, source.userAgent)
            } catch (error: Exception) {
                if (error is RecipeMismatch) mismatch = error
                continue
            }
            reachedAny = true
            val events = try { parse(page.body, page.url) } catch (error: Exception) { mismatch = error; continue }
            if (events.isNotEmpty()) return@withContext events
            recognizedEmpty = true
        }
        if (recognizedEmpty) return@withContext emptyList()
        if (mismatch != null) throw RecipeMismatch(mismatch.message ?: "Formato no reconocido")
        if (reachedAny) emptyList() else null
    }

    fun parse(html: String, baseUrl: String): List<Event> = source.agendaRecipe?.let {
        RecipeEngine.parse(html, baseUrl, source, JSONObject(it))
    } ?: when (source.strategy) {
        "rows" -> parseRows(html, baseUrl)
        "menu2" -> parseMenu2(html, baseUrl)
        "wpjson" -> parseWpJson(html)
        "strapi" -> parseStrapi(html)
        "eventsJson" -> parseEventsJson(html)
        else -> parseMenu(html, baseUrl)
    }

    private fun parseEventsJson(json: String): List<Event> {
        val entries = runCatching { JSONObject(json).optJSONArray("events") }.getOrNull()
            ?: return emptyList()
        return (0 until entries.length()).mapNotNull { index ->
            runCatching eventParse@{
                val entry = entries.optJSONObject(index) ?: return@eventParse null
                val title = entry.optString("title").trim()
                if (title.isEmpty() || title == "null") return@eventParse null
                val embeds = entry.optJSONArray("embeds")
                val servers = (0 until (embeds?.length() ?: 0)).mapNotNull { serverIndex ->
                    runCatching serverParse@{
                        val embed = embeds?.optJSONObject(serverIndex) ?: return@serverParse null
                        val url = embed.optString("iframe").toHttpUrlOrNull() ?: return@serverParse null
                        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return@serverParse null
                        Server(
                            name = embed.optString("name").trim().takeUnless { it.isEmpty() || it == "null" } ?: "Canal",
                            quality = embed.optString("lang").trim().takeUnless { it == "null" }.orEmpty(),
                            embedUrl = url.toString()
                        )
                    }.getOrNull()
                }
                Event(
                    title = title,
                    time = TimeConverter.toLocal(entry.optString("time").take(5), source),
                    category = entry.optString("sport").takeUnless { it == "null" }.orEmpty(),
                    servers = servers
                )
            }.getOrNull()
        }
    }

    /**
     * JSON tipo Strapi (pelotalibre.uno `/agenda-data.php`): `data[].attributes` con `diary_hour`
     * ("HH:MM:SS", base Perú/America-Lima), `diary_description` (título) y `embeds.data[].attributes`
     * con `embed_name` y `embed_iframe` (que trae `?r=BASE64` → embed real, lo decodifica EmbedDecoder).
     */
    private fun parseStrapi(json: String): List<Event> {
        val events = mutableListOf<Event>()
        runCatching {
            val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
            for (i in 0 until data.length()) {
                runCatching {
                    val at = data.getJSONObject(i).optJSONObject("attributes") ?: return@runCatching
                    val title = at.optString("diary_description").trim()
                    if (title.isEmpty()) return@runCatching
                    val time = TimeConverter.toLocal(at.optString("diary_hour").take(5), source)
                    val servers = mutableListOf<Server>()
                    val embeds = at.optJSONObject("embeds")?.optJSONArray("data")
                    if (embeds != null) {
                        for (e in 0 until embeds.length()) {
                            val ea = embeds.getJSONObject(e).optJSONObject("attributes") ?: continue
                            val embed = EmbedDecoder.fromHref(ea.optString("embed_iframe")) ?: continue
                            servers += Server(
                                name = ea.optString("embed_name").trim().ifEmpty { "Canal" },
                                quality = ea.optString("idioma").trim().let { if (it == "null") "" else it },
                                embedUrl = embed
                            )
                        }
                    }
                    events += Event(title, time, "", servers)
                }
            }
        }
        return events
    }

    /**
     * Plantilla WordPress nueva (2026): plugin "futbol-agenda" que sirve la agenda por JSON en
     * `/wp-admin/admin-ajax.php?action=futbol_agenda_data`. La usan alangulotv.quest, pelotalibre.uno,
     * etc. El JSON ya trae los embeds decodificados en `channels[].stream_url` (no hay base64 ni que
     * resolver). `time_raw` viene en el huso de la fuente (ej Perú -300).
     */
    private fun parseWpJson(json: String): List<Event> {
        val events = mutableListOf<Event>()
        runCatching {
            val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
            for (i in 0 until data.length()) {
                runCatching {
                    val o = data.getJSONObject(i)
                    val title = o.optString("title").trim()
                    if (title.isEmpty()) return@runCatching
                    val time = TimeConverter.toLocal(o.optString("time_raw").take(5), source)
                    val servers = mutableListOf<Server>()
                    val chans = o.optJSONArray("channels")
                    if (chans != null) {
                        for (c in 0 until chans.length()) {
                            val ch = chans.optJSONObject(c) ?: continue
                            val url = ch.optString("stream_url").trim()
                            if (url.isEmpty() || !url.startsWith("http")) continue
                            servers += Server(
                                name = ch.optString("name").trim().ifEmpty { "Canal" },
                                quality = "",
                                embedUrl = url
                            )
                        }
                    }
                    events += Event(title, time, o.optString("country").trim(), servers)
                }
            }
        }
        return events
    }

    /**
     * Familia A rediseñada (PelotaLibre 2026): ul#menu > li con div.info (título en span,
     * hora en <time datetime>) y ul.submenu con los links ?r=.
     */
    private fun parseMenu2(html: String, baseUrl: String): List<Event> {
        val doc = Jsoup.parse(html, baseUrl)
        val events = mutableListOf<Event>()
        for (li in doc.select("ul#menu > li")) {
            runCatching {
                val info = li.selectFirst(".info") ?: return@runCatching
                val title = info.selectFirst("span")?.text()?.trim().orEmpty()
                if (title.isEmpty()) return@runCatching
                val rawTime = info.selectFirst("time")?.attr("datetime").orEmpty().take(5)
                val time = TimeConverter.toLocal(rawTime, source)
                val servers = li.select("a[href]")
                    .filter { it.attr("href").contains("?r=") }
                    .mapNotNull { a ->
                        val href = a.attr("abs:href").ifEmpty { a.attr("href") }
                        val embed = EmbedDecoder.fromHref(href) ?: return@mapNotNull null
                        Server(
                            name = a.selectFirst("span")?.text()?.trim().orEmpty().ifEmpty { "Canal" },
                            quality = "",
                            embedUrl = embed
                        )
                    }
                events += Event(title, time, li.attr("data-category").trim(), servers)
            }
        }
        return events
    }

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
