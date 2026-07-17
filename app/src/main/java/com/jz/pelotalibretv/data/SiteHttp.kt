package com.jz.pelotalibretv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP único + GET con headers de navegador. El User-Agent y el Referer (origin del sitio)
 * se adaptan a cada fuente. Body one-shot -> siempre .use { }.
 */
object SiteHttp {

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val ORIGIN = Regex("^(https?://[^/]+)")

    /** GET simple. Lanza excepción si no es 2xx. */
    fun get(url: String, userAgent: String = AppConfig.BROWSER_UA): String {
        val origin = ORIGIN.find(url)?.value ?: url
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Language", "es-ES,es;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", "$origin/")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            return resp.body?.string().orEmpty()
        }
    }
}
