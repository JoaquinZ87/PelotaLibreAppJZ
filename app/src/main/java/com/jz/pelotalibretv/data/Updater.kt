package com.jz.pelotalibretv.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Descarga el APK de la actualización y abre el instalador del sistema (ACTION_VIEW + FileProvider).
 * En Android la instalación siempre pide confirmación del usuario (por eso es "semi-automático").
 */
object Updater {

    suspend fun downloadAndInstall(context: Context, apkUrl: String): Boolean {
        val ok = withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "update.apk")
            val req = Request.Builder().url(apkUrl)
                .header("User-Agent", AppConfig.BROWSER_UA).build()
            runCatching {
                SiteHttp.client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching false
                    val body = resp.body ?: return@runCatching false
                    body.byteStream().use { input -> file.outputStream().use { input.copyTo(it) } }
                    file.length() > 0
                }
            }.getOrDefault(false)
        }
        if (!ok) return false

        val file = File(context.cacheDir, "update.apk")
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            withContext(Dispatchers.Main) { context.startActivity(intent) }
            true
        }.getOrDefault(false)
    }
}
