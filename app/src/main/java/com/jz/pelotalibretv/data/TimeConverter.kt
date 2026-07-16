package com.jz.pelotalibretv.data

/**
 * Convierte la hora cruda de un evento (publicada por la fuente en UTC+1) a la zona en la que
 * queremos mostrarla (Argentina UTC-3 por defecto). Ambos offsets salen de [AppConfig] y son
 * ajustables por RemoteConfig. Maneja el cruce de día (ej 04:00 UTC+1 -> 00:00 ARG).
 */
object TimeConverter {

    /** Convierte "HH:mm" de la fuente a la hora de destino. Si no puede, devuelve el original. */
    fun toLocal(raw: String): String {
        val parts = raw.split(":")
        if (parts.size != 2) return raw
        val h = parts[0].trim().toIntOrNull() ?: return raw
        val m = parts[1].trim().toIntOrNull() ?: return raw

        val shift = AppConfig.targetUtcOffsetMinutes - AppConfig.sourceUtcOffsetMinutes
        var total = h * 60 + m + shift
        total = ((total % 1440) + 1440) % 1440 // normaliza a 0..1439 (cruce de día)
        return "%02d:%02d".format(total / 60, total % 60)
    }
}
