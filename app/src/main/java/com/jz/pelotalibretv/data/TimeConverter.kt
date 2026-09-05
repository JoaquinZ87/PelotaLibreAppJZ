package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.Source

/**
 * Convierte la hora cruda de un evento (publicada por la fuente en su zona base) a la zona en la
 * que la mostramos (Argentina por defecto). Los offsets salen de la [Source]. Maneja el cruce de
 * día (ej 04:00 -> 00:00). Sin java.time para funcionar desde minSdk 23.
 */
object TimeConverter {

    fun schedule(date: String, time: String, source: Source, format: String): Pair<String, String> {
        if (time.isBlank()) return date to ""
        val parser = java.text.SimpleDateFormat(format, java.util.Locale.US).apply {
            isLenient = false
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val position = java.text.ParsePosition(0)
        val parsed = parser.parse(time, position) ?: throw RecipeMismatch("Hora inválida")
        if (position.index != time.length) throw RecipeMismatch("Formato de hora incorrecto")
        val normalized = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(parsed)
        if (date.isBlank()) return "" to toLocal(normalized, source)
        fun zone(name: String, offset: Int): java.util.TimeZone = if (name.isBlank()) {
            java.util.SimpleTimeZone(offset * 60_000, "source")
        } else {
            require(name in java.util.TimeZone.getAvailableIDs()) { "Zona horaria desconocida" }
            java.util.TimeZone.getTimeZone(name)
        }
        val fullParser = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply {
            isLenient = false
            timeZone = zone(source.sourceTimeZone, source.sourceUtcOffsetMinutes)
        }
        val input = "$date $normalized"
        val fullPosition = java.text.ParsePosition(0)
        val instant = fullParser.parse(input, fullPosition) ?: throw RecipeMismatch("Fecha inválida")
        require(fullPosition.index == input.length)
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).apply {
            timeZone = zone(source.targetTimeZone, source.targetUtcOffsetMinutes)
        }
        val output = formatter.format(instant)
        return output.substringBefore(' ') to output.substringAfter(' ')
    }

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
