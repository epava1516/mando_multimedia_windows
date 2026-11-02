# Historial

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
- Cierra la conexión al abandonar la app; la reconexión es manual.
