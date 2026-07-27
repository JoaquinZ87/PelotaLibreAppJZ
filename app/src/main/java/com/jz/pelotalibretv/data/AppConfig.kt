package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Source

/**
 * Config por defecto (FALLBACK). La lista real de FUENTES vive en el config.json remoto
 * (RemoteConfig la PISA al abrir). Acá van solo las fuentes que suelen andar, como respaldo
 * para el primer arranque antes de que baje la config.
 *
 * Estrategias: "menuR" (Familia A: ul.menu + ?r=) | "rows" (Familia B: fila->detalle->iframe).
 * Zonas base: AlÁngulo1 = Perú (-300); Rústico/AlÁngulo2 = UTC+1 (60); RojaDirecta = España (120).
 */
object AppConfig {

    const val refreshIntervalMs = 45_000L

    const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    @Volatile
    var sources: List<Source> = listOf(
        Source(
            id = "pelotalibre", name = "Pelota Libre",
            mirrors = listOf("https://pelotalibrehd.su"),
            homePath = "/es/", agendaPath = "/es/agenda/", userAgent = BROWSER_UA,
            sourceUtcOffsetMinutes = -300, targetUtcOffsetMinutes = -180, // menu2: varias señales/evento (Perú)
            channelsEnabled = false,
            channelCardSelector = "", channelNameSelector = "",
            channelLogoSelector = "", channelLinkSelector = "",
            strategy = "menu2"
        ),
        Source(
            id = "pelotalibremas", name = "Pelota Libre +",
            mirrors = listOf("https://pelotalibre-hd.su"),
            homePath = "/", agendaPath = "/agenda2/", userAgent = BROWSER_UA,
            sourceUtcOffsetMinutes = -300, targetUtcOffsetMinutes = -180, // menuR: más eventos, 1 señal (Perú)
            channelsEnabled = false,
            channelCardSelector = "div.cards-container div.card", channelNameSelector = "h3",
            channelLogoSelector = "img", channelLinkSelector = "a.btn-watch",
            strategy = "menuR"
        ),
        Source(
            id = "alangulo1", name = "Al Ángulo TV",
            mirrors = listOf("https://alangulotv.su"),
            homePath = "/", agendaPath = "/agenda2/", userAgent = BROWSER_UA,
            sourceUtcOffsetMinutes = -300, targetUtcOffsetMinutes = -180,
            channelsEnabled = false,
            channelCardSelector = "div.grid div.card", channelNameSelector = "h3",
            channelLogoSelector = "img", channelLinkSelector = "a.btn"
        ),
        Source(
            id = "alangulo2", name = "Al Ángulo TV (2)",
            mirrors = listOf("https://alangulotv2.su"),
            homePath = "/", agendaPath = "/agenda.php", userAgent = BROWSER_UA,
            sourceUtcOffsetMinutes = 60, targetUtcOffsetMinutes = -180,
            channelsEnabled = false,
            channelCardSelector = "div.grid div.card", channelNameSelector = "h3",
            channelLogoSelector = "img", channelLinkSelector = "a.btn"
        ),
        Source(
            id = "rustico", name = "Rústico TV",
            mirrors = listOf("https://mirusticotv.su", "https://rusticotv.su"),
            homePath = "/", agendaPath = "/agenda.php", userAgent = BROWSER_UA,
            sourceUtcOffsetMinutes = 60, targetUtcOffsetMinutes = -180,
            channelsEnabled = false,
            channelCardSelector = "div.grid div.card", channelNameSelector = "h3",
            channelLogoSelector = "img", channelLinkSelector = "a.btn"
        ),
        Source(
            id = "rojadirecta", name = "RojaDirecta",
            mirrors = listOf("https://rojadirecta.st"),
            homePath = "/", agendaPath = "/", userAgent = BROWSER_UA,
            sourceUtcOffsetMinutes = 120, targetUtcOffsetMinutes = -180,
            channelsEnabled = false,
            channelCardSelector = "", channelNameSelector = "",
            channelLogoSelector = "", channelLinkSelector = "",
            strategy = "rows",
            eventRowSelector = "div.match", eventTimeSelector = "span.time",
            eventNameSelector = "span.name", eventLinkSelector = "div.chans a[href]"
        )
    )

    /** Fuente por defecto al abrir. */
    val defaultSource: Source get() = sources.first()
}
