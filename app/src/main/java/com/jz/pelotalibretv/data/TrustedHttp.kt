package com.jz.pelotalibretv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

object TrustedHttp {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followSslRedirects(false)
        .build()

    fun get(url: String, maxBytes: Long = 2_000_000): String {
        require(url.toHttpUrl().isHttps)
        return client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body ?: error("Respuesta vacía")
            val buffer = okio.Buffer()
            val input = body.source()
            while (buffer.size <= maxBytes && input.read(buffer, minOf(8192L, maxBytes + 1 - buffer.size)) != -1L) { }
            val bytes = buffer.readByteArray()
            require(bytes.size <= maxBytes) { "Configuración demasiado grande" }
            bytes.toString(Charsets.UTF_8)
        }
    }
}
