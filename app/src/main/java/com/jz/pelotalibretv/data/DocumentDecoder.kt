package com.jz.pelotalibretv.data

import okio.ByteString.Companion.decodeBase64
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Decodificación de datos públicos. Nunca interpreta código de la fuente. */
object DocumentDecoder {
    private const val MAX_DOCUMENT = 4_000_000
    private const val BASE64_URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun validate(spec: JSONObject) {
        require(spec.getString("type") == "substitutionAesGcm") { "Decodificador desconocido" }
        require(spec.getString("prefix").length in 1..16) { "Prefijo inválido" }
        val alphabet = spec.getString("alphabet")
        require(alphabet.length == 64 && alphabet.toSet() == BASE64_URL.toSet()) { "Alfabeto inválido" }
        val key = spec.getString("keyBase64")
        require(key.length <= 64 && key.matches(Regex("[A-Za-z0-9+/]+={0,2}")) &&
            key.decodeBase64()?.size in setOf(16, 24, 32)) { "Clave de datos inválida" }
    }

    fun decode(text: String, spec: JSONObject?): String {
        require(text.length <= MAX_DOCUMENT) { "Documento demasiado grande" }
        if (spec == null) return text
        validate(spec)
        val input = text.trim()
        val prefix = spec.getString("prefix")
        require(input.startsWith(prefix)) { "Prefijo de datos no reconocido" }
        val alphabet = spec.getString("alphabet")
        val encoded = buildString(input.length - prefix.length) {
            for (position in input.lastIndex downTo prefix.length) {
                val index = alphabet.indexOf(input[position])
                require(index >= 0) { "Carácter de datos inválido" }
                append(BASE64_URL[index])
            }
        }
        val bytes = encoded.decodeBase64()?.toByteArray() ?: error("Datos Base64 inválidos")
        require(bytes.size >= 12 + 16) { "Datos cifrados incompletos" }
        val key = spec.getString("keyBase64").decodeBase64()!!.toByteArray()
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
        } catch (_: java.security.GeneralSecurityException) {
            throw RecipeMismatch("No se pudo verificar la agenda cifrada")
        }
    }
}
