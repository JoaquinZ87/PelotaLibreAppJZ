package com.jz.pelotalibretv.data

import android.net.Uri
import android.util.Base64

/**
 * Decodifica el parámetro `r=` de los links de servidor.
 * El `r=` es base64 (URL-safe o estándar, con o sin padding) de la URL real del embed.
 *
 * Ej: ...eventos.html?r=aHR0cHM6Ly92aWR6ZW52aXZvLmNjL2NhbmFsLnBocD9zdHJlYW09ZHNwb3J0cw==
 *      ->  https://vidzenvivo.cc/canal.php?stream=dsports
 *
 * Se usa android.util.Base64 (no java.util.Base64, que recién existe desde API 26; minSdk=23).
 */
object EmbedDecoder {

    /** Recibe el href completo del <a> y devuelve la URL del embed, o null si no se puede. */
    fun fromHref(href: String): String? {
        val r = runCatching { Uri.parse(href).getQueryParameter("r") }.getOrNull()
            ?: return null
        return decode(r)
    }

    /** Recibe el valor crudo del r= y devuelve la URL del embed, o null. */
    fun decode(r: String): String? {
        val flags = if (r.contains('-') || r.contains('_')) Base64.URL_SAFE else Base64.DEFAULT
        return try {
            val bytes = Base64.decode(r, flags or Base64.NO_WRAP)
            val url = String(bytes, Charsets.UTF_8).trim()
            if (url.startsWith("http://") || url.startsWith("https://")) url else null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
