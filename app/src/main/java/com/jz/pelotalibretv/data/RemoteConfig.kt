package com.jz.pelotalibretv.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

object RemoteConfig {
    const val CONFIG_URL = "https://raw.githubusercontent.com/JoaquinZ87/pelotalibretv-config/main/config.json"
    private val mutableSources = MutableStateFlow(AppConfig.sources)
    val sources = mutableSources.asStateFlow()
    private val mutableStatus = MutableStateFlow("Configuración local")
    val status = mutableStatus.asStateFlow()
    private val mutex = Mutex()
    private var prefs: SharedPreferences? = null
    private var publicKey = ""
    private var lastAttempt = -300_000L

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("remote_config", Context.MODE_PRIVATE)
        publicKey = context.assets.open("config-public-key.txt").bufferedReader().use { it.readText().trim() }
        val bundled = context.assets.open("config-v2.json").bufferedReader().use { it.readText() }
        ConfigCodec.legacyRecipes = JSONObject(bundled).getJSONObject("recipes")
        apply(bundled)
        val cache = prefs?.getString("last_good_json", null)
        if (cache != null && !apply(cache)) prefs?.getString("previous_json", null)?.let { apply(it) }
    }

    private fun apply(json: String): Boolean = runCatching {
        val snapshot = ConfigCodec.decode(json)
        ConfigCodec.install(snapshot)
        mutableSources.value = snapshot.sources
        true
    }.getOrDefault(false)

    suspend fun ensureFresh(ioDispatcher: CoroutineDispatcher = Dispatchers.IO, force: Boolean = false) {
        mutex.withLock {
            if (!force && SystemClock.elapsedRealtime() - lastAttempt < 300_000) return
            lastAttempt = SystemClock.elapsedRealtime()
            withContext(ioDispatcher) {
                try {
                    val signed = SignedConfig.fetch(publicKey)
                    val json = signed ?: if (prefs?.getBoolean("signed_active", false) == true) {
                        error("Catálogo firmado no disponible")
                    } else TrustedHttp.get(CONFIG_URL)
                    val snapshot = ConfigCodec.decode(json)
                    val highWater = prefs?.getLong("highest_revision", 0) ?: 0
                    require(signed == null || snapshot.revision >= highWater) { "Revisión anterior rechazada" }
                    val editor = prefs?.edit()
                    val previous = prefs?.getString("last_good_json", null)
                    if (previous != json) editor?.putString("previous_json", previous)
                    editor?.putString("last_good_json", json)
                    if (signed != null) editor?.putBoolean("signed_active", true)?.putLong("highest_revision", snapshot.revision)
                    check(editor?.commit() != false) { "No se pudo guardar configuración" }
                    ConfigCodec.install(snapshot)
                    mutableSources.value = snapshot.sources
                    mutableStatus.value = if (signed == null) "Fuentes actualizadas · catálogo compatible" else "Configuración actualizada · ${snapshot.revision}"
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    mutableStatus.value = "Se conserva configuración anterior: ${error.message}"
                }
            }
        }
    }

    suspend fun rollback() = mutex.withLock {
        val previous = prefs?.getString("previous_json", null) ?: run {
            mutableStatus.value = "No hay configuración anterior guardada"
            return@withLock false
        }
        val snapshot = runCatching { ConfigCodec.decode(previous) }.getOrNull() ?: return@withLock false
        val current = prefs?.getString("last_good_json", null)
        if (prefs?.edit()?.putString("last_good_json", previous)?.putString("previous_json", current)?.commit() != true) return@withLock false
        ConfigCodec.install(snapshot)
        mutableSources.value = snapshot.sources
        mutableStatus.value = "Configuración anterior restaurada"
        lastAttempt = SystemClock.elapsedRealtime()
        true
    }
}
