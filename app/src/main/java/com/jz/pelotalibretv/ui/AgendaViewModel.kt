package com.jz.pelotalibretv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jz.pelotalibretv.data.AgendaRepository
import com.jz.pelotalibretv.data.AppConfig
import com.jz.pelotalibretv.data.RemoteConfig
import com.jz.pelotalibretv.domain.model.AgendaState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Expone la agenda como StateFlow y la auto-refresca cada ~45s mientras vive la pantalla.
 * NO usa WorkManager (piso de 15 min, inútil para eventos en vivo).
 */
class AgendaViewModel : ViewModel() {

    private val repository = AgendaRepository()

    private val _state = MutableStateFlow<AgendaState>(AgendaState.Loading)
    val state: StateFlow<AgendaState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                RemoteConfig.ensureFresh()
                _state.value = repository.loadAgenda()
                delay(AppConfig.refreshIntervalMs)
            }
        }
    }
}
