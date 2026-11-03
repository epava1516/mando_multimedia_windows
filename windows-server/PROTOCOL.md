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
