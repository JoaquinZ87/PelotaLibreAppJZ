package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Channel

/**
 * Config VOLÁTIL del scraping (los DEFAULTS). Todo lo que cambia cuando el sitio se muda vive acá.
 * [RemoteConfig] (M7) PISA estos valores con un JSON remoto al abrir → se arregla una mudanza
 * editando ese JSON, SIN recompilar. Por eso mirrors/paths/userAgent son `var` (no `const`).
 * Regla de oro: nada de dominios/rutas hardcodeados en la lógica; todo sale de acá.
 */
object AppConfig {

    /**
     * Dominios espejo, en orden de preferencia. Se prueban en orden hasta que uno
     * responda con datos. La fuente rota de dominio seguido; esta lista es la defensa.
     */
    var mirrors: List<String> = listOf(
        "https://librepelota.su",
        "https://pelotalibrehd.su",
        "https://librepelotatv.net",
        "https://pelotalibre.watch"
    )

    /** Ruta de la agenda (eventos del día) dentro de cada dominio. */
    var agendaPath: String = "/es/agenda/"

    /** Ruta de la home (de donde se scrapean los canales 24/7). */
    var homePath: String = "/es/"

    /** User-Agent de navegador real (evita bloqueos por bot). */
    var userAgent: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /** Cada cuánto se refresca la agenda mientras la pantalla está abierta (ms). */
    const val refreshIntervalMs = 45_000L

    /**
     * Zona horaria en que la FUENTE publica las horas de eventos. Verificado jul/2026: UTC+1 (=60).
     * Ajustable por RemoteConfig ("sourceUtcOffsetMinutes").
     */
    var sourceUtcOffsetMinutes = 60

    /**
     * Zona horaria en la que se MUESTRAN las horas. Fijo Argentina UTC-3 (=-180) para que
     * siempre se vean bien, sin depender de la zona del dispositivo (el emulador está en horario
     * de EE.UU.). Ajustable por RemoteConfig ("targetUtcOffsetMinutes").
     */
    var targetUtcOffsetMinutes = -180

    /**
     * Lista de canales por defecto (fallback si el scrape de la home falla).
     * Son URLs del dominio conocido; si rota, el scrape de la home las corrige solo.
     */
    val defaultChannels = listOf(
        Channel("TyC Sports", "https://cdn.librepelota.su/es/img/tyc_sports.webp", "https://librepelota.su/es/tyc-sports/"),
        Channel("DirecTV Sports", "https://cdn.librepelota.su/es/img/dsports.webp", "https://librepelota.su/es/directv-sports/"),
        Channel("TNT Sports", "https://cdn.librepelota.su/es/img/tnt_sport.webp", "https://librepelota.su/es/tnt-sports/"),
        Channel("ESPN Premium", "https://cdn.librepelota.su/es/img/espn_premium.webp", "https://librepelota.su/es/espn-premium/"),
        Channel("ESPN", "https://cdn.librepelota.su/es/img/espn1.webp", "https://librepelota.su/es/espn-1/"),
        Channel("Fox Sports", "https://cdn.librepelota.su/es/img/fox_sports.webp", "https://librepelota.su/es/fox-sports/"),
        Channel("TUDN", "https://cdn.librepelota.su/es/img/tudn.webp", "https://librepelota.su/es/tudn/"),
        Channel("Win Sports+", "https://cdn.librepelota.su/es/img/win_sports_plus.webp", "https://librepelota.su/es/win-sports-premium/")
    )
}
