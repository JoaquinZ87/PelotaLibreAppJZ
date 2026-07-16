package com.jz.pelotalibretv.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Config remota (M7). La app baja un JSON de GitHub al abrir y PISA los valores por defecto
 * de [AppConfig] (dominios, rutas, user-agent). Así, cuando el sitio se muda de dominio,
 * se edita ESE JSON y la app se arregla sola, SIN recompilar ni reinstalar.
 *
 * - Nunca falla: si no puede bajar/parsear, se queda con lo último bueno (cache) o los defaults.
 * - Cachea el último JSON bueno en SharedPreferences (sobrevive reinicios / sin internet).
 *
 * >>> [CONFIG_URL] es lo ÚNICO hardcodeado: la URL "raw" del config.json en GitHub. <<<
 */
object RemoteConfig {

    // OJO: si tu usuario/repo de GitHub es distinto, avisá y lo cambio.
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

    /** Aplica el JSON a AppConfig. Devuelve true si parseó ok. */
    private fun applyJson(json: String): Boolean = runCatching {
        val obj = JSONObject(json)
        obj.optJSONArray("mirrors")?.let { arr ->
            val list = (0 until arr.length())
                .map { arr.optString(it).trim() }
                .filter { it.startsWith("http") }
            if (list.isNotEmpty()) AppConfig.mirrors = list
        }
        obj.optString("agendaPath").takeIf { it.isNotBlank() }?.let { AppConfig.agendaPath = it }
        obj.optString("homePath").takeIf { it.isNotBlank() }?.let { AppConfig.homePath = it }
        obj.optString("userAgent").takeIf { it.isNotBlank() }?.let { AppConfig.userAgent = it }
        if (obj.has("sourceUtcOffsetMinutes")) {
            AppConfig.sourceUtcOffsetMinutes =
                obj.optInt("sourceUtcOffsetMinutes", AppConfig.sourceUtcOffsetMinutes)
        }
        if (obj.has("targetUtcOffsetMinutes")) {
            AppConfig.targetUtcOffsetMinutes =
                obj.optInt("targetUtcOffsetMinutes", AppConfig.targetUtcOffsetMinutes)
        }
        true
    }.getOrDefault(false)
}
