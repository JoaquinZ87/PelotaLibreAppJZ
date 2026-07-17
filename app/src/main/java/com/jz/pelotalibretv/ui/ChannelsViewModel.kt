package com.jz.pelotalibretv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jz.pelotalibretv.data.ChannelScraper
import com.jz.pelotalibretv.data.RemoteConfig
import com.jz.pelotalibretv.domain.model.Channel
import com.jz.pelotalibretv.domain.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Canales de la fuente seleccionada. Expone [loading] además de [channels] para distinguir
 * "cargando" de "vacío" (antes se quedaba en "Cargando…" para siempre si no había canales).
 */
class ChannelsViewModel : ViewModel() {

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var currentId: String? = null

    fun setSource(source: Source) {
        if (source.id == currentId) return
        currentId = source.id
        _channels.value = emptyList()
        _loading.value = source.channelsEnabled
        viewModelScope.launch {
            RemoteConfig.ensureFresh()
            _channels.value = ChannelScraper(source).fetchChannels()
            _loading.value = false
        }
    }
}
