package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Event
import com.jz.pelotalibretv.domain.model.Server
import com.jz.pelotalibretv.domain.model.Source
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.decodeBase64
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLDecoder

class RecipeMismatch(message: String) : IllegalArgumentException(message)

object RecipeEngine {
    const val VERSION = 1
    private const val MAX_ITEMS = 2000
    private val pathPattern = Regex("""\$(?:\.[A-Za-z_][A-Za-z0-9_-]*(?:\[\*\])?)*""")
    private val operations = setOf("trim", "query", "base64", "urlDecode", "absolute", "before", "after", "replace", "default")

    fun validate(recipe: JSONObject, resolver: Boolean = false) {
        require(recipe.optString("format") in setOf("html", "json")) { "Formato no soportado" }
        val format = recipe.getString("format")
        require(recipe.optString("timeFormat", "HH:mm") in setOf("HH:mm", "HH:mm:ss", "h:mma", "h:mm a"))
        fun selection(value: String) {
            require(value.length in 1..500)
            if (format == "json") require(pathPattern.matches(value)) { "Ruta JSON no soportada: $value" }
            else Jsoup.parse("").select(value)
        }
        fun field(spec: JSONObject) {
            if (format == "json") require(spec.has("select") || spec.has("literal"))
            if (spec.has("select")) selection(spec.getString("select"))
            require(spec.optString("read", "text") in setOf("text", "ownText", "attr"))
            if (spec.optString("read") == "attr") require(spec.getString("attribute").isNotBlank())
            val transforms = spec.optJSONArray("transforms") ?: JSONArray()
            require(transforms.length() <= 12)
            for (index in 0 until transforms.length()) {
                val operation = transforms.getJSONObject(index)
                val name = operation.getString("op")
                require(name in operations) { "Operación no soportada: $name" }
                if (name in setOf("query", "before", "after", "replace", "default")) require(operation.has("value"))
                if (name in setOf("before", "after", "replace")) require(operation.getString("value").isNotEmpty())
            }
        }
        if (resolver) field(recipe.getJSONObject("url")) else {
            selection(recipe.getString("items"))
            selection(recipe.getString("recognize"))
            val fields = recipe.getJSONObject("fields")
            require(fields.has("title"))
            fields.keys().forEach { field(fields.getJSONObject(it)) }
            recipe.optJSONObject("servers")?.let { servers ->
                selection(servers.getString("items"))
                val serverFields = servers.getJSONObject("fields")
                require(serverFields.has("url"))
                serverFields.keys().forEach { field(serverFields.getJSONObject(it)) }
            }
        }
        recipe.optJSONObject("request")?.let { request ->
            if (request.has("path")) require(request.getString("path").length in 1..2000)
            val steps = request.optJSONArray("follow") ?: JSONArray()
            require(steps.length() <= 3)
            for (index in 0 until steps.length()) {
                val step = steps.getJSONObject(index)
                validate(JSONObject().put("format", step.getString("format")).put("url", step.getJSONObject("url")), true)
            }
        }
    }

    private fun document(text: String, format: String, baseUrl: String): Any =
        if (format == "html") Jsoup.parse(text, baseUrl) else JSONTokener(text).nextValue()

    private fun select(node: Any, selector: String): List<Any> {
        if (node is Element) return node.select(selector).take(MAX_ITEMS + 1)
        require(pathPattern.matches(selector))
        var values = listOf(node)
        for (part in selector.removePrefix("$").split('.').filter { it.isNotEmpty() }) {
            val array = part.endsWith("[*]")
            val name = part.removeSuffix("[*]")
            values = values.flatMap { value ->
                val child = (value as? JSONObject)?.opt(name)
                if (array && child is JSONArray) (0 until child.length()).map { child.get(it) }
                else if (!array && child != null && child != JSONObject.NULL) listOf(child)
                else emptyList()
            }
            require(values.size <= MAX_ITEMS) { "Demasiados elementos" }
        }
        return values
    }

    fun url(raw: String, baseUrl: String): String? {
        if (raw.isBlank() || raw == "#") return null
        val parsed = baseUrl.toHttpUrlOrNull()?.resolve(raw) ?: raw.toHttpUrlOrNull() ?: return null
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
        return parsed.toString()
    }

    private fun field(node: Any, spec: JSONObject?, baseUrl: String): String {
        if (spec == null) return ""
        val selected = if (spec.has("select")) select(node, spec.getString("select")).firstOrNull() else node
        var value = when {
            spec.has("literal") -> spec.getString("literal")
            selected is Element -> when (spec.optString("read", "text")) {
                "ownText" -> selected.ownText()
                "attr" -> selected.attr(spec.getString("attribute"))
                else -> selected.text()
            }
            selected == null || selected == JSONObject.NULL -> ""
            else -> selected.toString()
        }.trim()
        require(value.length <= 16384)
        val transforms = spec.optJSONArray("transforms") ?: JSONArray()
        for (index in 0 until transforms.length()) {
            val step = transforms.getJSONObject(index)
            val argument = step.optString("value")
            value = when (step.getString("op")) {
                "trim" -> value.trim()
                "query" -> url(value, baseUrl)?.toHttpUrlOrNull()?.queryParameter(argument).orEmpty()
                "base64" -> value.decodeBase64()?.utf8() ?: throw RecipeMismatch("Base64 inválido")
                "urlDecode" -> URLDecoder.decode(value, "UTF-8")
                "absolute" -> url(value, baseUrl).orEmpty()
                "before" -> value.substringBefore(argument)
                "after" -> value.substringAfter(argument, "")
                "replace" -> value.replace(argument, step.optString("with"))
                "default" -> value.ifBlank { argument }
                else -> throw RecipeMismatch("Operación desconocida")
            }
        }
        return value
    }

    fun extractUrl(text: String, baseUrl: String, recipe: JSONObject): String? =
        url(field(document(text, recipe.getString("format"), baseUrl), recipe.getJSONObject("url"), baseUrl), baseUrl)

    fun fetch(source: Source, recipe: JSONObject, defaultPath: String, mirror: String, deadline: Long = System.nanoTime() + 40_000_000_000): SiteHttp.Page {
        val request = recipe.optJSONObject("request") ?: JSONObject()
        check(System.nanoTime() < deadline) { "Tiempo máximo de extracción" }
        var page = SiteHttp.getPage(url(request.optString("path", defaultPath), "$mirror/") ?: error("URL inválida"), source.userAgent)
        val steps = request.optJSONArray("follow") ?: JSONArray()
        val visited = mutableSetOf(page.url)
        for (index in 0 until steps.length()) {
            check(System.nanoTime() < deadline) { "Tiempo máximo de extracción" }
            val next = extractUrl(page.body, page.url, steps.getJSONObject(index)) ?: throw RecipeMismatch("No se encontró página intermedia")
            check(visited.add(next)) { "Ciclo de páginas" }
            page = SiteHttp.getPage(next, source.userAgent)
        }
        return page
    }

    fun parse(text: String, baseUrl: String, source: Source, recipe: JSONObject): List<Event> {
        val root = document(text, recipe.getString("format"), baseUrl)
        val recognized = select(root, recipe.getString("recognize"))
        if (recognized.isEmpty() || (root !is Element && recognized.none { it is JSONArray })) throw RecipeMismatch("Formato de agenda no reconocido")
        val items = select(root, recipe.getString("items"))
        require(items.size <= MAX_ITEMS)
        if (items.isEmpty() && !recipe.optBoolean("allowEmpty", false)) throw RecipeMismatch("Vacío no confirmado")
        val fields = recipe.getJSONObject("fields")
        val serversSpec = recipe.optJSONObject("servers")
        val result = mutableListOf<Event>()
        for (item in items) {
            runCatching {
                val title = field(item, fields.optJSONObject("title"), baseUrl)
                if (title.isBlank()) throw RecipeMismatch("Evento sin título")
                val serverItems = serversSpec?.let { select(item, it.getString("items")) }.orEmpty()
                val serverFields = serversSpec?.getJSONObject("fields")
                val servers = serverItems.mapNotNull { serverItem ->
                    runCatching signal@{
                        val embedUrl = url(field(serverItem, serverFields?.optJSONObject("url"), baseUrl), baseUrl)
                            ?: return@signal null
                        Server(
                            name = field(serverItem, serverFields?.optJSONObject("name"), baseUrl).ifBlank { "Canal" },
                            quality = field(serverItem, serverFields?.optJSONObject("quality"), baseUrl),
                            embedUrl = embedUrl,
                            needsResolve = serversSpec?.optBoolean("needsResolve") == true,
                            referer = baseUrl
                        )
                    }.getOrNull()
                }.distinctBy { it.embedUrl }
                if (serverItems.isNotEmpty() && servers.isEmpty()) throw RecipeMismatch("No se pudieron interpretar las señales")
                val rawTime = field(item, fields.optJSONObject("time"), baseUrl)
                val rawDate = field(item, fields.optJSONObject("date"), baseUrl)
                val schedule = TimeConverter.schedule(rawDate, rawTime, source, recipe.optString("timeFormat", "HH:mm"))
                result += Event(title, schedule.second, field(item, fields.optJSONObject("category"), baseUrl), servers,
                    schedule.first, source.id, baseUrl)
            }
        }
        if (items.isNotEmpty() && result.isEmpty()) throw RecipeMismatch("Ningún evento válido: revisar campos, horas y señales")
        return if (recipe.optBoolean("groupByTitle")) result.groupBy { Triple(it.title, it.date, it.time) }.values.map { group ->
            group.first().copy(servers = group.flatMap { it.servers }.distinctBy { it.embedUrl })
        } else result
    }
}
