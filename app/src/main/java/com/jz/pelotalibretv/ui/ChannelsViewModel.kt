package com.jz.pelotalibretv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jz.pelotalibretv.data.ChannelScraper
import com.jz.pelotalibretv.data.RemoteConfig
import com.jz.pelotalibretv.domain.model.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Carga la lista de canales 24/7 una vez (siempre disponibles, cambian muy poco).
 * El ChannelScraper garantiza una lista no vacía (cae a defaults si el scrape falla).
 */
class ChannelsViewModel : ViewModel() {

    private val scraper = ChannelScraper()

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    init {
        viewModelScope.launch {
            RemoteConfig.ensureFresh()
            _channels.value = scraper.fetchChannels()
        }
    }
}
