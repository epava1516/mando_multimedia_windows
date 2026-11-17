# Mando wire protocol v1

Transporte: TCP1979 sobre TLS>=1.2. Identidad: SHA-256 del DER del certificado del servidor, obtenida del código mostrado localmente por el PC. El cliente rechaza cualquier certificado distinto antes de enviar el PIN. No se negocia una degradación al protocolo oficial.

Cada frame: longitud unsigned de 32 bits big-endian seguida por un objeto JSON UTF-8, de 1 a 8192 bytes. La lectura completa de cada frame tiene un plazo total, sin alargarlo por cada byte. No se admiten mensajes de shell, rutas arbitrarias, patrones IR ni carga de código.

1. Servidor: `{"server":"mando","protocol":1}`.
2. Cliente: `{"op":"pair","protocol":1,"pin":"12345678"}`. PIN aleatorio de ocho dígitos; diez intentos/minuto globales y validez de diez minutos.
3. Servidor: `{"ok":true,"protocol":1,"capabilities":{...}}`, o error y desconexión. Hasta cuatro conexiones autenticadas simultáneas. La autenticación queda ligada exclusivamente a esa conexión TLS.
4. Cliente: `{"op":"action","id":1,"action":{"type":"move","dx":5,"dy":-2}}`.
5. Servidor: `{"ok":true,"id":1}`, o error sin repetir la acción. El cliente cierra al recibir error de acción o una respuesta con secuencia incorrecta. Los IDs crecen estrictamente de uno en uno; una repetición o salto cierra la sesión antes de ejecutar.
6. Estado/heartbeat: `{"op":"status","id":2}`; respuesta con el mismo ID y capacidades. Inactividad de 30 segundos o duración de ocho horas cierra la sesión.

Capacidades: input, lock, task_view, zoom, secure_desktop (disponibilidad del broker local; no implica prueba física), persistent_pairing, horizontal_scroll, dry_run. Un cliente futuro debe rechazar versiones no admitidas. Mantener campos nuevos opcionales y capacidades explícitas evita depender de detalles de una aplicación externa.

Acciones: move(dx/dy enteros ±500), click(left/right), scroll(steps enteros ±10; positivo baja), scroll_horizontal(steps enteros ±10; positivo derecha), text(1–500 caracteres Unicode válidos), key(nombre permitido y hasta cuatro modificadores únicos), zoom(steps entero ±3; se ejecuta un gesto según el signo), task_view y lock. Máximo global 120 acciones/segundo. Todas las entradas se serializan; ningún evento de pulsación queda intencionadamente abierto entre peticiones.

El ACK significa que el adaptador devolvió sin error, no una confirmación visual de Firefox ni garantía de entrega física. Un corte después de ejecutar pero antes del ACK es ambiguo: no se reintenta automáticamente.

## Dispositivos recordados

La respuesta correcta al PIN incluye `device:{id,token}`. El token aleatorio de 256 bits solo se entrega por TLS; Android lo cifra con Keystore. El servidor conserva su hash SHA-256 en el perfil local. Para conectar otra vez: `{op:"resume",protocol:1,device:ID,token:TOKEN}` sobre TLS con el mismo pin de certificado; no necesita un PIN vigente. Se devuelve el mismo esquema de capacidades y empieza una secuencia nueva desde1.

El registro está limitado a cuatro dispositivos. REVOKE borra el registro y rota el PIN dentro de la misma sección crítica; las sesiones comprueban que su dispositivo sigue autorizado antes de atender cada mensaje. Ninguna acción se reintenta para recuperar una conexión rota.

El broker de escritorio usa otro canal: pipe local protegido, sin red, con límites idénticos de frame y validación propia. No acepta rutas, sesiones elegidas por el cliente ni comandos de shell. Solo el servicio decide si la consola está bloqueada y crea el trabajador Winlogon de esa sesión.

La capacidad horizontal_scroll anuncia scroll_horizontal. No se trata como scroll vertical en versiones anteriores. La rueda horizontal usa MOUSEEVENTF_HWHEEL sin mantener Shift. El signo vertical coincide con el protocolo oficial; el gesto natural de dos dedos y los bordes eligen sus signos en el cliente.

## Portapapeles explícito (alpha.7)

Capacidad `clipboard_text`. Operación autenticada `clipboard` con el siguiente id secuencial y `action: {direction: "read"}` o `{direction: "write", text: "..."}`. Respuesta normal `{ok:true,id}` y campo `text` solo en lectura. Texto Unicode válido sin NUL, hasta 1000 unidades UTF-16. No se ofrece en dry-run; solo escritorio normal de la cuenta del servidor, sin broker Winlogon. Sin publicación/polling, historial, logs de contenido ni pegado automático. Comparte límites de frame8192 y revocación de dispositivo de la sesión TLS.

## Descubrimiento local (alpha.10)

UDP1980 recibe exactamente `MANDO_DISCOVER_V1 <nonce>` donde nonce tiene32 hex minúsculas. Responde `MANDO_SERVER_V1 <mismo nonce> <puerto TCP>`, sin credenciales ni operaciones. Límite global20 respuestas/segundo. El anuncio no acredita identidad; Android sigue exigiendo el código completo de la pantalla del PC para fijar el certificado TLS. Los cambios de estado y PIN solo pueden hacerse mediante consola local del proceso, no por descubrimiento.

## Diagnóstico autenticado (alpha.13)

La capacidad `diagnostics_v1:true` anuncia `{"op":"diagnostics","id":N}`. Se aplica el mismo TLS, autorización vigente y secuencia estricta que al resto de mensajes; no hay endpoint HTTP ni puerto adicional. Respuesta `{"ok":true,"id":N,"diagnostics":{...}}`, dentro del límite de8192 bytes. El servidor reutiliza el resultado durante5 segundos por conexión para limitar sondeos repetidos.

Campos: server_version, uptime_s, clients, desktop y events. `desktop` permite únicamente service_reachable, probe, worker_running, worker_verified, locked (si se obtuvo), last_stage, last_win32. Un servicio accesible no equivale a entrada de bloqueo lista; el diagnóstico no arranca el worker ni inyecta entrada. Un código Win32 cero significa que no se capturó un error nativo, no éxito de la operación.

Historial en memoria de los24 eventos más recientes desde el arranque: SERVER_STARTED, DEVICE_CONNECTED, DEVICE_DISCONNECTED, INPUT_REJECTED, CLIPBOARD_UNAVAILABLE. Cada uno lleva fecha UTC. No incluye acciones, texto, rutas, excepciones crudas, IP, nombres de cuenta, PIN, tokens, claves o portapapeles. No lee archivos de logs ni persiste el historial. Copiar el diagnóstico es una acción explícita en el móvil.

## Diagnóstico avanzado (alpha.14)

Capacidad `diagnostics_advanced_v1` y operación fija `diagnostics_advanced`, sin parámetros ejecutables. Devuelve el diagnóstico básico más advanced.local, advanced.previous y advanced.pipe. Caché separada por conexión y operación de5 segundos. Reutiliza el límite8192 bytes y toda la autenticación/secuencia/revocación existentes.

Comprobaciones locales de solo lectura: estado/PID/cuenta clasificada del servicio (LOCAL_SYSTEM/OTHER), comparación de la cuenta actual con el archivo fijo allowed.sid (sin devolver SID/nombre), sesión del proceso y consola activa. No se permite elegir archivos, servicios, comandos o argumentos. No cambia configuración ni permisos.

El sondeo avanzado cierra el canal propio previo y abre uno nuevo con comprobación de PID/SCM; solo envía status y espera hasta2 segundos, sin iniciar worker ni repetir entrada. Trace conserva hasta16 etapas, tiempos y cantidades de bytes, nunca su contenido. Distingue WIN32, TIMEOUT, EOF, IDENTITY_MISMATCH y razones cerradas de rechazo de sesión/cuenta del broker. Win32 cero no se presenta como éxito. `pid_matches:null` o identidad/sesión desconocida permanecen sin confirmar.
