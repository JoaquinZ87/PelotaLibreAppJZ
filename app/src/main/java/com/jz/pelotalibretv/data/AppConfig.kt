package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Source

/**
 * Config por defecto. La lista de FUENTES vive acá y RemoteConfig la PISA con el JSON remoto.
 *
 * FASES (para ir sumando fuentes):
 *  - Fase 1 (Familia A, motor `?r=` + cards): PelotaLibre, Fútbol Libre, AlÁngulo1, AlÁngulo2.
 *  - Fase 2 (Familia B, agregador fila→detalle→iframe): RojaDirecta y su ecosistema. (Pendiente)
 *
 * Zonas horarias base verificadas (jul/2026): PelotaLibre/FutbolLibre/AlÁngulo2 = UTC+1 (60);
 * AlÁngulo1 = Perú (-300). Todo se muestra en Argentina (-180).
 */
object AppConfig {

    const val refreshIntervalMs = 45_000L

    const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private const val PL_CARD = "div.cards-container div.card"

    @Volatile
    var sources: List<Source> = listOf(
        Source(
            id = "pelotalibre", name = "Pelota Libre",
            mirrors = listOf(
                "https://librepelota.su", "https://pelotalibrehd.su",
                "https://librepelotatv.net", "https://pelotalibre.watch"
            ),
            homePath = "/es/", agendaPath = "/es/agenda/", userAgent = BROWSER_UA,
            sourceUtcOffsetMinutes = 60, targetUtcOffsetMinutes = -180,
            channelsEnabled = true,
            channelCardSelector = PL_CARD, channelNameSelector = "h3",
            channelLogoSelector = "img", channelLinkSelector = "a.btn-watch"
        ),
        Source(
            id = "futbollibre", name = "Fútbol Libre TV",
            mirrors = listOf("https://futbol-libres.su"),
            homePath = "/", agendaPath = "/agenda/", userAgent = BROWSER_UA,
            sourceUtcOffsetMinutes = 60, targetUtcOffsetMinutes = -180,
            channelsEnabled = true,
            channelCardSelector = PL_CARD, channelNameSelector = "h3",
            channelLogoSelector = "img", channelLinkSelector = "a.btn-watch"
        ),
        Source(
            id = "alangulo1", name = "Al Ángulo TV",
            mirrors = listOf("https://alangulotv.su"),
            homePath = "/", agendaPath = "/agenda2/", userAgent = BROWSER_UA,
            sourceUtcOffsetMinutes = -300, targetUtcOffsetMinutes = -180, // base Perú
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
            channelsEnabled = false, // en la home las tarjetas linkean a "/" (no reproducen)
            channelCardSelector = "div.grid div.card", channelNameSelector = "h3",
            channelLogoSelector = "img", channelLinkSelector = "a.btn"
        ),
        // --- Familia B (agregador: fila -> página de detalle -> iframe) ---
        Source(
            id = "rojadirecta", name = "RojaDirecta",
            mirrors = listOf("https://rojadirecta.st"),
            homePath = "/", agendaPath = "/", userAgent = BROWSER_UA,
            sourceUtcOffsetMinutes = 120, targetUtcOffsetMinutes = -180, // base España (verano UTC+2)
            channelsEnabled = false,
            channelCardSelector = "", channelNameSelector = "",
            channelLogoSelector = "", channelLinkSelector = "",
            strategy = "rows",
            eventRowSelector = "div.match", eventTimeSelector = "span.time",
            eventNameSelector = "span.name", eventLinkSelector = "div.chans a[href]"
        ),
        Source(
            id = "tarjetaroja", name = "Tarjeta Roja TV",
            mirrors = listOf("https://tarjetarojatv.click"),
            homePath = "/", agendaPath = "/", userAgent = BROWSER_UA,
            sourceUtcOffsetMinutes = -300, targetUtcOffsetMinutes = -180, // base UTC-5
            channelsEnabled = false,
            channelCardSelector = "", channelNameSelector = "",
            channelLogoSelector = "", channelLinkSelector = "",
            strategy = "rows",
            eventRowSelector = "table tr", eventTimeSelector = "span.t",
            eventNameSelector = "td a", eventLinkSelector = "td a[href]"
        )
    )

    /** Fuente por defecto al abrir. */
    val defaultSource: Source get() = sources.first()
}
