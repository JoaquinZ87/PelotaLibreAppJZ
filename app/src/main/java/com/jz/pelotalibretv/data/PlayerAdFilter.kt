package com.jz.pelotalibretv.data

import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

class PlayerAdFilter(
    private val hosts: Set<String>,
    private val urls: Set<String>
) {
    fun blocks(uri: Uri): Boolean {
        if (uri.scheme !in setOf("http", "https")) return false
        val host = uri.host?.lowercase() ?: return false
        return hosts.any { host == it || host.endsWith(".$it") } ||
            uri.buildUpon().clearQuery().fragment(null).build().toString() in urls
    }

    fun intercept(uri: Uri): WebResourceResponse? = if (blocks(uri)) {
        WebResourceResponse(
            "text/plain", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0))
        )
    } else null
}
