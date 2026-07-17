package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.AgendaState
import com.jz.pelotalibretv.domain.model.Event
import com.jz.pelotalibretv.domain.model.Source

/**
 * Orquesta la agenda de una [Source]: cachea la última copia buena y traduce a AgendaState.
 * hay partidos / no hay partidos ahora / no se pudo conectar.
 */
class AgendaRepository(source: Source) {

    private val scraper = AgendaScraper(source)

    @Volatile
    private var lastGood: List<Event>? = null

    suspend fun loadAgenda(): AgendaState {
        val events = scraper.fetchAgenda()
        return when {
            events == null -> lastGood?.let { AgendaState.Success(it, stale = true) }
                ?: AgendaState.Error("No se pudo conectar con la fuente.")
            events.isEmpty() -> AgendaState.Success(emptyList(), stale = false)
            else -> {
                lastGood = events
                AgendaState.Success(events, stale = false)
            }
        }
    }
}
