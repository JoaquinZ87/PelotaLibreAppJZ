package com.jz.pelotalibretv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jz.pelotalibretv.domain.model.AgendaState
import com.jz.pelotalibretv.domain.model.Event

/**
 * Modalidad "Eventos": la agenda de partidos del día (auto-refresca cada ~45s).
 * Al tocar un evento se abre su PRIMER servidor ([onPlay]). La elección de servidor
 * (cuando hay varios) queda para un paso siguiente.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AgendaContent(
    onPlayEvent: (Event) -> Unit,
    viewModel: AgendaViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            is AgendaState.Loading -> Message("Cargando agenda…")
            is AgendaState.Error -> Message(s.message)
            is AgendaState.Success ->
                if (s.events.isEmpty()) Message("No hay partidos en la agenda ahora mismo.")
                else EventList(s.events, s.stale, onPlayEvent)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Message(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.headlineSmall
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EventList(events: List<Event>, stale: Boolean, onPlayEvent: (Event) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (stale) {
            item {
                Text(
                    text = "· datos guardados (no se pudo actualizar) ·",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        items(events) { event ->
            EventRow(event = event, onClick = { onPlayEvent(event) })
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EventRow(event: Event, onClick: () -> Unit) {
    val hasSignal = event.servers.isNotEmpty()
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable { if (hasSignal) onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = if (event.time.isNotEmpty()) "${event.time}   ${event.title}" else event.title,
            color = fg,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = if (hasSignal) {
                event.servers.joinToString("   ·   ") {
                    if (it.quality.isNotEmpty()) "${it.name} (${it.quality})" else it.name
                }
            } else {
                "Sin señal disponible aún"
            },
            color = if (hasSignal) fg else fg.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
