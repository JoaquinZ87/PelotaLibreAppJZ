package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.AgendaState
import com.jz.pelotalibretv.domain.model.Event

/**
 * Orquesta la obtención de la agenda y guarda la última copia buena (cache en memoria).
 * Traduce el resultado del scraper a un AgendaState listo para la UI.
 * Distingue tres casos: hay partidos / no hay partidos ahora / no se pudo conectar.
 */
class AgendaRepository(
    private val scraper: AgendaScraper = AgendaScraper()
) {

    @Volatile
    private var lastGood: List<Event>? = null

    suspend fun loadAgenda(): AgendaState {
        val events = scraper.fetchAgenda()
        return when {
            // No se alcanzó ningún mirror: mostrar cache si hay, si no error.
            events == null -> lastGood?.let { AgendaState.Success(it, stale = true) }
                ?: AgendaState.Error("No se pudo conectar con la fuente (mirrors caídos).")

            // Se alcanzó la fuente pero no hay partidos en este momento.
            events.isEmpty() -> AgendaState.Success(emptyList(), stale = false)

            // Hay partidos: actualizar cache y devolver.
            else -> {
                lastGood = events
                AgendaState.Success(events, stale = false)
            }
        }
    }
}
