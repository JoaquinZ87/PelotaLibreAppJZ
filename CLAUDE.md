# CLAUDE.md — PelotaLibre TV (uso estrictamente personal)

App **Android TV** (Kotlin nativo) para ver agendas de streams de "pelota libre" en un TV box,
manejada 100% con el **control remoto (D-pad)**. Debe ser **mega simple de usar**: prender, elegir
fuente, ver la grilla, apretar OK, mirar a pantalla completa. **También corre en celular** (se
detecta el dispositivo y se adapta orientación y forma de arrancar el player).

> Este archivo es la fuente de verdad del proyecto. **Mantenerlo vivo**: actualizar el estado de los
> módulos y las decisiones cada vez que cambie algo relevante.
>
> Última revisión contra el código: **sept/2026** (v0.9, `versionCode 9`). Ver §3.bis para el motor de
> recipes + el arreglo del "Player privado" (capo8play) que hizo ChatGPT.

---

## 0. Realidad crítica (leer antes de tocar nada)

1. **Uso personal.** Contenido no licenciado. **Nunca** publicar en Play Store ni distribuir el APK.
   Sideload propio solamente. Diseñar para fallar con gracia si la fuente desaparece.
2. **Las fuentes son HOSTILES y cambian solas.** Verificado en jul/2026:
   - **Rotan de dominio** (semanal o más rápido): `librepelota.su`, `pelotalibrehd.su`,
     `librepelotatv.net`, `pelotalibre.watch`, `mirusticotv.su`, etc. Algunos aparecen **incautados**
     ("THIS DOMAIN HAS BEEN SEIZED") por bloqueo judicial dinámico (Operación Tarjeta Roja).
   - **Cloudflare** delante de varios dominios; puede activar el challenge JS en horario pico.
   - **Reskins de HTML**: las clases CSS cambian entre versiones/mirrors.
   - **Certificados rotos**: varios sitios sirven la cadena TLS incompleta (ver §4, SSL laxo).
   - **Redirecciones raras**: algunos dominios devuelven un stub HTML que redirige por
     `<meta refresh>` o `window.location` en vez de un 301.
3. **El USO es simple; el MANTENIMIENTO no.** Esto **NO es "set-and-forget"**. Esperar retoques
   mensuales + cortes duros cuando incautan un dominio. Todo el diseño apunta a **minimizar** ese
   mantenimiento con **configuración remota** (ver Módulo 7): que la mayoría de los cambios se
   arreglen editando un JSON, sin recompilar.
4. **Redundancia por fuentes.** Desde la v0.2 la app es **multi-fuente**: si un sitio cae o lo
   incautan, se cambia de fuente con el D-pad y se sigue mirando. Esa es la principal defensa.

**Regla de oro de código:** NADA de dominios, hosts ni selectores CSS hardcodeados en el binario.
Todo lo volátil vive en config (local con defaults, sobreescribible por JSON remoto). Las **dos
únicas** URLs hardcodeadas son las de GitHub: `RemoteConfig.CONFIG_URL` y `UpdateChecker.VERSION_URL`.

---

## 1. Arquitectura (HÍBRIDA, MULTI-FUENTE)

Lista nativa (scrape) + reproducción en **WebView blindado (default)** con **player nativo Media3
opcional** (no implementado todavía, ver M5).

```
[ AppConfig.sources ]  <--pisa--  [ RemoteConfig ]  <--  config.json (GitHub raw)
   List<Source>: platform, mirrors, paths, selectores, husos, strategy
        |
        |  UI de 2 niveles: solapa = PLATAFORMA (agrupa por Source.platform);
        |  adentro, VariantSelector elige la variante ("mirror") por dominio.
        |  (HomeScreen: PlatformSelector -> VariantSelector -> agenda)
        v
   +----+--------------------------------------------------+
   |                                                        |
[ AgendaScraper(source) ]                          [ ChannelScraper(source) ]
   strategy "menuR" -> ul.menu > li + ?r=BASE64       div.card -> nombre/logo/link
   strategy "rows"  -> fila -> link a detalle              |
        |  (prueba mirrors en orden vía SiteHttp)          |
        v                                                  v
[ AgendaRepository ] -> sealed AgendaState            [ ChannelsViewModel ]
   cache last-good                                         |
        v                                                  |
[ AgendaViewModel ] auto-refresh 45s                       |
        |                                                  |
        v                                                  v
              [ UI Compose for TV: HomeScreen ]
        PlatformSelector · VariantSelector (variantes/mirrors por dominio) · agenda · ServerPicker
        (el toggle Canales/Eventos se sacó por ahora; ChannelsScreen/VM quedan dormidos)
                          |
                          v  al elegir un servidor / canal
   needsResolve? --sí--> [ EmbedResolver ] baja la página y saca el <iframe>
                --no--> [ EmbedDecoder ] base64(r=) -> embed (ej vidzenvivo.cc/canal.php?stream=X)
                          |
                          v
              [ PlayerScreen ] WebView blindado (Clappr/HLS) a pantalla completa
```

Capas y archivos reales:

- `data/` — `AppConfig` (fuentes por defecto), `RemoteConfig` (M7), `SiteHttp` (OkHttp único, SSL
  laxo, redirecciones meta/JS), `AgendaScraper`, `ChannelScraper`, `AgendaRepository`,
  `EmbedDecoder`, `EmbedResolver`, `TimeConverter`, `UpdateChecker`, `Updater`.
- `domain/model/` — `Source`, `Event`, `Server`, `Channel`, `AgendaState`.
- `ui/` — `HomeScreen` (selector de fuente + modalidad + ServerPicker + diálogo de update),
  `AgendaScreen`/`AgendaViewModel`, `ChannelsScreen`/`ChannelsViewModel`, `PlayerScreen`,
  `PlatformUtils` (`isTvDevice()`, `findActivity()`), `theme/`.

> No existen (todavía) `SourceResolver` ni `CloudflareHelper`: la prueba de mirrors vive **dentro**
> de cada scraper (recorre `source.mirrors` en orden) y el fallback de Cloudflare es el M4 pendiente.

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
- **Red/scrape:** OkHttp **4.12.0** (cliente único en `SiteHttp`, UA de browser) + Jsoup **1.18.1**.
- **Imágenes:** Coil **2.7.0** (`coil-compose`) para los logos de canales (`AsyncImage`).
- **Async:** Coroutines **1.9.0**, MVVM, `StateFlow`. Dispatcher **inyectado** (no hardcodear `Dispatchers.IO`).
- **Reproducción nativa (M5, NO agregada aún):** Media3 ExoPlayer (`media3-exoplayer`,
  `media3-exoplayer-hls`, `media3-ui`). HLS sin DRM, sin config extra.
- **Build:** compileSdk **35**, targetSdk **35**, **minSdk 23**. `applicationId` = `namespace` =
  **`com.jz.pelotalibretv`**. Versión actual: `versionCode 9` / `versionName "0.9"`.
  **AGP 9.3.0 + Kotlin 2.2.10** (Android Studio Q2-2026 auto-actualiza AGP/Gradle al abrir el
  proyecto; dejar lo que ponga y reflejarlo acá).
- **Java:** `sourceCompatibility`/`targetCompatibility` = **17** y `jvmTarget = 17` (DSL nueva
  `kotlin { compilerOptions { … } }`, no `kotlinOptions`). Para **correr Gradle** usar el JDK
  bundled de Android Studio (**JBR = JDK 21**, en `…\Android Studio\jbr`). El Java 8 del sistema NO
  sirve. VS Code apunta a ese JBR vía `.vscode/settings.json` (si no, su servidor de Gradle falla
  con `--add-opens / Could not create the Java Virtual Machine`).
- **Build fuera de Drive:** `layout.buildDirectory` → `C:/tmp/pelotalibretv-build` (Drive
  sincroniza/bloquea `build/` y rompe el merge de recursos y el clean).
- **Firma:** `keystore.properties` + `pelotalibre-keystore.jks` en la raíz (**fuera de git**). El
  buildType `release` firma solo si ese archivo existe. `isMinifyEnabled = false`.
- **Warnings esperados (NO son errores):** con AGP sobre Gradle 9.x salen decenas de avisos de
  deprecación (`android.newDsl`, `applicationVariants`, `usesSdkInManifest.disallowed`, etc.). Son de
  la transición hacia AGP 10 y no rompen el build. Si molestan, se pueden silenciar con
  `android.sync.suppressAgpWarnings=...`.

### Manifest Android TV (imprescindible)
- `<uses-feature android:name="android.software.leanback" android:required="false"/>`
- `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>` ← **obligatorio**
  o la app no aparece en el launcher de TV.
- Activity launcher con `<category android:name="android.intent.category.LEANBACK_LAUNCHER"/>`
  **y** `LAUNCHER` (para que también aparezca en un box Android normal y en el celular).
- `android:banner="@drawable/banner"` **320x180** con el nombre de la app como texto.
- Permisos: `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES` (auto-update).
- `android:usesCleartextTraffic="true"` (embeds/HLS por HTTP).
- **Sin `screenOrientation` fija**: la orientación la setea el código (celular vertical al navegar /
  horizontal al reproducir; TV siempre horizontal). `configChanges` incluye `orientation|screenSize|…`.
- `FileProvider` con authority `${applicationId}.fileprovider` + `@xml/file_paths` (auto-update).

---

## 3. Mecánica de los sitios (confirmada jul/2026)

Hay **dos familias** de sitio, distinguidas por `Source.strategy`:

### Familia A — `strategy = "menuR"` (PelotaLibre, Fútbol Libre, AlÁngulo 1 y 2, Rústico)

- **Dos fuentes de streams:**
  1. **Eventos (agenda del día):** en `agendaPath` (`/es/agenda/`, `/agenda/`, `/agenda2/`,
     `/agenda.php` según el sitio). Es **HTML plano del servidor** (`ul.menu > li.CATEGORIA`),
     **sin Cloudflare ni JS** para la lista: un GET normal con User-Agent de browser alcanza.
     **Si NO hay partidos, el `<ul class="menu">` viene vacío** (solo el encabezado con la fecha) →
     eso NO es error, es "no hay eventos ahora". `smallscripts.js` es solo utilidades
     (reloj/zona horaria/popups), no trae datos.
  2. **Canales (24/7):** en `homePath`, en `div.cards-container > div.card` (nombre en `h3`, logo
     `.webp` en `cdn.<dominio>`, link `a.btn-watch` → `/es/<slug>/`). **Solo algunas fuentes los
     tienen usables** → flag `channelsEnabled` por fuente (hoy: PelotaLibre y Fútbol Libre en true;
     AlÁngulo y Rústico en false porque sus tarjetas linkean a `/` y no reproducen).
- Cada evento tiene 0+ **servidores** como
  `<a href=".../eventos.html?r=BASE64">Nombre<span>Calidad</span></a>`.
- **`r=` es base64** del embed real. Ej: `aHR0cHM6Ly...` → `https://vidzenvivo.cc/canal.php?stream=dsports`.

**PARSEAR POR EL PATRÓN `href` `?r=`, NO por clases CSS.** Las clases cambian por mirror: una versión
usa `ul.menu / li.subitem1 / span.t`; otra usa `submenu-item / channel / match-item / schedule`.
El patrón `?r=<base64>` es lo estable. Validar que el base64 decodifica a un `http(s)://`.

### Familia A rediseñada — `strategy = "menu2"` (PelotaLibre desde 2026)

Pelota Libre (`pelotalibrehd.su`) se rediseñó: misma idea (`?r=` base64) pero otro HTML. Eventos
en `ul#menu > li`; cada `li` tiene un `div.info` con `<span>` (título) y `<time datetime="HH:MM:SS">`
(hora), y `ul.submenu a[href*="?r="]` con las señales. Lo maneja `AgendaScraper.parseMenu2`.
**Ojo: publica en hora de Perú (UTC-5, `-300`)**, NO en UTC+1 como la versión vieja (se detectó
cruzando los horarios crudos con AlÁngulo1, que también es Perú).

> **Pelota Libre son DOS fuentes** (jul/2026), porque cada frontend tiene una ventaja:
> - **"Pelota Libre"** = `pelotalibrehd.su` (sin guión, **`menu2`**, `/es/agenda/`): **3-4 señales por
>   evento** → el `ServerPicker` deja cambiar de señal si una anda lenta.
> - **"Pelota Libre +"** = `pelotalibre-hd.su` (con guión, **`menuR`** clásico, `/agenda2/`, agenda en
>   iframe): **más eventos** pero 1 señal cada uno.
>
> Los dos publican en **hora de Perú (`-300`)**.

### Familia C — `strategy = "wpjson"` (plantilla WordPress nueva, 2026)

El operador migró varios sitios a un **WordPress con plugin `futbol-agenda`** que sirve la agenda por
**JSON** (no HTML server-rendered): la usa **Al Ángulo** (`alangulotv.quest`) y también apareció en
`pelotalibre.uno`. El HTML raíz NO trae los eventos (los pinta JS); hay que pegarle al endpoint:
`/wp-admin/admin-ajax.php?action=futbol_agenda_data` (GET, sin nonce, devuelve JSON). Lo maneja
`AgendaScraper.parseWpJson`. Estructura: `{"success":true,"data":[{ title, time_raw:"HH:MM:SS",
date_raw, country, channels:[{ name, stream_url }], has_channels }]}`. **`stream_url` ya viene
decodificado** (embed directo, ej `https://tvf90.com/1.php?stream=espn4`) — no hay base64 ni resolver.
`time_raw` está en el huso de la fuente (Al Ángulo = Perú `-300`, verificado por cruce). El `agendaPath`
de la fuente ES la ruta del endpoint (con su `?action=...`).

### Familia D — `strategy = "strapi"` (Pelota Libre `pelotalibre.uno`, 2026)

Otra plantilla: agenda **JSON tipo Strapi** en `/agenda-data.php` (el `agendaPath`). La maneja
`AgendaScraper.parseStrapi`. Forma: `data[].attributes` con `diary_hour` ("HH:MM:SS"), `diary_description`
(título) y `embeds.data[].attributes` con `embed_name`, `idioma` y `embed_iframe` (`/embed/eventos.html?r=BASE64`
→ `EmbedDecoder` lo decodifica). **Base Perú (`-300`, `ZONA_HORARIA_ORIGEN=America/Lima`).** Si `idioma`
viene JSON `null`, `optString` devuelve la string `"null"` (se filtra a "").

> **Pelota Libre = 4 variantes** (sept/2026), todas bajo la solapa "Pelota Libre" (se elige por dominio):
> `pelotalibrehd.su` (menu2, Perú) · `pelotalibre-hd.su` (menuR, Perú) · `pelotalibre.uno` (strapi, Perú) ·
> `pelotaalibre.la` (menuR, **UTC+1** — el crudo sin JS da UTC+1; el navegador convierte con `horario.js`).

## 3.bis — Motor de "recipes" + arreglos de reproducción (hecho por ChatGPT, sept/2026, v0.9)

Todo lo de esta sección lo resolvió **ChatGPT/Astra**, no Claude. Es lo que destrabó el "Player privado"
y, de paso, sacó al proyecto de tener que escribir una `strategy` de Kotlin por cada sitio nuevo.

### El arreglo del "Player privado" (capo8play) — `PlayerDocumentInterceptor`
El problema (que Claude no pudo): embeds tipo `playvi.org/liga1max.php` cargan el player desde
`capo8play.com` (`capo.js` + un `fid`) y **capo8play rechaza reproducir** ("Player privado") porque
valida el **Referer del documento del player**. Setear el Referer del documento TOP no alcanza: la
sub-request a `capo8play.com/capo.php` sale con el Referer equivocado.
**Solución:** en `PlayerScreen` → `WebViewClient.shouldInterceptRequest`, `PlayerDocumentInterceptor`
detecta esa sub-request (por `playerHost`+`playerPath`) cuando el embed pertenece a un `parentHost`
conocido, la **re-descarga con OkHttp poniéndole el `Referer` del sitio padre** (ej `https://playvi.org/`)
y devuelve el HTML como `WebResourceResponse`. Así capo8play "ve" el Referer correcto y reproduce.
Config: `playerDocumentRules: [{ parentHost, playerHost, playerPath, referer }]` en `config.json`
(y default en `AppConfig.playerDocumentRules`). Guardas de seguridad: solo `https` + `GET` +
no-main-frame + código 200 + sin `Set-Cookie` + `text/html` + < 1 MB.

### Motor de recipes (`RecipeEngine`) — scraping declarativo por CONFIG
Reemplaza la necesidad de codear una `strategy` nueva por sitio. Una **"recipe" JSON** en
`Source.agendaRecipe` (y `channelRecipe` / `resolverRecipe`) describe cómo scrapear:
- `format`: `"html"` (selectores CSS Jsoup) o `"json"` (rutas tipo `$.data[*].title`).
- `items` (cada evento), `recognize` (confirma que el formato matchea; si no → `RecipeMismatch`),
  `fields` (`title`/`time`/`date`/`category`) y `servers` (`items` + `fields.url/name/quality`).
- **Pipeline de transforms** por campo: `trim`, `query` (saca un query param), `base64`, `urlDecode`,
  `absolute`, `before`, `after`, `replace`, `default`.
- `request.follow` (hasta 3 saltos a páginas intermedias), `groupByTitle`, `timeFormat`.
Si una fuente trae `agendaRecipe`, `AgendaScraper` la usa en vez de las strategies hardcodeadas. O sea:
**un sitio con estructura nueva ahora se agrega editando el JSON remoto, sin recompilar** (esto es lo que
antes se marcaba como "NECESITA CAMBIO DE CÓDIGO"). Las strategies viejas (`menuR`/`menu2`/`wpjson`/
`strapi`/`rows`/`eventsJson`) siguen como fallback cuando no hay recipe.

### Otros agregados de ChatGPT
- **Blocklist de ads a nivel red (`PlayerAdFilter`):** hosts/URLs de anuncios → respuesta vacía en
  `shouldInterceptRequest` (cumple el pendiente de M3). Se combina con el mata-overlays por JS.
- **Config firmada opcional (`SignedConfig` + `TrustedHttp` + `ConfigCodec`):** un `manifest.json`
  firmado (RSA `SHA256withRSA`, `schemaVersion 2`) que lista bundles con su `sha256`; se verifica con
  una clave pública antes de aplicar. Config remota **a prueba de manipulación** (TrustedHttp usa TLS
  estricto, NO el SSL laxo de `SiteHttp`).
- **RemoteConfig reactivo:** `sources` y `status` son `StateFlow` (la UI se actualiza en vivo, ya no
  hace falta reabrir); `ensureFresh(force=true)` y `rollback()` (volver a la config anterior), con los
  chips **"Actualizar fuentes"** y **"Restaurar anterior"** en `HomeScreen`.
- **Campos nuevos:** `Server.referer` (Referer por señal), `Source.sourceTimeZone`/`targetTimeZone`
  (zonas horarias IANA con DST, ej `America/Argentina/Buenos_Aires`), `Source.agendaRecipe`/
  `channelRecipe`/`resolverRecipe`.
- Dependencia `androidx.webkit`. `versionCode 9` / `versionName "0.9"`.

### Familia B — `strategy = "rows"` (RojaDirecta, Tarjeta Roja)

Agregadores: la agenda es una lista de **filas** de partido, y cada link **NO** es el embed sino una
**página de detalle** que hay que bajar para sacarle el `<iframe>`. Por eso esos `Server` se marcan
con **`needsResolve = true`** y se resuelven recién al apretar OK (con `EmbedResolver`), no al
scrapear. Los selectores de fila/hora/nombre/links son **por fuente** (`eventRowSelector`,
`eventTimeSelector`, `eventNameSelector`, `eventLinkSelector`) y viven en config.
`parseRows` **agrupa por título**: varias filas del mismo partido = un evento con varias señales.

### Horarios

- **Cada fuente publica en SU huso**, no en el del visitante: PelotaLibre / Fútbol Libre /
  AlÁngulo2 / Rústico = **UTC+1** (60); AlÁngulo1 = **Perú** (-300); RojaDirecta = **España**
  (+120, verano); Tarjeta Roja = **UTC-5** (-300). Todo se muestra en **Argentina** (-180).
- La conversión la hace `TimeConverter.toLocal(raw, source)` usando
  `source.sourceUtcOffsetMinutes` → `source.targetUtcOffsetMinutes` (ambos ajustables por
  RemoteConfig, **por fuente**). Verificado jul/2026: 22:45 UTC+1 → 18:45 en Argentina.

### Otros hechos comunes

- **Eventos SIN señal aún:** un evento puede venir con el `<ul>` de servidores **vacío** (todavía no
  le asignaron stream). Se PARSEA igual y se muestra como "sin señal disponible aún"; cuando le
  asignan el `?r=`, aparece jugable solo por el auto-refresh. (No filtrar los eventos sin servidor.)
- El embed corre **Clappr sobre HLS (.m3u8)**, **sin DRM** (a lo sumo AES-128 clear-key, que
  ExoPlayer/hls.js manejan solos). La protección real es **Referer/Origin** (a veces token/cookie corto).
- **Canales con link `?r=` directo** (ej Rústico): `EmbedResolver` detecta ese caso y decodifica en
  vez de bajar la página.

---

## 4. Decisiones firmes (salidas de la verificación adversarial + uso real)

- **Playback default = WebView.** El player nativo Media3 es **opt-in con fallback automático**, no el
  principal (y todavía no está hecho). Motivo: la protección de estos hosts (JWT en cookie HTTP-only
  invisible en la captura, tokens que rotan por minuto en vivo, IP-binding) hace que el replay nativo
  sea "moneda al aire" por stream. El WebView "just works" porque el browser manda
  Referer/Origin/cookies solo.
- **Autoplay AUTOMÁTICO — hoy SOLO-JS (sept/2026, `PlayerScreen.tick`).** Un loop cada ~2s (mientras el
  player está abierto) que recorre el documento y sus **iframes same-origin** y: (1) desmutea + `play()`
  cada `<video>` (si se frenó, lo re-arranca — arregla el "arranca y se detiene"), (2) si nada reproduce,
  clickea el botón de play del propio player, y (3) **esconde overlays de ads** (elementos fixed/absolute
  z alto SIN video/iframe adentro → `display:none`; protege al player). **NO** se tocan teclas ni la
  pantalla (`dispatchTouchEvent`/`dispatchKeyEvent`): un toque al centro en estas páginas hostiles
  **clickeaba un ad** y mandaba la app a segundo plano. El embed suele ser un iframe same-origin
  (ej `tvf90.com/hd.php` → `tvf90.com/5.php`), por eso el JS puede entrar y darle play. Se corta con un
  `AtomicBoolean` al salir (onDispose).
- **Popups:** apilar TODAS las defensas — multiple-windows OFF, `javaScriptCanOpenWindowsAutomatically`
  OFF, `onCreateWindow`→false, y bloqueo de navegación del **main frame** a otro host en
  `shouldOverrideUrlLoading` (comparación de hosts tolerante a `www.` y subdominios).
  *Pendiente:* blocklist angosta de ads/trackers en `shouldInterceptRequest` (que nunca bloquee el
  m3u8/CDN).
- **SSL laxo A PROPÓSITO.** Varios sitios sirven la cadena de certificados incompleta y OkHttp los
  rechaza con "Chain validation failed" aunque el navegador los abra. `SiteHttp` usa un
  `X509TrustManager` que acepta todo + `hostnameVerifier { true }`. Es aceptable **solo** porque la
  app es de uso personal y únicamente **lee HTML público** (sin logins ni datos sensibles).
  **No agregar nunca** credenciales ni datos personales a este cliente.
- **Redirecciones meta/JS globales.** `SiteHttp.get()` sigue hasta 4 saltos detectando
  `<meta http-equiv=refresh>` y `window.location`, pero **solo en páginas de menos de 4 KB** (los
  stubs de redirección son chicos; la agenda real es grande y se devuelve tal cual). Esa guarda de
  tamaño evita "redirigir" por un `location.href` que aparezca en el JS de una página con contenido.
- **Scraper:** dominios **nunca** hardcodeados (fuentes + mirrors, ver M1/M7). Cache **last-good**.
  Parseo por-item aislado (`runCatching` por `<li>`/fila + `selectFirst()?.`) → un reskin degrada a
  lista vacía/stale, no a crash. Distinguir **"vacío"** (llegué al sitio y no hay nada) de
  **"sin conexión"** (no llegué a ningún mirror): `fetchAgenda()` devuelve `emptyList()` vs `null`.
- **Refresh en vivo:** loop coroutine **~45s en foreground** (`viewModelScope`), **NO** WorkManager
  (piso de 15 min, inútil para vivo).
- **v2 nativo — gate de aceptación:** solo hacerlo default si sobrevive **15-20 min de vivo continuo
  sin 403** en los servidores reales. Si 403ea a mitad, queda experimental y el WebView lleva el peso.

---

## 5. Módulos y estado

Marcar `[x]` al completar. v1 usable = M0→M3 (+M4 en la práctica). **Estado real a jul/2026 (v0.2).**

- [x] **M0 — Andamiaje.** Proyecto single-activity Compose for TV, manifest TV completo, banner,
  build config (catálogo de versiones), deps. **VERIFICADO (jul/2026):** compila y corre.
- [x] **M1 — Núcleo de datos resiliente + multi-fuente.** `Source` + lista de fuentes en `AppConfig`
  (7 fuentes, 2 familias) + `SiteHttp` (OkHttp, UA browser, SSL laxo, redirecciones) +
  `AgendaScraper` (estrategias `menuR` y `rows`) + `ChannelScraper` + `AgendaRepository` (cache
  last-good, sealed `AgendaState`) + `AgendaViewModel` (auto-refresh 45s) + `ChannelsViewModel`
  (estado vacío ≠ cargando) + `TimeConverter` por fuente. Logos de canales con Coil (`AsyncImage`).
- [x] **M2 — Grilla TV.** Canales en `LazyVerticalGrid` adaptable + eventos en `LazyColumn`, focus
  D-pad, foco inicial (en el selector de fuente), borde de foco visible, estados
  carga/lista/vacío-error. **VERIFICADO en emulador de TV (jul/2026): el control remoto navega bien.**
- [~] **M3 — Reproductor WebView blindado (corazón de v1).** *Hecho:* `PlayerScreen` (WebView
  fullscreen) carga el embed con `Referer`, autoplay automático TV/celular + desmuteo con reintentos,
  popups off (`onCreateWindow=false`, multiple-windows off), bloquea navegación del main frame a otro
  host (ads), mixed-content ON + `usesCleartextTraffic`, Back cierra, `destroy()` al salir.
  `EmbedResolver` resuelve canales y señales de Familia B (link `?r=` directo o `<iframe>` de la
  página, con filtro de iframes de ads). `ServerPicker` para elegir señal cuando hay varias.
  *Falta:* CSS/JS para aislar el `<video>` y matar overlays; blocklist de ads en
  `shouldInterceptRequest`; manejo de `onShowCustomView`/`onHideCustomView` (ver §8).
- [ ] **M4 — Fallback Cloudflare.** Ante 403/503/challenge, resolver en WebView oculto, cosechar
  `cf_clearance` (CookieManager) → OkHttp; persistir y refrescar. *Done:* la agenda carga aún con
  challenge activo. **No empezado** (hoy un mirror con challenge simplemente se saltea).
- [ ] **M5 — (Opcional) Player nativo Media3.** Sniff `.m3u8` (WebView oculto + `shouldInterceptRequest`)
  + Referer/Origin/UA + cookies; re-resolver al play; `Player.Listener` → fallback auto a WebView en
  401/403; toggle manual Nativo/Web. Pasar el gate de aceptación (§4). *Done:* donde se puede, reproduce
  nativo sin ads; si no, cae solo a WebView.
- [x] **M6 — Distribución + auto-update.** Keystore propio (`keystore.properties`, reusar siempre);
  `assembleRelease` firmado; `UpdateChecker` baja `version.json` de GitHub al abrir y compara
  `versionCode`; si hay una nueva, diálogo "Actualización disponible" → `Updater` descarga el APK a
  `cacheDir` y abre el instalador del sistema (ACTION_VIEW + FileProvider). La instalación siempre
  pide confirmación (Android no permite instalación silenciosa sin ser device owner).
  *Pendiente operativo:* subir cada release a GitHub Releases y actualizar `version.json`.
- [x] **M7 — Reconfiguración remota (lo que evita recompilar).** `RemoteConfig` baja
  `https://raw.githubusercontent.com/JoaquinZ87/pelotalibretv-config/main/config.json` al abrir y PISA
  **`AppConfig.sources`** completo (todas las fuentes con sus mirrors, paths, husos, selectores y
  `strategy`). Cachea el último bueno en SharedPreferences (sobrevive offline); `init()` en
  `MainActivity`, `ensureFresh()` 1 vez/arranque desde los ViewModels. Nunca lanza: si falla, quedan
  el cache o los defaults. **`config.example.json` en la raíz es la plantilla** de ese JSON.
  Para arreglar una mudanza: editar `mirrors` en ese repo. *Pendiente opcional:* sumar la blocklist
  de ads al JSON.

### Backlog inmediato (lo próximo que conviene tocar)
1. Aislar el `<video>` por CSS/JS y matar overlays en el player (M3).
2. Blocklist de ads en `shouldInterceptRequest`, cuidando de no tocar m3u8/CDN (M3).
3. M4 (Cloudflare) recién cuando un challenge realmente moleste.

---

## 6. Convenciones de código

- Kotlin idiomático, MVVM, coroutines; funciones de red/scrape `suspend` + `withContext(ioDispatcher)`.
- Resultados como **sealed** (`AgendaState`: Loading/Success(stale)/Error(cached)). Nunca throw al hilo UI.
- Parseo defensivo: `runCatching` por item + `selectFirst()?.` + `orEmpty()`; cada evento/servidor aislado.
- Config sobre constantes: dominios, paths, husos, selectores y UA salen de `Source` (defaults en
  `AppConfig`, pisables por `RemoteConfig`), no de literales en el código.
- **Al agregar una fuente nueva:** sumarla a `AppConfig.sources` **y** a `config.example.json`. Si el
  sitio no encaja en `menuR` ni en `rows`, primero pensar si se puede describir con selectores en
  `Source` antes de escribir un scraper nuevo.
- TV: todo elemento navegable `focusable` con indicador visible; algo debe tomar el foco inicial o el
  control "no responde" (hoy el foco inicial lo toma el primer chip del `SourceSelector`).
- Comentarios y textos de UI en español (es la app de un usuario, no una librería).

---

## 7. Comandos

```powershell
# Build (Windows). IMPORTANTE: JAVA_HOME al JBR de Android Studio (JDK 21).
# El build sale FUERA de Drive (layout.buildDirectory -> C:\tmp\pelotalibretv-build) para
# evitar locks. Si el clean/merge falla: .\gradlew.bat --stop  y recompilar.
.\gradlew.bat assembleRelease

# El APK firmado queda en:
C:\tmp\pelotalibretv-build\outputs\apk\release\app-release.apk

# Instalar en el box por red (una vez habilitada la depuración por red en el TV)
adb connect <IP_DEL_BOX>:5555
adb install -r C:\tmp\pelotalibretv-build\outputs\apk\release\app-release.apk
adb shell monkey -p com.jz.pelotalibretv -c android.intent.category.LEANBACK_LAUNCHER 1   # lanzar
adb logcat -s PelotaLibre                # logs de la app
```

**Publicar una versión nueva (auto-update):**
1. Subir `versionCode`/`versionName` en `app/build.gradle.kts`.
2. `assembleRelease` y subir el APK a GitHub Releases.
3. Actualizar `version.json` en el repo `pelotalibretv-config`
   (`versionCode`, `versionName`, `apkUrl`, `notes`) — el box lo ofrece al abrir.

Distribución sin PC: bajar el APK en el box con la app **Downloader** (AFTVnews), o dejar que el
auto-update lo haga solo.

**Mantenimiento automático (rutina semanal en la nube):** hay una *routine* de Claude Code que corre
**los lunes 12:00 UTC (9:00 ART)** en la nube, con el repo `pelotalibretv-config` clonado. Revisa cada
fuente del `config.json` (baja la agenda con `curl -skL`, clasifica OK / caída / incautada / rediseñada /
bloqueada-por-Cloudflare), y si algo se rompió **busca el dominio nuevo o re-deriva selectores y commitea
el `config.json` sola**, avisando por **Telegram** (bot propio → chat de Joaquín). Solo toca
`config.json`; una estructura nueva que no encaje en `menuR`/`menu2`/`rows` la marca como
"NECESITA CAMBIO DE CÓDIGO" (eso sí requiere un humano). Gestión/edición de la rutina:
https://claude.ai/code/routines (id `trig_018CSjpDQmmEimgJDZJYHws5`).

---

## 8. Gotchas confirmados

- Sin `touchscreen required=false` → la app **no aparece** en el launcher de TV (error #1).
- Banner debe ser exactamente **320x180** y contener el nombre como texto, o el tile sale vacío.
- WebView **roba el foco** y se come el D-pad. **OJO (sept/2026):** NO uses `dispatchTouchEvent`/
  `dispatchKeyEvent` para arrancar el player en estas páginas — un toque al centro **clickea un ad** y
  manda la app a segundo plano. El autoplay pasó a ser **solo-JS** (`video.play()` + click al botón del
  propio player recorriendo iframes same-origin; ver §4). El `.click()` JS sobre el botón HTML del player
  sí funciona (es un click DOM real sobre ese elemento), a diferencia de simular controles nativos.
- `onShowCustomView` sin `onCustomViewHidden()` en `onHideCustomView` **traba** el player al salir de
  fullscreen. (Hoy no implementamos custom view: el WebView ya ocupa toda la pantalla.)
- Body de OkHttp es one-shot: siempre `.use { }`.
- Base64 del `r=` es **URL-safe**, puede venir sin padding → decodificar con `URL_SAFE or NO_PADDING` en try/catch.
- `WebResourceRequest.getRequestHeaders()` **no** trae la Cookie (WebView la inyecta después) → para el
  path nativo hay que leerla aparte con `CookieManager.getInstance().getCookie(url)`.
- ExoPlayer falla **duro** en 403 (retry ~4x y muere), no degrada → siempre tener el fallback a WebView.
- Play Protect puede avisar "app no segura" al instalar → Detalles → Instalar igual (esperado en sideload).
- Compilar dentro de Google Drive rompe el merge de recursos y el `clean` → build redirigido a `C:\tmp`.
- Sitios con cadena TLS incompleta fallan con "Chain validation failed" → resuelto con el SSL laxo (§4).
- Un sitio que "no carga" puede estar devolviendo un stub de redirección meta/JS → `SiteHttp` ya los sigue.

---

## 9. Fuera de alcance (backlog largo)

Categorías/filtros, buscador, favoritos, historial/continuar viendo, multi-idioma, recordar la última
fuente elegida. Se suman recién después de que M3/M4 estén sólidos.
