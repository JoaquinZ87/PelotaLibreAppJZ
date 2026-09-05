package com.jz.pelotalibretv.data

import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.jz.pelotalibretv.domain.model.PlayerDocumentRule
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PlayerDocumentInterceptor(
    embedUrl: String,
    rules: List<PlayerDocumentRule>
) {
    private val active = AtomicBoolean(true)
    private val applicableRules = rules.filter {
        val parent = Uri.parse(embedUrl)
        parent.scheme == "https" && parent.host.equals(it.parentHost, true)
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        if (!active.get() || request.isForMainFrame || request.method != "GET") return null
        val uri = request.url
        if (uri.scheme != "https" || uri.port !in listOf(-1, 443)) return null
        val rule = applicableRules.firstOrNull {
            uri.host.equals(it.playerHost, true) && uri.path == it.playerPath
        } ?: return null
        return runCatching {
            val builder = Request.Builder().url(uri.toString())
            request.requestHeaders.forEach { (name, value) ->
                if (name.lowercase() !in setOf("referer", "accept-encoding", "host")) {
                    builder.header(name, value)
                }
            }
            builder.header("Referer", rule.referer)
            client.newCall(builder.build()).execute().use { response ->
                if (response.code != 200 || response.headers.values("Set-Cookie").isNotEmpty()) {
                    Log.w("PelotaLibre", "Documento de player: respuesta no interceptada (${response.code})")
                    return@use null
                }
                val body = response.body ?: return@use null
                val type = body.contentType() ?: return@use null
                if (type.type != "text" || type.subtype != "html") return@use null
                val source = body.source()
                source.request(1_048_577)
                if (source.buffer.size > 1_048_576 || !active.get()) return@use null
                val bytes = source.readByteArray()
                val excluded = setOf("content-length", "content-encoding", "transfer-encoding", "connection")
                val headers = response.headers.names()
                    .filterNot { it.lowercase() in excluded }
                    .associateWith { response.headers.values(it).joinToString(", ") }
                Log.i("PelotaLibre", "Documento de player: Referer del padre aplicado")
                WebResourceResponse(
                    "text/html", type.charset(Charsets.UTF_8)!!.name(),
                    response.code, response.message.ifBlank { "OK" }, headers,
                    ByteArrayInputStream(bytes)
                )
            }
        }.getOrElse {
            Log.w("PelotaLibre", "Documento de player: se conserva la carga WebView (${it.javaClass.simpleName})")
            null
        }
    }

    fun close() {
        active.set(false)
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }
}
