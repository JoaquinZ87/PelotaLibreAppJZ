# Recetas remotas — motor 1 / APK 0.9

## Qué cambia

El APK interpreta recetas HTML/JSON, no descarga código ejecutable. Dominios, campos,
selectores, decodificación, pasos intermedios y reglas del player se publican sin recompilar.
Una operación que el motor no conozca sigue requiriendo APK. No hay backend de reproducción.

Archivos de autoría:

- `config/catalog.json`: lista de archivos, versión y reglas globales del player.
- `config/sources/<id>.json`: una fuente; conserva campos compatibles con v0.8.
- `config/recipes/<nombre>.json`: receta reutilizable. Cambiarla afecta sus fuentes consumidoras.
- `app/src/main/assets/config-v2.json`: copia incorporada para primer arranque/offline.
- `config.example.json`: contrato antiguo para APK anteriores. No reemplazarlo por un manifiesto.

`agendaRecipe`, `channelRecipe` y `resolverRecipe` aceptan un objeto o el nombre de una receta.
El bundle materializa las referencias: cada archivo remoto de fuente contiene sus recetas.
El servidor de configuración sigue siendo el repositorio `pelotalibretv-config`.

## Lenguaje disponible

Agendas: `format` (`html`/`json`), `recognize`, `items`, `fields`, `servers` y `allowEmpty`.
`recognize` es obligatorio: identifica el contenedor real de agenda, nunca simplemente `body`.
En JSON debe seleccionar el array contenedor (sin `[*]`); un array vacío reconocido es válido
si `allowEmpty=true`. HTML de challenge, estructura inesperada y datos inválidos no son una
agenda vacía. Un item malformado se aísla; si ningún item es válido se informa incompatibilidad.

Campos: `select`, `read` (`text`, `ownText`, `attr`), `attribute`, `literal`, `transforms`.
En HTML, omitir `select` lee el elemento actual. JSON admite solo rutas `$`, `$.clave`,
`$.data[*].attributes`; no es JSONPath completo: no filtros, índices, expresiones ni scripts.
Los selectores CSS se validan con Jsoup dentro del APK.

Transformaciones ordenadas, máximo 12:

| op | Uso |
|---|---|
| trim | Quita espacios extremos |
| query | Lee el parámetro `value` de una URL, con URL decoding del query |
| base64 | Base64 estándar/URL-safe, con o sin padding |
| urlDecode | Decodificación de formulario; `+` se vuelve espacio, no aplicar de más |
| absolute | Resuelve contra la URL final del documento |
| before / after | Recorta por el separador literal `value` |
| replace | Reemplaza `value` por `with`, sin expresiones regulares |
| default | Usa `value` si el campo quedó vacío |

Ejemplo de URL codificada:

```json
{"read":"attr","attribute":"href","transforms":[
  {"op":"query","value":"r"}, {"op":"base64"}, {"op":"absolute"}
]}
```

`servers.items` se evalúa dentro de cada evento; sus `fields` son `name`, `quality`, `url`.
`needsResolve=true` indica página de detalle. `groupByTitle=true` agrupa por título, fecha y hora,
no mezcla partidos con el mismo título en distintos horarios.

`request.path` sobreescribe el endpoint; `request.follow` admite hasta tres pasos, cada uno con
`format` y `url` (especificación de campo). Sirve para portada → iframe de agenda → datos.
Cada paso descarga un documento público; no ejecuta JavaScript ni resuelve challenges.
El resolvedor usa la misma secuencia de pasos y finalmente extrae `url`.

Canales reutilizan el motor: `fields.title` es nombre, `fields.category` es logo, y el primer
servidor es la página del canal. Ver receta `cards`. La pantalla actual sigue centrada en eventos;
esto no agrega una modalidad nueva de UI.

## Fechas y procedencia

`fields.date` admite ISO `yyyy-MM-dd`; `timeFormat` admite `HH:mm`, `HH:mm:ss`, `h:mma`, `h:mm a`.
Con fecha: se convierte usando `sourceTimeZone` / `targetTimeZone` (IDs IANA). Sin zona declarada
se usa el offset de la fuente. Sin fecha: se conserva fecha desconocida y se usan los offsets
configurados; no se inventa el día actual ni se infiere horario de verano.
Se muestra la fecha cuando existe. Cada evento guarda fuente y URL final; cada señal conserva
la página que la originó como Referer. Eso no garantiza que el servidor permita reproducción.

## Publicación segura

1. Editar fuente/receta y ejecutar `node tools/config-tool.mjs validate`.
2. Validar con el motor Android (inspector debug), usando muestras guardadas y/o la fuente real.
3. Generar una revisión NUMÉRICA nueva, mayor que la última publicada:

```powershell
node tools/config-tool.mjs bundle --out C:\tmp\pelota-config-dist `
  --key "$env:USERPROFILE\.pelotalibretv\config-signing.pem" --revision 2026090502
```

4. Revisar el resultado. El comando NO hace commit, push ni publica automáticamente.
5. Subir `bundles/<revision>/*.json` al repo de configuración. No modificar revisiones existentes.
6. Subir `manifest.json` AL FINAL. Incluye payload firmado RSA-SHA256 y hashes SHA-256 por archivo.
7. Presionar **Actualizar fuentes** en la app y verificar la revisión y la agenda.

La clave privada está fuera del proyecto, en `%USERPROFILE%\.pelotalibretv\config-signing.pem`.
Hacer una copia segura: perderla requiere distribuir otro APK con otra clave pública.
NUNCA subir esa clave a GitHub ni Telegram. No es la clave de firma del APK. Para automatizar
la publicación hace falta provisionarla por separado como secreto del entorno de mantenimiento.
No se configuraron nuevos jobs ni secretos en GitHub durante esta implementación.

La app valida firma, esquema, operaciones, fuentes y hashes antes de activar. Descargas de
configuración/APK usan TLS normal, separado del scraper heredado. No se desactiva CSP ni se
descarga Kotlin/DEX/JS remoto. Hay límites de tamaño, cantidad de fuentes/items, pasos y tiempo.

El catálogo y cada archivo son limitados, y la activación es de conjunto: un archivo incompleto
o inválido deja la configuración anterior. Se guardan la actual y la previa. **Restaurar anterior**
permite rollback local; tras cinco minutos se vuelve a consultar el remoto. Para rollback duradero,
publicar contenido conocido con una revisión MAYOR. Revisión firmada inferior se rechaza.
La UI recibe cambios por StateFlow, conserva selección por ID y no modifica un player abierto.

## Migración / compatibilidad

- Hasta que exista `manifest.json` (HTTP 404), se permite el `config.json` antiguo por HTTPS.
- A los formatos heredados se les asignan recetas incorporadas por estrategia.
- Después de aceptar un catálogo firmado, la app no vuelve a aceptar el canal antiguo.
- Firma inválida o fallo de red NO dispara fallback remoto sin firma.
- `useLegacyParser=true` conserva explícitamente el parser Kotlin de una fuente durante migración.
  No hay fallback silencioso si una receta falla: ocultaría errores como agendas vacías.
- Mantener `config.json`/`version.json` antiguos para los APK ya instalados; publicar v0.9 solo
  después de validar. No se publicó ni alteró producción con esta implementación.

## Diagnóstico y mantenimiento

`RecipeInspectorActivity` existe SOLO en debug. Usa exactamente ConfigCodec/RecipeEngine del APK.
Acepta `--es config archivo.json`, `--es sourceId <id|all>`, `--es sample muestra.html` y
`--es manifest manifest.json`. Archivos en `/sdcard/Android/data/com.jz.pelotalibretv/files/`.
Sin sourceId valida configuración, sin descargar fuentes; `sample` evita solicitudes de red.
Salida: `recipe-report.json` en esa carpeta y tag logcat `RecipeInspector`. No reporta tokens
de URLs ni contenido de cookies. El resultado contiene conteos, primer título, hora y fecha.

```powershell
adb push config-v2.json /sdcard/Android/data/com.jz.pelotalibretv/files/candidate.json
adb shell am start -n com.jz.pelotalibretv/.RecipeInspectorActivity `
  --es config candidate.json --es sourceId futbollibrehdlol
adb logcat -d -s RecipeInspector:I
```

El validador Node comprueba estructura y genera bundles; NO sustituye la validación de selectores
con Jsoup ni una prueba real de video. La rutina semanal existente debe adoptar estos comandos:
proponer cambios → validar muestras con el APK debug → revisar conteos/fechas → firmar/publicar.
Un HTTP 200 o un iframe encontrado no se informa como transmisión operativa.
No subir muestras con cookies/tokens efímeros a un repo público. El respaldo de agendas es
en memoria y por ID de fuente; no persiste tras matar el proceso. La configuración sí persiste.

## Fuera de esta entrega

Normalizador remoto opcional, ejecución de JS para agendas, renovación de challenges, rotación
automática de claves de firma, reglas con regex arbitrario, DEX remoto y cambios nativos del player.
Las limitaciones intencionales mantienen pequeño y auditable el motor.
