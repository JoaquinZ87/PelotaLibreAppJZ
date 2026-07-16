# CLAUDE.md — PelotaLibre TV (uso estrictamente personal)

App **Android TV** (Kotlin nativo) para ver la agenda de streams de "pelota libre" en un TV box,
manejada 100% con el **control remoto (D-pad)**. Debe ser **mega simple de usar**: prender, ver la
grilla de hoy, apretar OK, mirar a pantalla completa.

> Este archivo es la fuente de verdad del proyecto. **Mantenerlo vivo**: actualizar el estado de los
> módulos y las decisiones cada vez que cambie algo relevante.

---

## 0. Realidad crítica (leer antes de tocar nada)

1. **Uso personal.** Contenido no licenciado. **Nunca** publicar en Play Store ni distribuir el APK.
   Sideload propio solamente. Diseñar para fallar con gracia si la fuente desaparece.
2. **La fuente es HOSTIL y cambia sola.** Verificado en jul/2026:
   - **Rota de dominio** (semanal o más rápido): `librepelota.su`, `pelotalibrehd.su`,
     `librepelotatv.net`, `pelotalibre.watch`, etc. Algunos aparecen **incautados**
     ("THIS DOMAIN HAS BEEN SEIZED") por bloqueo judicial dinámico (Operación Tarjeta Roja).
   - **Cloudflare** delante de todos los dominios; puede activar el challenge JS en horario pico.
   - **Reskins de HTML**: las clases CSS cambian entre versiones/mirrors.
3. **El USO es simple; el MANTENIMIENTO no.** Esto **NO es "set-and-forget"**. Esperar retoques
   mensuales + cortes duros cuando incautan un dominio. Todo el diseño apunta a **minimizar** ese
   mantenimiento con **configuración remota** (ver Módulo 7): que la mayoría de los cambios se
   arreglen editando un JSON, sin recompilar.

**Regla de oro de código:** NADA de dominios, hosts ni selectores CSS hardcodeados en el binario.
Todo lo volátil vive en config (local con defaults, sobreescribible por JSON remoto).

---

## 1. Arquitectura (HÍBRIDA)

Lista nativa (scrape) + reproducción en **WebView blindado (default)** con **player nativo Media3
opcional** (fast-path con fallback automático).

```
[ SourceResolver ] --prueba mirrors--> [ Scraper (OkHttp+Jsoup) ] --parse--> [ AgendaState ]
        |  (lista de dominios, remota)          | key = href "eventos.html?r=BASE64"      |
        |                                       | cache last-good, sealed states          v
        |                                                                        [ UI grilla TV ]
        |                                                                          (Compose for TV,
        |                                                                           D-pad focus)
        v  al elegir servidor                                                            |
[ EmbedDecoder ] base64(r=) -> embed URL (ej vidzenvivo.cc/canal.php?stream=X)           |
        |                                                                                v
        +--------------------------------> [ PlayerScreen ] --default--> WebView blindado (Clappr/HLS)
                                                          \--opt-in---> Media3 ExoPlayer (sniff m3u8)
                                                                         \-- fallback auto --> WebView
```

Capas: `data/` (SourceResolver, Scraper, CloudflareHelper, repos, config) · `domain/` (modelos:
Event, Server, AgendaState) · `ui/` (grilla, player) · `update/` (UpdateChecker).

---

## 2. Stack y versiones

> **Versiones REALES pineadas** en `gradle/libs.versions.toml` (conservadoras y probadas).
> Son fáciles de subir: se toca solo ese archivo. Si el sync marca una versión inexistente,
> actualizala ahí.

- **Lenguaje/UI:** Kotlin **2.2.10**, Jetpack **Compose for TV** (`androidx.tv:tv-material` **1.0.0**),
  Compose BOM **2024.12.01**, plugin `org.jetbrains.kotlin.plugin.compose`.
  - Usar **Foundation lazy layouts estándar** (`LazyVerticalGrid`, `LazyRow`) — **NO** las
    `tv-foundation` lazy (deprecadas). **NO** Leanback (legacy para apps nuevas).
  - Componentes de `androidx.tv.material3` requieren `@OptIn(ExperimentalTvMaterial3Api::class)`.
- **Reproducción nativa (M5):** Media3 ExoPlayer (`media3-exoplayer`, `media3-exoplayer-hls`,
  `media3-ui`) — se agrega recién en el M5. HLS sin DRM, sin config extra.
- **Red/scrape:** OkHttp **4.12.0** (cliente único, CookieJar persistente, UA de browser) + Jsoup **1.18.1**.
- **Async:** Coroutines **1.9.0**, MVVM, `StateFlow`. Dispatcher **inyectado** (no hardcodear `Dispatchers.IO`).
- **Build:** compileSdk **35**, targetSdk **35**, **minSdk 23**. `applicationId` = `namespace` =
  **`com.jz.pelotalibretv`**. **AGP 9.3.0 + Gradle 9.6.1 + Kotlin 2.2.10** (Android Studio Q2-2026 los
  auto-actualiza al abrir el proyecto; dejar lo que ponga).
- **JDK:** usar el bundled de Android Studio (**JBR = JDK 21**, en `…\Android Studio\jbr`). El Java 8
  del sistema NO sirve. VS Code apunta a ese JBR vía `.vscode/settings.json` (si no, su servidor de
  Gradle falla con `--add-opens / Could not create the Java Virtual Machine`).
- **Warnings esperados (NO son errores):** con AGP 8.13 sobre Gradle 9.6 salen decenas de avisos de
  deprecación (`android.newDsl`, `applicationVariants`, `usesSdkInManifest.disallowed`, etc.). Son de
  la transición hacia AGP 9/10 y no rompen el build. Kotlin usa la DSL `compilerOptions` (no
  `kotlinOptions`). Si molestan, se pueden silenciar con `android.sync.suppressAgpWarnings=...`.

### Manifest Android TV (imprescindible)
- `<uses-feature android:name="android.software.leanback" android:required="false"/>`
- `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>` ← **obligatorio**
  o la app no aparece en el launcher de TV.
- Activity launcher con `<category android:name="android.intent.category.LEANBACK_LAUNCHER"/>`.
- `android:banner="@drawable/banner"` **320x180** con el nombre de la app como texto.
- `<uses-permission android:name="android.permission.INTERNET"/>`.
- Para el auto-update: `REQUEST_INSTALL_PACKAGES`. Cleartext puede requerirse para embeds/HLS por HTTP.

---

## 3. Mecánica del sitio (confirmada jul/2026)

- **Dos fuentes de streams:**
  1. **Eventos (agenda del día):** en `/es/agenda/` (la home la embebe en un `<iframe>`). Es
     **HTML plano del servidor** (`ul.menu > li.CATEGORIA`), **sin Cloudflare ni JS** para la lista:
     un GET normal con User-Agent de browser alcanza. **Si NO hay partidos, el `<ul class="menu">`
     viene vacío** (solo el encabezado con la fecha) → eso NO es error, es "no hay eventos ahora".
     `smallscripts.js` es solo utilidades (reloj/zona horaria/popups), no trae datos.
  2. **Canales (24/7):** en la home `/es/`, en `div.cards-container > div.card` (nombre en `h3`,
     logo `.webp` en `cdn.<dominio>`, link `a.btn-watch` → `/es/<slug>/`). Siempre disponibles.
     La página del canal `/es/<slug>/` tiene el embed (a resolver en el M3, igual que un evento).
- Cada evento tiene 1+ **servidores** como
  `<a href=".../eventos.html?r=BASE64">Nombre<span>Calidad</span></a>`.
- **Horas en UTC+1 (fijo), NO en la zona del server.** El `<span class="t">` viene en UTC+1; la web
  lo convierte a la zona del visitante por JS (`smallscripts.js`). La app hace lo mismo con
  `TimeConverter` (`AppConfig.sourceUtcOffsetMinutes`, ajustable por RemoteConfig). Verificado
  jul/2026: 22:45 UTC+1 → 18:45 en Argentina (UTC-3).
- **Eventos SIN señal aún:** un evento puede venir con el `<ul>` de servidores **vacío** (todavía no
  le asignaron stream). Se PARSEA igual y se muestra como "sin señal disponible aún"; cuando le
  asignan el `?r=`, aparece jugable solo por el auto-refresh. (No filtrar los eventos sin servidor.)
- **`r=` es base64** del embed real. Ej: `aHR0cHM6Ly...` → `https://vidzenvivo.cc/canal.php?stream=dsports`.
- El embed corre **Clappr sobre HLS (.m3u8)**, **sin DRM** (a lo sumo AES-128 clear-key, que
  ExoPlayer/hls.js manejan solos). La protección real es **Referer/Origin** (a veces token/cookie corto).

**PARSEAR POR EL PATRÓN `href` `eventos.html?r=`, NO por clases CSS.** Las clases cambian por mirror:
una versión usa `ul.menu / li.subitem1 / span.t`; otra usa `submenu-item / channel / match-item /
schedule`. El patrón `?r=<base64>` es lo estable. Validar que el base64 decodifica a un `http(s)://`.

---

## 4. Decisiones firmes (salidas de la verificación adversarial)

- **Playback default = WebView.** El player nativo Media3 es **opt-in con fallback automático**, no el
  principal. Motivo: la protección de estos hosts (JWT en cookie HTTP-only invisible en la captura,
  tokens que rotan por minuto en vivo, IP-binding) hace que el replay nativo sea "moneda al aire" por
  stream. El WebView "just works" porque el browser manda Referer/Origin/cookies solo.
- **Autoplay:** arrancar **MUTEADO** (Chromium nunca bloquea muted autoplay) y **desmutear en el primer
  OK (DPAD_CENTER)**. Interceptar ese primer OK con un keymap propio para que **no** dispare el
  pop-under "abre ad al primer click" (el fallo #1 de UX en TV).
- **Popups:** apilar TODAS las defensas — multiple-windows OFF, `onCreateWindow`→false,
  **whitelist** de host en `shouldOverrideUrlLoading`, **blocklist** angosta de ads/trackers en
  `shouldInterceptRequest` (que nunca bloquee el m3u8/CDN).
- **Scraper:** dominio **nunca** hardcodeado (mirror list, ver M1/M7). **cf_clearance** vía WebView
  oculto desde el arranque (no "solo si aparece 403"). Cache **last-good**. Parseo por-item aislado
  con `selectFirst()?.` en todo → un reskin degrada a lista vacía/stale, no a crash.
- **Refresh en vivo:** loop coroutine **~45s en foreground** (`viewModelScope`), **NO** WorkManager
  (piso de 15 min, inútil para vivo).
- **v2 nativo — gate de aceptación:** solo hacerlo default si sobrevive **15-20 min de vivo continuo
  sin 403** en los servidores reales. Si 403ea a mitad, queda experimental y el WebView lleva el peso.

---

## 5. Módulos y estado

Marcar `[x]` al completar. v1 usable = M0→M3 (+M4 en la práctica).

- [x] **M0 — Andamiaje.** Proyecto single-activity Compose for TV, manifest TV completo, banner,
  build config (catálogo de versiones), deps. **VERIFICADO (jul/2026):** compila y corre en emulador
  mostrando "Cargando agenda…". Falta probarlo en el box físico (mismo APK, debería andar igual).
- [~] **M1 — Núcleo de datos resiliente + 2 modalidades.** Mirror list en `AppConfig` (prueba en
  orden) + `SiteHttp` (OkHttp, UA browser) + `AgendaScraper` y `ChannelScraper` (Jsoup, parseo por
  `?r=`, decode base64, aislado por item) + `AgendaRepository` (cache last-good, sealed `AgendaState`:
  hay/nada-ahora/sin-conexión) + `AgendaViewModel` (auto-refresh 45s) + `ChannelsViewModel`.
  **UI con 2 modalidades** (`HomeScreen`: selector Canales/Eventos). *Falta:* verificar en emulador
  (Canales con tarjetas, Eventos = "no hay partidos") y sumar logos de canales (Coil).
  **v1 = Canales + Eventos** (el usuario pidió las dos formas de selección).
- [x] **M2 — Grilla TV.** Canales en `LazyVerticalGrid` adaptable + eventos en `LazyColumn`, focus
  D-pad, foco inicial (en el selector), borde de foco visible, estados carga/lista/vacío-error.
  **VERIFICADO en emulador de TV (jul/2026): el control remoto navega bien.**
- [~] **M3 — Reproductor WebView blindado (corazón de v1).** *Hecho (v1):* `PlayerScreen` (WebView
  fullscreen) carga el embed con `Referer`, autoplay ON (`mediaPlaybackRequiresUserGesture=false`),
  popups off (`onCreateWindow=false`, multiple-windows off), bloquea navegación a otro host (ads),
  mixed-content ON + `usesCleartextTraffic`, Back cierra. `EmbedResolver` saca el `<iframe>` del canal
  (canal.php). *Falta:* autoplay muted + unmute en primer OK + keymap D-pad; CSS/JS para aislar el
  `<video>` y matar overlays; elegir servidor cuando hay varios. *Done:* ver un canal a pantalla
  completa sin que el primer OK abra un ad.
- [ ] **M4 — Fallback Cloudflare.** Ante 403/503/challenge, resolver en WebView oculto, cosechar
  `cf_clearance` (CookieManager) → OkHttp; persistir y refrescar. *Done:* la agenda carga aún con
  challenge activo.
- [ ] **M5 — (Opcional) Player nativo Media3.** Sniff `.m3u8` (WebView oculto + `shouldInterceptRequest`)
  + Referer/Origin/UA + cookies; re-resolver al play; `Player.Listener` → fallback auto a WebView en
  401/403; toggle manual Nativo/Web. Pasar el gate de aceptación (§4). *Done:* donde se puede, reproduce
  nativo sin ads; si no, cae solo a WebView.
- [ ] **M6 — Distribución + auto-update.** Keystore propio (reusar siempre); `assembleRelease` firmado;
  hospedar `app-release.apk` + `version.json` (GitHub Releases); instalar via Downloader (AFTVnews) o adb;
  chequeo de versión al abrir → PackageInstaller. *Done:* publico versión nueva y el box la ofrece/instala.
- [x] **M7 — Reconfiguración remota (lo que evita recompilar).** `RemoteConfig` baja
  `https://raw.githubusercontent.com/JoaquinZ87/pelotalibretv-config/main/config.json` al abrir y PISA
  `AppConfig` (`mirrors`, `agendaPath`, `homePath`, `userAgent`, ahora `var`). Cachea el último bueno
  en SharedPreferences (sobrevive offline). `ensureFresh()` corre 1 vez/arranque desde los ViewModels.
  **JSON vivo y verificado (jul/2026).** Para arreglar una mudanza: editar `mirrors` en ese repo.
  *Pendiente opcional:* sumar selectores/blocklist al JSON.

---

## 6. Convenciones de código

- Kotlin idiomático, MVVM, coroutines; funciones de red/scrape `suspend` + `withContext(ioDispatcher)`.
- Resultados como **sealed** (`AgendaState`: Loading/Success(stale)/Error(cached)). Nunca throw al hilo UI.
- Parseo defensivo: `selectFirst()?.` + `orEmpty()` + `continue`; cada evento/servidor aislado.
- Config sobre constantes: dominios, patrones, blocklist y UA salen de `RemoteConfig`/`AppConfig`, no de literales.
- TV: todo elemento navegable `focusable` con indicador visible; algo debe tomar el foco inicial o el
  control "no responde".

---

## 7. Comandos

```powershell
# Build (Windows). IMPORTANTE: JAVA_HOME al JBR de Android Studio (JDK 21).
# El build sale FUERA de Drive (layout.buildDirectory -> C:\tmp\pelotalibretv-build) para
# evitar locks. Si el clean/merge falla: .\gradlew.bat --stop  y recompilar.
.\gradlew.bat assembleRelease            # APK firmado en C:\tmp\pelotalibretv-build\outputs\apk\release\

# Instalar en el box por red (una vez habilitada la depuración por red en el TV)
adb connect <IP_DEL_BOX>:5555
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell monkey -p <applicationId> -c android.intent.category.LEANBACK_LAUNCHER 1   # lanzar
adb logcat -s PelotaLibre                # logs de la app
```

Distribución sin PC: subir el APK a GitHub Releases y bajarlo en el box con la app **Downloader** (AFTVnews).

---

## 8. Gotchas confirmados

- Sin `touchscreen required=false` → la app **no aparece** en el launcher de TV (error #1).
- Banner debe ser exactamente **320x180** y contener el nombre como texto, o el tile sale vacío.
- WebView **roba el foco** y se come el D-pad: reenviar KeyEvents con `super.dispatchKeyEvent` (los
  eventos JS sintéticos por `evaluateJavascript` son "untrusted" y no manejan controles nativos).
- `onShowCustomView` sin `onCustomViewHidden()` en `onHideCustomView` **traba** el player al salir de fullscreen.
- Body de OkHttp es one-shot: siempre `.use { }`.
- Base64 del `r=` es **URL-safe**, puede venir sin padding → decodificar con `URL_SAFE or NO_PADDING` en try/catch.
- `WebResourceRequest.getRequestHeaders()` **no** trae la Cookie (WebView la inyecta después) → para el
  path nativo hay que leerla aparte con `CookieManager.getInstance().getCookie(url)`.
- ExoPlayer falla **duro** en 403 (retry ~4x y muere), no degrada → siempre tener el fallback a WebView.
- Play Protect puede avisar "app no segura" al instalar → Detalles → Instalar igual (esperado en sideload).

---

## 9. Fuera de alcance en v1 (backlog)

Categorías/filtros, buscador, favoritos, historial/continuar viendo, multi-idioma. Se suman recién
después de que M0→M4 estén sólidos. v1 = **una** grilla de hoy que se auto-refresca + reproductor.
