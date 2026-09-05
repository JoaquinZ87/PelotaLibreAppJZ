package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Source
import com.jz.pelotalibretv.domain.model.PlayerDocumentRule
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

data class ConfigSnapshot(
    val sources: List<Source>, val disabled: Set<String>, val blockedHosts: Set<String>,
    val blockedUrls: Set<String>, val adText: List<String>, val documentRules: List<PlayerDocumentRule>, val revision: Long
)

object ConfigCodec {
    var legacyRecipes: JSONObject = JSONObject()
    @Volatile var active: ConfigSnapshot? = null
        private set

    fun decode(json: String): ConfigSnapshot {
        val root = JSONObject(json)
        require(root.optInt("schemaVersion", 1) in 1..2) { "Esquema incompatible" }
        require(root.optInt("minEngineVersion", 1) <= RecipeEngine.VERSION) { "Actualizar APK" }
        val recipes = root.optJSONObject("recipes") ?: legacyRecipes
        fun strings(key: String, fallback: Collection<String>): List<String> {
            if (!root.has(key)) return fallback.toList()
            val array = root.getJSONArray(key)
            require(array.length() <= 500)
            return (0 until array.length()).map { array.getString(it).trim().also { value -> require(value.isNotEmpty()) } }
        }
        val disabled = strings("disabledSourceIds", AppConfig.disabledSourceIds).toSet()
        val entries = root.getJSONArray("sources")
        require(entries.length() in 1..100)
        val ids = mutableSetOf<String>()
        val sources = (0 until entries.length()).map { index ->
            val entry = entries.getJSONObject(index)
            val id = entry.getString("id")
            require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}")) && ids.add(id)) { "ID inválido o duplicado" }
            val mirrorsArray = entry.getJSONArray("mirrors")
            require(mirrorsArray.length() in 1..10)
            val mirrors = (0 until mirrorsArray.length()).map { mirrorIndex ->
                val value = mirrorsArray.getString(mirrorIndex).toHttpUrlOrNull() ?: error("Mirror inválido")
                require(value.username.isEmpty() && value.password.isEmpty())
                require(value.encodedPath == "/" && value.query == null && value.fragment == null)
                value.toString().trimEnd('/')
            }
            val strategy = entry.optString("strategy", "menuR")
            fun recipe(key: String, fallback: String? = null, resolver: Boolean = false): String? {
                val spec = when (val value = entry.opt(key)) {
                    is JSONObject -> value
                    is String -> recipes.getJSONObject(value)
                    null, JSONObject.NULL -> fallback?.let { recipes.optJSONObject(it) }
                    else -> error("Receta inválida")
                } ?: return null
                val copy = JSONObject(spec.toString())
                if (key == "agendaRecipe" && strategy == "rows" && !entry.has(key)) {
                    copy.put("items", entry.getString("eventRowSelector"))
                    copy.getJSONObject("fields").getJSONObject("title").put("select", entry.getString("eventNameSelector"))
                    copy.getJSONObject("fields").getJSONObject("time").put("select", entry.getString("eventTimeSelector"))
                    copy.getJSONObject("servers").put("items", entry.getString("eventLinkSelector"))
                }
                RecipeEngine.validate(copy, resolver)
                return copy.toString()
            }
            val agenda = if (entry.optBoolean("useLegacyParser")) null else recipe("agendaRecipe", strategy)
            require(agenda != null || strategy in setOf("menuR", "menu2", "rows", "strapi", "wpjson", "eventsJson"))
            fun offset(key: String, default: Int): Int = entry.optInt(key, default).also { require(it in -840..840) }
            fun zone(key: String, default: String): String = entry.optString(key, default).also {
                require(it.isBlank() || it in java.util.TimeZone.getAvailableIDs())
            }
            Source(
                id = id, name = entry.getString("name"), mirrors = mirrors,
                homePath = entry.optString("homePath", "/"), agendaPath = entry.optString("agendaPath", "/agenda/"),
                userAgent = entry.optString("userAgent", AppConfig.BROWSER_UA),
                sourceUtcOffsetMinutes = offset("sourceUtcOffsetMinutes", 60), targetUtcOffsetMinutes = offset("targetUtcOffsetMinutes", -180),
                channelsEnabled = entry.optBoolean("channelsEnabled"),
                channelCardSelector = entry.optString("channelCardSelector", "div.cards-container div.card"),
                channelNameSelector = entry.optString("channelNameSelector", "h3"),
                channelLogoSelector = entry.optString("channelLogoSelector", "img"),
                channelLinkSelector = entry.optString("channelLinkSelector", "a.btn-watch"),
                strategy = strategy, platform = entry.optString("platform"),
                eventRowSelector = entry.optString("eventRowSelector"), eventTimeSelector = entry.optString("eventTimeSelector"),
                eventNameSelector = entry.optString("eventNameSelector"), eventLinkSelector = entry.optString("eventLinkSelector"),
                agendaRecipe = agenda, channelRecipe = recipe("channelRecipe"), resolverRecipe = recipe("resolverRecipe", "iframe", true),
                sourceTimeZone = zone("sourceTimeZone", ""), targetTimeZone = zone("targetTimeZone", "America/Argentina/Buenos_Aires")
            )
        }.filter { it.id !in disabled }
        require(sources.isNotEmpty()) { "No quedan fuentes habilitadas" }
        val rules = if (!root.has("playerDocumentRules")) AppConfig.playerDocumentRules else {
            val array = root.getJSONArray("playerDocumentRules")
            require(array.length() <= 100)
            (0 until array.length()).map { index ->
                val entry = array.getJSONObject(index)
                val parent = entry.getString("parentHost")
                val host = entry.getString("playerHost")
                val path = entry.getString("playerPath")
                val referer = entry.getString("referer").toHttpUrlOrNull() ?: error("Referer inválido")
                require(referer.isHttps && referer.host == parent && referer.port == 443)
                require(referer.username.isEmpty() && referer.password.isEmpty() && path.startsWith('/'))
                require("https://$host".toHttpUrlOrNull()?.host == host)
                PlayerDocumentRule(parent, host, path, referer.toString())
            }
        }
        return ConfigSnapshot(sources, disabled,
            strings("playerBlockedHosts", AppConfig.playerBlockedHosts).map { it.lowercase() }.toSet(),
            strings("playerBlockedUrls", AppConfig.playerBlockedUrls).toSet(),
            strings("playerAdText", AppConfig.playerAdText), rules, root.optLong("revision", 0))
    }

    fun install(snapshot: ConfigSnapshot) {
        AppConfig.disabledSourceIds = snapshot.disabled
        AppConfig.playerBlockedHosts = snapshot.blockedHosts
        AppConfig.playerBlockedUrls = snapshot.blockedUrls
        AppConfig.playerAdText = snapshot.adText
        AppConfig.playerDocumentRules = snapshot.documentRules
        AppConfig.sources = snapshot.sources
        active = snapshot
    }
}
