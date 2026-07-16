package com.jz.pelotalibretv.domain.model

/**
 * Un canal 24/7 (TyC Sports, DSports, ESPN, etc.).
 * [pageUrl] es la página del canal (ej .../es/tyc-sports/), de donde el reproductor
 * sacará el embed en el M3. [logoUrl] es el .webp del CDN para mostrar el escudo.
 */
data class Channel(
    val name: String,
    val logoUrl: String,
    val pageUrl: String
)
