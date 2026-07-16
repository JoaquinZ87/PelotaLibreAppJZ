package com.jz.pelotalibretv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jz.pelotalibretv.data.RemoteConfig
import com.jz.pelotalibretv.ui.HomeScreen
import com.jz.pelotalibretv.ui.theme.PelotaLibreTvTheme

/**
 * Única Activity de la app (single-activity + Compose).
 * Muestra HomeScreen con las dos modalidades: Canales (24/7) y Eventos (agenda).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RemoteConfig.init(applicationContext) // carga la config remota cacheada (M7)
        setContent {
            PelotaLibreTvTheme {
                HomeScreen()
            }
        }
    }
}
