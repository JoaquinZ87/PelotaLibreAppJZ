package com.jz.pelotalibretv.data

import okhttp3.Request
import okio.ByteString.Companion.decodeBase64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object SignedConfig {
    private val base = RemoteConfig.CONFIG_URL.substringBeforeLast('/') + "/"

    fun verify(envelopeText: String, publicKey: String): JSONObject {
        val envelope = JSONObject(envelopeText)
        val payload = envelope.getString("payload").decodeBase64()?.toByteArray() ?: error("Payload inválido")
        val signature = envelope.getString("signature").decodeBase64()?.toByteArray() ?: error("Firma inválida")
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKey.decodeBase64()?.toByteArray() ?: error("Clave inválida")))
        require(Signature.getInstance("SHA256withRSA").run { initVerify(key); update(payload); verify(signature) }) { "Firma incorrecta" }
        val root = JSONObject(payload.toString(Charsets.UTF_8))
        require(root.getInt("schemaVersion") == 2 && root.getInt("minEngineVersion") <= RecipeEngine.VERSION)
        require(root.getLong("revision") > 0)
        return root
    }

    fun fetch(publicKey: String): String? {
        val deadline = System.nanoTime() + 60_000_000_000
        val envelopeText = TrustedHttp.client.newCall(Request.Builder().url(base + "manifest.json").build()).execute().use { response ->
            if (response.code == 404) return null
            check(response.isSuccessful) { "Catálogo HTTP ${response.code}" }
            val body = response.body ?: error("Catálogo vacío")
            val source = body.source()
            val buffer = okio.Buffer()
            while (buffer.size <= 262144 && source.read(buffer, minOf(8192L, 262145 - buffer.size)) != -1L) { }
            require(buffer.size <= 262144)
            buffer.readUtf8()
        }
        val root = verify(envelopeText, publicKey)
        val files = root.getJSONArray("sourceFiles")
        require(files.length() in 1..100)
        val sources = JSONArray()
        var totalBytes = 0
        for (index in 0 until files.length()) {
            check(System.nanoTime() < deadline) { "Tiempo máximo de configuración" }
            val file = files.getJSONObject(index)
            val path = file.getString("path")
            require(path.matches(Regex("bundles/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+\\.json")))
            val text = TrustedHttp.get(base + path, 100_000)
            totalBytes += text.toByteArray().size
            require(totalBytes <= 2_000_000)
            val hash = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            require(hash == file.getString("sha256")) { "Integridad incorrecta: $path" }
            sources.put(JSONObject(text))
        }
        root.put("sources", sources)
        return root.toString()
    }
}
