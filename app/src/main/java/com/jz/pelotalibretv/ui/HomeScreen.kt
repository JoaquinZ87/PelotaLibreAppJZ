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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.jz.pelotalibretv.data.RemoteConfig
import com.jz.pelotalibretv.data.EmbedResolver
import com.jz.pelotalibretv.data.UpdateChecker
import com.jz.pelotalibretv.data.UpdateInfo
import com.jz.pelotalibretv.data.Updater
import com.jz.pelotalibretv.domain.model.Event
import com.jz.pelotalibretv.domain.model.Server
import com.jz.pelotalibretv.domain.model.Source
import kotlinx.coroutines.launch

/**
 * Pantalla principal MULTI-PLATAFORMA. Arriba: solapas de PLATAFORMA (Pelota Libre, Al Ángulo TV…).
 * Adentro, si la plataforma tiene varias variantes ("mirrors"), un selector por dominio para cambiar
 * entre frontends. Siempre se muestra la agenda (Eventos). Al elegir un evento con varias señales,
 * un selector de señal. Reproductor a pantalla completa. Orientación por dispositivo.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val isTv = remember { context.isTvDevice() }
    val activity = remember { context.findActivity() }
    val sources by RemoteConfig.sources.collectAsState()
    val configStatus by RemoteConfig.status.collectAsState()
    // Agrupar fuentes por PLATAFORMA (clave = platform, o name si viene vacío), preservando orden.
    // Cada plataforma es una solapa; sus variantes ("mirrors") se eligen adentro.
    val platforms = remember(sources) {
        val map = LinkedHashMap<String, MutableList<Source>>()
        sources.forEach { s -> map.getOrPut(s.platform.ifBlank { s.name }) { mutableListOf() } += s }
        map.map { it.key to it.value.toList() }
    }

    var selectedSource by remember { mutableStateOf(sources.first()) }
    val lastVariant = remember { mutableStateMapOf<String, Source>() } // última variante elegida por plataforma
    var playUrl by remember { mutableStateOf<String?>(null) }
    var playReferer by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf(false) }
    var serverPicker by remember { mutableStateOf<Event?>(null) }
    var activeEvent by remember { mutableStateOf<Event?>(null) }    // evento multi-señal en curso (para volver al selector con Atrás)
    var currentServer by remember { mutableStateOf<Server?>(null) } // señal en uso (para marcarla en el selector)
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(sources, playUrl, opening) {
        if (playUrl == null && !opening) {
            val replacement = sources.firstOrNull { it.id == selectedSource.id } ?: sources.first()
            if (replacement != selectedSource) {
                selectedSource = replacement
                lastVariant.clear()
                serverPicker = null
                activeEvent = null
            }
        }
    }

    // Chequeo de actualización al abrir.
    LaunchedEffect(Unit) {
        val vc = runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pi).toInt()
        }.getOrDefault(0)
        update = UpdateChecker.check(vc)
    }

    val agendaVM: AgendaViewModel = viewModel()

    // Cambiar de variante = recargar la agenda de esa fuente.
    LaunchedEffect(selectedSource) { agendaVM.setSource(selectedSource) }

    LaunchedEffect(playUrl, isTv) {
        activity?.requestedOrientation = when {
            playUrl != null -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            isTv -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_USER
        }
    }

    val currentUrl = playUrl
    if (currentUrl != null) {
        PlayerScreen(
            embedUrl = currentUrl,
            referer = playReferer,
            onBack = {
                playUrl = null
                // Si el evento tenía varias señales, Atrás vuelve al selector para cambiar de señal.
                val ev = activeEvent
                if (ev != null && ev.servers.size > 1) serverPicker = ev
            }
        )
        return
    }

    val agendaReferer = selectedSource.mirrors.first() + selectedSource.agendaPath

    // Reproduce un servidor. Familia B (needsResolve): baja la página de detalle y saca el iframe.
    fun playServer(server: Server) {
        currentServer = server
        if (server.needsResolve) {
            opening = true
            scope.launch {
                val url = EmbedResolver.resolveChannel(server.embedUrl, selectedSource.userAgent, recipe = selectedSource.resolverRecipe)
                    ?: server.embedUrl
                playReferer = server.embedUrl
                opening = false
                playUrl = url
            }
        } else {
            playReferer = server.referer.ifBlank { agendaReferer }
            playUrl = server.embedUrl
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val activePlatform = selectedSource.platform.ifBlank { selectedSource.name }
        val activeVariants = platforms.firstOrNull { it.first == activePlatform }?.second
            ?: listOf(selectedSource)
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)) {
                Chip("Actualizar fuentes", selected = false) { scope.launch { RemoteConfig.ensureFresh(force = true) } }
                Chip("Restaurar anterior", selected = false) { scope.launch { RemoteConfig.rollback() } }
            }
            Text(configStatus, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 48.dp))
            PlatformSelector(platforms.map { it.first }, activePlatform) { p ->
                val variants = platforms.firstOrNull { it.first == p }?.second
                if (variants != null) selectedSource = variants.firstOrNull { it.id == lastVariant[p]?.id } ?: variants.first()
            }
            if (activeVariants.size > 1) {
                VariantSelector(activeVariants, selectedSource) { v ->
                    lastVariant[activePlatform] = v
                    selectedSource = v
                }
            }
            AgendaContent(
                viewModel = agendaVM,
                onPlayEvent = { event ->
                    when {
                        event.servers.size == 1 -> { activeEvent = null; playServer(event.servers.first()) }
                        event.servers.size > 1 -> { activeEvent = event; serverPicker = event }
                    }
                }
            )
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
                current = currentServer,
                onPick = { server ->
                    serverPicker = null
                    playServer(server)
                },
                onDismiss = { serverPicker = null; activeEvent = null }
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
private fun PlatformSelector(platforms: List<String>, selected: String, onSelect: (String) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    val first = platforms.firstOrNull()

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(platforms) { p ->
            Chip(
                label = p,
                selected = p == selected,
                modifier = if (p == first) Modifier.focusRequester(firstFocus) else Modifier
            ) { onSelect(p) }
        }
    }
}

/** Selector de variantes ("mirrors") de la plataforma activa. Etiqueta = dominio. Centrado + divisor. */
@Composable
private fun VariantSelector(variants: List<Source>, selected: Source, onSelect: (Source) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            variants.forEach { v ->
                Chip(domainLabel(v), selected = v.id == selected.id) { onSelect(v) }
            }
        }
    }
}

/** Dominio (host) de la variante, para mostrar como etiqueta del mirror (ej "pelotalibrehd.su"). */
private fun domainLabel(source: Source): String =
    source.mirrors.firstOrNull().orEmpty()
        .substringAfter("://")
        .removePrefix("www.")
        .substringBefore("/")
        .ifBlank { source.name }

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

/**
 * Selector de señal: aparece cuando un evento tiene más de una. También es la pantalla a la que
 * vuelve el botón Atrás desde el reproductor (para cambiar de señal si una anda lenta). Marca la
 * que se está usando y arranca el foco sobre una alternativa.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServerPicker(event: Event, current: Server?, onPick: (Server) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    fun isCurrent(s: Server) = current != null && s.embedUrl == current.embedUrl && s.name == current.name
    // Foco inicial en la primera señal distinta a la que ya se está usando (para cambiar rápido).
    val focusIndex = event.servers.indexOfFirst { !isCurrent(it) }.let { if (it < 0) 0 else it }

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
                text = if (current != null) "Elegí o cambiá la señal:" else "Elegí una señal:",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            event.servers.forEachIndexed { i, server ->
                ServerButton(
                    server = server,
                    inUse = isCurrent(server),
                    modifier = if (i == focusIndex) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onPick(server) }
                )
                if (i < event.servers.size - 1) Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServerButton(server: Server, inUse: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        Text(
            text = if (inUse) "${server.name}   · en uso" else server.name,
            color = fg,
            style = MaterialTheme.typography.titleMedium
        )
        if (server.quality.isNotEmpty()) {
            Text(
                text = server.quality,
                color = fg.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
