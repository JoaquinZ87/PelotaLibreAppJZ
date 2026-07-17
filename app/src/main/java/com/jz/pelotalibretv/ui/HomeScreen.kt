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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jz.pelotalibretv.data.AppConfig
import com.jz.pelotalibretv.data.EmbedResolver
import com.jz.pelotalibretv.data.UpdateChecker
import com.jz.pelotalibretv.data.UpdateInfo
import com.jz.pelotalibretv.data.Updater
import com.jz.pelotalibretv.domain.model.Event
import com.jz.pelotalibretv.domain.model.Server
import com.jz.pelotalibretv.domain.model.Source
import kotlinx.coroutines.launch

private enum class Mode { CANALES, EVENTOS }

/**
 * Pantalla principal MULTI-FUENTE. Arriba: selector de fuente (PelotaLibre, Fútbol Libre, AlÁngulo…)
 * y selector de modalidad (Canales/Eventos). Al elegir un evento con varias señales, un selector
 * de servidor. Reproductor a pantalla completa. Orientación por dispositivo.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val isTv = remember { context.isTvDevice() }
    val activity = remember { context.findActivity() }
    val sources = remember { AppConfig.sources }

    var selectedSource by remember { mutableStateOf(sources.first()) }
    var mode by remember {
        mutableStateOf(if (sources.first().channelsEnabled) Mode.CANALES else Mode.EVENTOS)
    }
    var playUrl by remember { mutableStateOf<String?>(null) }
    var playReferer by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf(false) }
    var serverPicker by remember { mutableStateOf<Event?>(null) }
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Chequeo de actualización al abrir.
    LaunchedEffect(Unit) {
        val vc = runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi).toInt()
        }.getOrDefault(0)
        update = UpdateChecker.check(vc)
    }

    val agendaVM: AgendaViewModel = viewModel()
    val channelsVM: ChannelsViewModel = viewModel()

    // Cambiar de fuente = recargar agenda y canales de esa fuente.
    LaunchedEffect(selectedSource) {
        agendaVM.setSource(selectedSource)
        channelsVM.setSource(selectedSource)
        if (!selectedSource.channelsEnabled) mode = Mode.EVENTOS
    }

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

    val agendaReferer = selectedSource.mirrors.first() + selectedSource.agendaPath

    // Reproduce un servidor. Familia B (needsResolve): baja la página de detalle y saca el iframe.
    fun playServer(server: Server) {
        if (server.needsResolve) {
            opening = true
            scope.launch {
                val url = EmbedResolver.resolveChannel(server.embedUrl, selectedSource.userAgent)
                    ?: server.embedUrl
                playReferer = server.embedUrl
                opening = false
                playUrl = url
            }
        } else {
            playReferer = agendaReferer
            playUrl = server.embedUrl
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SourceSelector(sources, selectedSource) { selectedSource = it }
            ModeSelector(mode, selectedSource.channelsEnabled) { mode = it }
            when (mode) {
                Mode.CANALES -> ChannelsContent(
                    viewModel = channelsVM,
                    onOpenChannel = { channel ->
                        opening = true
                        scope.launch {
                            val url = EmbedResolver.resolveChannel(channel.pageUrl, selectedSource.userAgent)
                                ?: channel.pageUrl
                            playReferer = channel.pageUrl
                            opening = false
                            playUrl = url
                        }
                    }
                )
                Mode.EVENTOS -> AgendaContent(
                    viewModel = agendaVM,
                    onPlayEvent = { event ->
                        when {
                            event.servers.size == 1 -> playServer(event.servers.first())
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
                Text("Abriendo…", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
        }

        serverPicker?.let { ev ->
            ServerPicker(
                event = ev,
                onPick = { server ->
                    serverPicker = null
                    playServer(server)
                },
                onDismiss = { serverPicker = null }
            )
        }

        update?.let { info ->
            UpdateDialog(
                info = info,
                updating = updating,
                onUpdate = {
                    updating = true
                    scope.launch {
                        val ok = Updater.downloadAndInstall(context, info.apkUrl)
                        if (!ok) updating = false
                    }
                },
                onDismiss = { update = null }
            )
        }
    }
}

/** Diálogo de "hay una versión nueva". */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    updating: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = { if (!updating) onDismiss() })
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
                text = "Actualización disponible",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Versión ${info.versionName}" +
                    if (info.notes.isNotBlank()) "\n\n${info.notes}" else "",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(20.dp))
            if (updating) {
                Text(
                    text = "Descargando…",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Chip("Actualizar", selected = true, modifier = Modifier.focusRequester(firstFocus)) { onUpdate() }
                    Chip("Ahora no", selected = false) { onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun SourceSelector(sources: List<Source>, selected: Source, onSelect: (Source) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    val firstId = sources.firstOrNull()?.id

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sources) { src ->
            Chip(
                label = src.name,
                selected = src.id == selected.id,
                modifier = if (src.id == firstId) Modifier.focusRequester(firstFocus) else Modifier
            ) { onSelect(src) }
        }
    }
}

@Composable
private fun ModeSelector(mode: Mode, channelsEnabled: Boolean, onSelect: (Mode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (channelsEnabled) Chip("Canales", mode == Mode.CANALES) { onSelect(Mode.CANALES) }
        Chip("Eventos", mode == Mode.EVENTOS) { onSelect(Mode.EVENTOS) }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Chip(
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
            .padding(horizontal = 24.dp, vertical = 8.dp)
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
