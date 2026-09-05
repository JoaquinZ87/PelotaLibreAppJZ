package com.jz.pelotalibretv.data

import com.jz.pelotalibretv.domain.model.AgendaState
import com.jz.pelotalibretv.domain.model.Event
import com.jz.pelotalibretv.domain.model.Source

/**
 * Orquesta la agenda de una [Source]: cachea la última copia buena y traduce a AgendaState.
 * hay partidos / no hay partidos ahora / no se pudo conectar.
 */
class AgendaRepository(source: Source) {

    private val sourceId = source.id
    companion object {
        private val cache = java.util.Collections.synchronizedMap(LinkedHashMap<String, List<Event>>())
    }

    private val scraper = AgendaScraper(source)

    @Volatile
    private var lastGood: List<Event>? = cache[sourceId]

    suspend fun loadAgenda(): AgendaState {
        val events = try { scraper.fetchAgenda() } catch (error: RecipeMismatch) {
            return lastGood?.let { AgendaState.Success(it, stale = true) }
                ?: AgendaState.Error("La fuente cambió de formato. Revisar su receta.")
        }
        return when {
            events == null -> lastGood?.let { AgendaState.Success(it, stale = true) }
                ?: AgendaState.Error("No se pudo conectar con la fuente.")
            events.isEmpty() -> {
                lastGood = emptyList()
                cache[sourceId] = emptyList()
                AgendaState.Success(emptyList(), stale = false)
            }
            else -> {
                lastGood = events
                synchronized(cache) {
                    if (cache.size >= 100 && sourceId !in cache) cache.remove(cache.keys.first())
                    cache[sourceId] = events
                }
                AgendaState.Success(events, stale = false)
            }
        }
    }
}
