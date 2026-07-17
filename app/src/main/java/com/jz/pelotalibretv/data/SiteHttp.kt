package com.jz.pelotalibretv.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Cliente HTTP único + GET con headers de navegador. Sigue redirecciones HTTP + meta/JS.
 *
 * SSL laxo A PROPÓSITO: varios de estos sitios tienen la cadena de certificados incompleta
 * (les falta el intermedio) y OkHttp los rechaza con "Chain validation failed", aunque un
 * navegador los abre igual. Como la app es de USO PERSONAL y solo lee HTML público (sin logins
 * ni datos sensibles), aceptamos cualquier certificado para que esos sitios funcionen.
 */
object SiteHttp {

    private val trustAll: X509TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    val client: OkHttpClient = run {
        val ssl = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        }
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private val ORIGIN = Regex("^(https?://[^/]+)")
    private val META_REFRESH =
        Regex("""(?i)<meta[^>]+http-equiv\s*=\s*["']?refresh["']?[^>]*content\s*=\s*["'][^"']*url\s*=\s*([^"'\s>]+)""")
    private val JS_LOCATION =
        Regex("""(?i)(?:window\.)?location(?:\.href|\.replace)?\s*[=(]\s*["'](https?://[^"']+)["']""")

    /** GET siguiendo hasta 4 saltos de redirección (HTTP + meta/JS). Lanza excepción si no es 2xx. */
    fun get(url: String, userAgent: String = AppConfig.BROWSER_UA): String {
        var current = url
        repeat(4) {
            val html = rawGet(current, userAgent)
            // Solo seguimos meta/JS en páginas CHICAS (stubs de redirección). Las páginas con
            // contenido real (agenda/canales) son grandes y se devuelven tal cual.
            val next = if (html.length < 4000) redirectTarget(html) else null
            if (next == null || next == current) return html
            current = next
        }
        return rawGet(current, userAgent)
    }

    private fun rawGet(url: String, userAgent: String): String {
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

    /** Detecta redirección por meta-refresh o JS (solo a URLs absolutas http(s)). */
    private fun redirectTarget(html: String): String? {
        META_REFRESH.find(html)?.groupValues?.get(1)?.trim()
            ?.let { if (it.startsWith("http")) return it }
        return JS_LOCATION.find(html)?.groupValues?.get(1)?.trim()
    }
}
