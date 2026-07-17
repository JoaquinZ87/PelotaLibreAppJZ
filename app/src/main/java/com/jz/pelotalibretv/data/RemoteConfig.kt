package com.jz.pelotalibretv.data

import android.content.Context
import android.content.SharedPreferences
import com.jz.pelotalibretv.domain.model.Source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Config remota (M7). La app baja un config.json de GitHub al abrir y PISA la lista de FUENTES
 * de [AppConfig]. Así, cuando un sitio se muda / cambia un selector / se suma una fuente nueva,
 * se edita ESE JSON y la app se arregla sola, SIN recompilar ni reinstalar.
 *
 * - Nunca falla: si no puede bajar/parsear, se queda con lo último bueno (cache) o los defaults.
 * - Cachea el último JSON bueno en SharedPreferences (sobrevive offline).
 *
 * >>> [CONFIG_URL] es lo ÚNICO hardcodeado: la URL "raw" del config.json en GitHub. <<<
 */
object RemoteConfig {

    const val CONFIG_URL =
        "https://raw.githubusercontent.com/JoaquinZ87/pelotalibretv-config/main/config.json"

    private const val PREFS = "remote_config"
    private const val KEY_JSON = "last_good_json"

    private val mutex = Mutex()

    @Volatile
    private var refreshedThisRun = false

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Llamar una vez al arrancar (MainActivity): carga el último JSON bueno cacheado. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY_JSON, null)?.let { applyJson(it) }
    }

    /** Baja el JSON remoto UNA vez por ejecución y actualiza AppConfig. No lanza excepciones. */
    suspend fun ensureFresh(ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
        if (refreshedThisRun) return
        mutex.withLock {
            if (refreshedThisRun) return
            withContext(ioDispatcher) {
                val json = runCatching { SiteHttp.get(CONFIG_URL) }.getOrNull()
                if (json != null && applyJson(json)) {
                    runCatching { prefs?.edit()?.putString(KEY_JSON, json)?.apply() }
                }
            }
            refreshedThisRun = true
        }
    }

    /** Parsea el JSON y pisa AppConfig.sources. Devuelve true si obtuvo al menos una fuente. */
    private fun applyJson(json: String): Boolean = runCatching {
        val arr = JSONObject(json).optJSONArray("sources") ?: return@runCatching false
        val list = mutableListOf<Source>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val mirrorsArr = o.optJSONArray("mirrors") ?: continue
            val mirrors = (0 until mirrorsArr.length())
                .map { mirrorsArr.optString(it).trim() }
                .filter { it.startsWith("http") }
            if (mirrors.isEmpty()) continue
            list += Source(
                id = o.optString("id", "src$i"),
                name = o.optString("name", "Fuente ${i + 1}"),
                mirrors = mirrors,
                homePath = o.optString("homePath", "/"),
                agendaPath = o.optString("agendaPath", "/agenda/"),
                userAgent = o.optString("userAgent", AppConfig.BROWSER_UA),
                sourceUtcOffsetMinutes = o.optInt("sourceUtcOffsetMinutes", 60),
                targetUtcOffsetMinutes = o.optInt("targetUtcOffsetMinutes", -180),
                channelsEnabled = o.optBoolean("channelsEnabled", false),
                channelCardSelector = o.optString("channelCardSelector", "div.cards-container div.card"),
                channelNameSelector = o.optString("channelNameSelector", "h3"),
                channelLogoSelector = o.optString("channelLogoSelector", "img"),
                channelLinkSelector = o.optString("channelLinkSelector", "a.btn-watch"),
                strategy = o.optString("strategy", "menuR"),
                eventRowSelector = o.optString("eventRowSelector", ""),
                eventTimeSelector = o.optString("eventTimeSelector", ""),
                eventNameSelector = o.optString("eventNameSelector", ""),
                eventLinkSelector = o.optString("eventLinkSelector", "")
            )
        }
        if (list.isNotEmpty()) {
            AppConfig.sources = list
            true
        } else {
            false
        }
    }.getOrDefault(false)
}
