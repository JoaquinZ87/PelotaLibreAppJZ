package com.jz.pelotalibretv.domain.model

/**
 * Una FUENTE de streams (PelotaLibre, Fútbol Libre, AlÁngulo, ...). Todo lo volátil de un sitio
 * vive acá. Las fuentes se definen en AppConfig (defaults) y se pueden PISAR desde el JSON remoto
 * (RemoteConfig) → agregar/arreglar un sitio = editar el JSON, sin recompilar.
 *
 * La agenda de toda la "familia A" comparte el patrón `ul.menu > li` + link `?r=BASE64`
 * (por eso el scraper de eventos y el decodificador son comunes). Lo que cambia por fuente:
 * dominios, paths, zona horaria base y los selectores de las tarjetas de canales.
 */
data class Source(
    val id: String,
    val name: String,
    val mirrors: List<String>,
    val homePath: String,
    val agendaPath: String,
    val userAgent: String,
    val sourceUtcOffsetMinutes: Int,   // zona en que la fuente publica las horas
    val targetUtcOffsetMinutes: Int,   // zona en que las mostramos (Argentina -180)
    val channelsEnabled: Boolean,
    val channelCardSelector: String,
    val channelNameSelector: String,
    val channelLogoSelector: String,
    val channelLinkSelector: String,

    // Estrategia del scraper de EVENTOS:
    //  - "menuR" (Familia A): ul.menu > li + link ?r=BASE64 (embed final directo).
    //  - "rows"  (Familia B, RojaDirecta): filas de partido -> links a páginas de detalle
    //    que hay que resolver (bajar la página y sacar el <iframe>) antes de reproducir.
    val strategy: String = "menuR",
    val eventRowSelector: String = "",
    val eventTimeSelector: String = "",
    val eventNameSelector: String = "",
    val eventLinkSelector: String = ""
)
