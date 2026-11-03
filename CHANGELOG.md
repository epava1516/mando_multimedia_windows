# Historial

## 0.5.0-alpha.4 — bordes de rueda

- Borde derecho para rueda vertical y borde inferior para horizontal, con un dedo y sin clics ni movimiento del cursor.
- Espera movimiento de ambos dedos antes de distinguir scroll y pellizco; corrige la clasificación prematura de eventos separados.
- Normaliza los pasos del servidor oficial 531: muestra inicial tras reposo y muestras de rueda de alta resolución para un paso visible.
- Rueda horizontal nativa sin modificadores mantenidos y soporte equivalente en el servidor propio/broker.
- Conserva la recuperación automática de 20 minutos. El modo propio requiere el paquete de servidor actualizado para horizontal.

## 0.5.0-alpha.3 — recuperación de sesión

- Recupera automáticamente el último servidor si vuelves antes de 20 minutos, con la vista anterior mientras conecta.
- Si falla o caduca el plazo, muestra Conectar. Desconectar cancela incluso una recuperación en curso.
- Descarta entradas pendientes al salir; no envía controles en segundo plano ni los reproduce después.
- Conserva metadatos privados de recuperación ante recreación del proceso y valida el arranque del móvil.
- Regreso automático confirmado por el usuario en su móvil y servidor oficial. La parte propia sigue en pruebas.

## 0.5.0-alpha.2 — componentes propios integrados

- Paquete Windows con servidor ejecutable sin Python externo, gestos y bloqueo directo.
- Servicio local restringido y trabajador Winlogon para la cuenta/sesión autorizada, con instalador y desinstalador revisables.
- Zoom incluido en el paquete, sin instalar MandoLock ni el complemento por separado para el modo propio.
- Dispositivos recordados con Android Keystore/AES-GCM; hashes en Windows y revocación de autorizaciones/sesiones.
- CI Windows: compilación, autopruebas, instalación/desinstalación, identidad del pipe y smoke test TLS del EXE sin entrada real.
- Prueba física de bloqueo y gestos aún pendiente; se mantiene como alfa.

## 0.5.0-alpha.1 — servidor propio experimental

- Servidor independiente para Windows: TLS, código de emparejamiento, contrato JSON v1 y ACK por acción.
- Cliente Android con pinning de certificado y selector propio/oficial.
- Entrada Unicode, ratón, multimedia/volumen, Vista de tareas y bloqueo directo; reutiliza el complemento propio de zoom.
- Límites de mensajes, sesiones, intentos y secuencias para rechazar acciones repetidas.
- Sin soporte de escritorio seguro todavía. Validación física en Windows pendiente; estable continúa en 0.4.1.

## 0.4.2-rc.2 — candidata

- Pellizco para zoom visual de Firefox alrededor del cursor con el complemento local opcional MandoTouchZoom. Se configura desde Ajustes, después de instalar y probar el complemento en Windows.
- Tres dedos hacia arriba abren la Vista de tareas (ventanas y escritorios) mediante Win+Tab, una vez por gesto.
- Distingue gestos de scroll y filtra movimientos pequeños, cancelaciones y solicitudes caducadas.
- La prueba física de los nuevos gestos sigue pendiente. La release estable continúa en 0.4.1.

## 0.4.2-rc.1 — sustituida

El usuario comprobó que el pellizco enviaba Ctrl+más/menos y cambiaba la distribución de la página. No correspondía al zoom visual solicitado; se ha retirado ese envío.

## 0.4.1 — estable

Primera publicación estable, confirmada por el usuario.

- Control del PC con touchpad, teclado Android y extendido, reproducción y volumen.
- Controles multimedia integrados con el touchpad y teclado centrado entre los clics.
- Conexión y estado de operaciones en Ajustes.
- Ratón y teclado compatibles con la pantalla de bloqueo del servidor oficial; ajuste opcional MandoLock para bloquear.
- Mando IR LGTV1 con cinco comandos probados en el televisor.
- Preferencia persistente para mostrar u ocultar IR, oculto inicialmente sin hardware compatible.

## Limitaciones conocidas

- Sin autenticación de servidores que requieran contraseña ni cifrado del canal UDP de compatibilidad. Sin vault.
- Retroceder/avanzar requieren seleccionar primero el reproductor del navegador.
- No incluye arrastre mantenido ni duplicación de pantalla.
- Cierra el transporte al abandonar la app; recupera la conexión al volver durante 20 minutos desde alpha.3.
