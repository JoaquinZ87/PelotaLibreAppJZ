# Reglas de ProGuard/R8. El release de v1 va sin minify (isMinifyEnabled = false),
# así que por ahora no hace falta nada acá. Cuando se active el minify:
#  - Jsoup y OkHttp ya traen sus reglas consumer, no suele hacer falta agregar.
#  - Revisar las clases usadas por reflexión si se agregan librerías nuevas.
