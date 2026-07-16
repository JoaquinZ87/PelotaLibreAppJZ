package com.jz.pelotalibretv.domain.model

/**
 * Estado de la agenda para la UI. Se expone como StateFlow.
 * Regla: nunca se tira una excepción al hilo de UI; todo error es un estado.
 */
sealed interface AgendaState {

    /** Primera carga en curso (sin datos previos). */
    data object Loading : AgendaState

    /**
     * Hay eventos para mostrar.
     * [stale] = true cuando son datos cacheados porque la última actualización falló.
     */
    data class Success(
        val events: List<Event>,
        val stale: Boolean = false
    ) : AgendaState

    /** No se pudo cargar y no hay cache (o se muestra [cached] si existe). */
    data class Error(
        val message: String,
        val cached: List<Event>? = null
    ) : AgendaState
}
