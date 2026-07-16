package com.jz.pelotalibretv.ui

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jz.pelotalibretv.data.AppConfig
import com.jz.pelotalibretv.data.EmbedResolver
import com.jz.pelotalibretv.domain.model.Event
import com.jz.pelotalibretv.domain.model.Server
import kotlinx.coroutines.launch

private enum class Mode { CANALES, EVENTOS }

/**
 * Pantalla principal con las DOS modalidades: Canales (24/7) y Eventos (agenda del día).
 * - Orientación: reproduciendo = horizontal; navegando = TV horizontal / celular libre.
 * - Foco inicial en el selector (control remoto).
 * - Al elegir un evento con VARIAS señales, muestra un selector de servidor.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val isTv = remember { context.isTvDevice() }
    val activity = remember { context.findActivity() }

    var mode by remember { mutableStateOf(Mode.CANALES) }
    var playUrl by remember { mutableStateOf<String?>(null) }
    var playReferer by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf(false) }
    var serverPicker by remember { mutableStateOf<Event?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(playUrl, isTv) {
        activity?.requestedOrientation = when {
            playUrl != null -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            isTv -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_USER
        }
    }

    val currentUrl = playUrl
    if (currentUrl != null) {
        PlayerScreen(embedUrl = currentUrl, referer = playReferer, onBack = { playUrl = null })
        return
    }

    val agendaReferer = AppConfig.mirrors.first() + AppConfig.agendaPath

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ModeSelector(selected = mode, onSelect = { mode = it })
            when (mode) {
                Mode.CANALES -> ChannelsContent(
                    onOpenChannel = { channel ->
                        opening = true
                        scope.launch {
                            val url = EmbedResolver.resolveChannel(channel.pageUrl) ?: channel.pageUrl
                            playReferer = channel.pageUrl
                            opening = false
                            playUrl = url
                        }
                    }
                )
                Mode.EVENTOS -> AgendaContent(
                    onPlayEvent = { event ->
                        when {
                            event.servers.size == 1 -> {
                                playReferer = agendaReferer
                                playUrl = event.servers.first().embedUrl
                            }
                            event.servers.size > 1 -> serverPicker = event
                        }
                    }
                )
            }
        }

        if (opening) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Abriendo…",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        serverPicker?.let { ev ->
            ServerPicker(
                event = ev,
                onPick = { server ->
                    serverPicker = null
                    playReferer = agendaReferer
                    playUrl = server.embedUrl
                },
                onDismiss = { serverPicker = null }
            )
        }
    }
}

@Composable
private fun ModeSelector(selected: Mode, onSelect: (Mode) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ModeChip("Canales", selected == Mode.CANALES, Modifier.focusRequester(firstFocus)) {
            onSelect(Mode.CANALES)
        }
        ModeChip("Eventos", selected == Mode.EVENTOS, Modifier) {
            onSelect(Mode.EVENTOS)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary
        focused -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground

    Text(
        text = label,
        color = fg,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 28.dp, vertical = 10.dp)
    )
}

/** Selector de servidor: se muestra cuando un evento tiene más de una señal. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServerPicker(event: Event, onPick: (Server) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp)
        ) {
            Text(
                text = event.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Elegí una señal:",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            event.servers.forEachIndexed { i, server ->
                ServerButton(
                    server = server,
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onPick(server) }
                )
                if (i < event.servers.size - 1) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServerButton(server: Server, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(text = server.name, color = fg, style = MaterialTheme.typography.titleMedium)
        if (server.quality.isNotEmpty()) {
            Text(
                text = server.quality,
                color = fg.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
