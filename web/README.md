# Mando · presentación y descargas

Landing pública: https://mando.sherlockhomeless.net/. Homepage apunta a esta página. La ruta anterior `/apps/mando/` conserva su autenticación.

HTML y CSS locales sin JavaScript, formularios ni analítica. Solo `public/` se sirve por Nginx; contiene APK alpha.26, EXE alpha.16, instrucciones, hashes, metadatos y recursos visuales. No montar la raíz del proyecto ni publicar claves o `.state`.

Despliegue desde este directorio:

```sh
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d
```

Configuración inicial: copiar `.env.example` a `.env`. Usa la red externa proxy, HTTPS de Traefik sin CrowdSec en este subdominio. DNS directo gestionado con `cloudflared/cloudflare_dns.py create mando` desde la raíz del repositorio (comprobar primero con `--dry-run`).

Al publicar versiones, actualizar los dos artefactos en `public/downloads/`, sus SHA-256, `release.json` y los enlaces/versiones de `index.html`. La descripción distingue funciones disponibles del fallo pendiente en el escritorio bloqueado. El fragmento `homepage-service.yaml` define la entrada pública de Homepage; modificar únicamente Mando conservando las demás entradas y una copia de seguridad del YAML activo.

La presentación no tiene limitador de peticiones. Las descargas APK/EXE/ZIP permiten por IP una media de 6 peticiones/minuto y una ráfaga de 6; el exceso devuelve HTTP 429. Traefik usa la IP de conexión, sin confiar en X-Forwarded-For aportado por el cliente. Las IP compartidas comparten el cupo. APK, EXE y ZIP tienen además un límite de 2 MiB/s por transferencia desde el inicio. No es un límite global de ancho de banda. La ruta antigua autenticada no cambia.

Máximo de 2 transferencias simultáneas por IP. Las solicitudes HEAD también cuentan en los límites de frecuencia y concurrencia; HTML, imágenes, instrucciones y hashes quedan fuera.

## Datos estructurados

Ejecutar `python3 update_schema.py` desde este directorio tras actualizar `release.json` y la versión/enlace visible de la APK. Genera JSON-LD con WebSite (nombre del sitio para Google), WebPage y SoftwareApplication enlazados por @id. Comprueba enlace y tamaño del artefacto para no publicar una versión contradictoria. No cambia CSP ni carga JavaScript ejecutable.

La app se describe semánticamente, pero sin valoraciones/reseñas reales no es elegible actualmente para el resultado enriquecido de software de Google. No se han inventado Review, AggregateRating, ofertas, precios, editor legal, fechas o una ficha de Play. Tampoco se añade Product para eludir requisitos ni FAQ/HowTo para apariencias retiradas. Los datos describen la APK personal actualmente descargable, no la variante pública ni compras futuras aún desactivadas.

Guías: https://developers.google.com/search/docs/appearance/site-names y https://developers.google.com/search/docs/appearance/structured-data/software-app . Validación local de JSON y referencias y comprobación HTTP no equivalen a una aprobación de Rich Results Test o a indexación en Search Console.

Para copiar binarios usar `python3 publish_download.py /ruta/al/artefacto Nombre-version.apk --legacy` (o `.exe`). Publica de forma atómica, con permisos0644 y etiqueta SELinux heredada del directorio de destino. No usar copy2/cp --preserve=xattr desde /tmp: una etiqueta user_tmp_t produce Forbidden de Nginx. Actualizar después hashes, release.json, versiones visibles y ejecutar update_schema.py. Comprobar tanto descarga real como SHA-256, no solo la landing.
