package com.jz.pelotalibretv.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String
)

/**
 * Auto-update (M6, parte 2). Baja un version.json de GitHub y, si hay una versión MÁS NUEVA que
 * la instalada, devuelve la info para ofrecer actualizar. La URL del APK es estable
 * (releases/latest) para no tener que tocar nada en cada versión.
 */
object UpdateChecker {

    const val VERSION_URL =
        "https://raw.githubusercontent.com/JoaquinZ87/pelotalibretv-config/main/version.json"

    suspend fun check(
        currentVersionCode: Int,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ): UpdateInfo? = withContext(ioDispatcher) {
        val json = runCatching { SiteHttp.get(VERSION_URL) }.getOrNull() ?: return@withContext null
        runCatching {
            val o = JSONObject(json)
            val vc = o.optInt("versionCode", 0)
            val apk = o.optString("apkUrl")
            if (vc > currentVersionCode && apk.startsWith("http")) {
                UpdateInfo(vc, o.optString("versionName"), apk, o.optString("notes"))
            } else null
        }.getOrNull()
    }
}
