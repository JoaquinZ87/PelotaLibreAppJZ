package com.jz.pelotalibretv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Tema de la app basado en Compose for TV (androidx.tv.material3).
 * Esquema oscuro: es lo correcto para una TV en un living.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PelotaLibreTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}
