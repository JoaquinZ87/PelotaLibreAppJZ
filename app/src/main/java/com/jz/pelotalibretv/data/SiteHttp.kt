package com.jz.pelotalibretv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP único (pool/HTTP2) + GET con headers de navegador.
 * Lo comparten AgendaScraper y ChannelScraper. Body one-shot -> siempre .use { }.
 */
object SiteHttp {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** GET simple. Lanza excepción si no es 2xx. */
    fun get(url: String): String {
        val base = url.substringBefore("/es/") + "/es/"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", AppConfig.userAgent)
            .header("Accept-Language", "es-ES,es;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", base)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }
}
