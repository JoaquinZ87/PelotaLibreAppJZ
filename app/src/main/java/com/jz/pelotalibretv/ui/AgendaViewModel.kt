package com.jz.pelotalibretv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jz.pelotalibretv.data.AgendaRepository
import com.jz.pelotalibretv.data.AppConfig
import com.jz.pelotalibretv.data.RemoteConfig
import com.jz.pelotalibretv.domain.model.AgendaState
import com.jz.pelotalibretv.domain.model.Source
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Agenda de la fuente seleccionada. [setSource] (re)arranca el loop de auto-refresh (~45s) para
 * la fuente elegida, cancelando el anterior. NO WorkManager (piso de 15 min).
 */
class AgendaViewModel : ViewModel() {

    private val _state = MutableStateFlow<AgendaState>(AgendaState.Loading)
    val state: StateFlow<AgendaState> = _state.asStateFlow()

    private var job: Job? = null
    private var currentId: String? = null

    fun setSource(source: Source) {
        if (source.id == currentId) return
        currentId = source.id
        job?.cancel()
        _state.value = AgendaState.Loading
        val repository = AgendaRepository(source)
        job = viewModelScope.launch {
            while (isActive) {
                RemoteConfig.ensureFresh()
                _state.value = repository.loadAgenda()
                delay(AppConfig.refreshIntervalMs)
            }
        }
    }
}
