package com.jz.pelotalibretv.domain.model

/**
 * Una opción de reproducción de un evento (un "servidor").
 * Ej: name="DSports", quality="Calidad 720p".
 *
 * [embedUrl] ya viene DECODIFICADO del parámetro r= del link
 * (ej: https://vidzenvivo.cc/canal.php?stream=dsports).
 */
data class Server(
    val name: String,
    val quality: String,
    val embedUrl: String,
    /** true (Familia B): [embedUrl] es una página de detalle a resolver (sacar iframe) antes de reproducir. */
    val needsResolve: Boolean = false
)
