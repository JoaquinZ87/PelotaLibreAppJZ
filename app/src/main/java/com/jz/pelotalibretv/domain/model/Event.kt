package com.jz.pelotalibretv.domain.model

/**
 * Un partido/evento de la agenda del día, con sus servidores.
 * Campos "blandos" (time, category) pueden venir vacíos si el markup cambia:
 * el scraper degrada con gracia y nunca crashea por eso.
 */
data class Event(
    val title: String,
    val time: String,
    val category: String,
    val servers: List<Server>
)
