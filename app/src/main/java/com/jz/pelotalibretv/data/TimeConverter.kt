package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Source

/**
 * Convierte la hora cruda de un evento (publicada por la fuente en su zona base) a la zona en la
 * que la mostramos (Argentina por defecto). Los offsets salen de la [Source]. Maneja el cruce de
 * día (ej 04:00 -> 00:00). Sin java.time para funcionar desde minSdk 23.
 */
object TimeConverter {

    /** "HH:mm" de la fuente -> hora de destino. Si no puede, devuelve el original. */
    fun toLocal(raw: String, source: Source): String {
        val parts = raw.split(":")
        if (parts.size != 2) return raw
        val h = parts[0].trim().toIntOrNull() ?: return raw
        val m = parts[1].trim().toIntOrNull() ?: return raw

        val shift = source.targetUtcOffsetMinutes - source.sourceUtcOffsetMinutes
        var total = h * 60 + m + shift
        total = ((total % 1440) + 1440) % 1440
        return "%02d:%02d".format(total / 60, total % 60)
    }
}
