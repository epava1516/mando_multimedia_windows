# Historial

## 0.5.0-alpha.13 — Mando Server fijo

- Retira el selector de servidor y la entrada de IP del servidor oficial. La app solo conecta y recupera sesiones con Mando Server.
- Buscar en Wi-Fi consulta únicamente el descubrimiento propio; conserva QR, autorización recordada y recuperación por cambio de IP.
- Mantiene sensibilidad y preferencias guardadas. Regresión de controles, QR y recuperación correcta; APK compilada y firmada.


## 0.5.0-alpha.12 — QR y cambios de IP

- QR local en Windows con IP, puerto, huella del servidor y PIN temporal. Lector QR integrado en Android sin guardar ni enviar imágenes.
- El vínculo se recuerda con Android Keystore. Volver a escanear el mismo PC reutiliza su autorización y evita registros duplicados.
- Buscar un PC conocido con otra IP actualiza el destino, conserva la huella TLS y no envía el token hasta comprobar esa identidad. No necesita QR nuevo por DHCP.
- El servidor detecta cambios de dirección de su red y vuelve a escuchar con un QR actualizado.
- Validación QR Python → ZXing en cuatro orientaciones, regresión UI con IP distinta y Windows CI41735614327 correctas. Prueba física del escáner pendiente.


## 0.5.0-alpha.11 — inicio automático y servicio opcional para escritorio normal

- Inicio automático por cuenta de Windows, directamente en la bandeja; espera y reintenta si la red aún no está conectada.
- Abrir directamente el backend instalado redirige a la bandeja.
- La falta del broker de bloqueo ya no impide iniciar y conectar al escritorio normal; no se relajan sus comprobaciones de identidad ni permisos.
- Diagnóstico local del servicio, coincidencia de cuenta y código IPC. El fallo físico del componente de bloqueo sigue pendiente de diagnóstico en el PC.
- Windows CI41730608599 correcto: arranque sin broker y emparejamiento TLS/estado sin entradas, modo oculto, inicio automático por usuario, instalación/actualización/desinstalación y reglas privadas.


## 0.5.0-alpha.10 — bandeja e IP automática

- Inicio abre el servidor en la bandeja del sistema con IP de la red local detectada automáticamente. Cerrar oculta la ventana; Salir detiene el backend.
- La app busca Mando propio y Remote Mouse; elegir Mando mantiene obligatorio el código completo la primera vez.
- Reglas de firewall limitadas al programa, red privada y subred local para conexión TCP1979 y descubrimiento UDP1980.
- Estado y gestión del código desde la bandeja; detección de puerto ocupado e instancia duplicada.
- Verificación Windows CI41727905603 correcta, incluidos arranque/parada y cierre a bandeja, instalación/actualización/desinstalación y reglas privadas. APK instalada en el móvil; validación de conexión física pendiente.


## 0.5.0-alpha.9 — instalador Windows

- Instalador EXE con asistente, acceso en Inicio y desinstalador. Incluye servidor y configura servicio de bloqueo/gestos sin Python externo ni comandos manuales.
- Autoriza la cuenta de consola activa si UAC usa otra cuenta administrativa. El servidor de red se abre desde Inicio como usuario normal.
- Actualiza solo componentes reconocidos y conserva identidad/autorizaciones. Instalación, actualización y desinstalación verificadas en Windows CI41724946650.
- Publicado en /apps/mando/; la APK sigue en alpha.8 y el servidor propio continúa experimental.


## 0.5.0-alpha.8 — diagnóstico de recepción del portapapeles

- El usuario confirma Móvil → PC y PC → móvil con texto corto de Bloc de notas. Una URL del navegador devuelve `CLIP-NO-TEXT` (`nop` del oficial); queda pendiente probar esa URL con el servidor propio.
- Sustituye el error genérico por un diálogo con código de formato, tamaño, decodificación, timeout o cierre de conexión. No muestra ni registra el contenido.
- Revisión estática mantiene el formato existente: no se ha demostrado todavía la causa del fallo físico.


## 0.5.0-alpha.7 — volumen mantenido y portapapeles

- Mantener volumen más/menos repite hasta soltar, tanto en Windows como en IR. Se cancela al cambiar de pestaña, pausar, perder foco o desconectar; una sola operación pendiente.
- Portapapeles de texto manual Móvil → PC / PC → móvil desde Teclado y la barra nativa, hasta 1000 unidades UTF-16. Sin copiar ni pegar automáticamente.
- Compatible con Dock TCP1979 del oficial (escritura sin ACK: solo confirma envío) y con el servidor propio actualizado mediante TLS y autorización existente.
- Servidor propio limita acceso al escritorio normal; no reenvía el portapapeles al broker de bloqueo.
- 29 tests Python, vectores JVM del codec y regresión de gestos/volumen correctos. Prueba física de portapapeles y volumen mantenido pendiente.


## 0.5.0-alpha.6 — barra sobre el teclado Android

- Fila nativa con desplazamiento horizontal entre el editor y el teclado Android: flechas, Ctrl/Alt/Shift/Win, Esc, Tab, Inicio/Fin, Re/Av Pág y Supr.
- Modificadores de una combinación aplicables a las teclas de la barra o a una letra/número/espacio del teclado Android. Se limpian al ocultar el teclado o abandonar la app; no se mantienen pulsados en el PC.
- Botones sin captura del foco del editor, objetivos de 48dp y etiquetas accesibles.
- Compilación y firma verificadas; prueba física de disposición y combinaciones pendiente.


## 0.5.0-alpha.5 — rueda fina

- Sustituye las ráfagas de quince muestras por muestras individuales en el servidor oficial.
- Bordes y scroll con dos dedos envían incrementos cada 4px en ese modo, reduciendo el desplazamiento por recorrido a un tercio del anterior y evitando el umbral de 20px.
- El usuario confirmó ambos ejes en alpha.4, pero reportó saltos grandes. La fluidez de alpha.5 queda pendiente de prueba física.


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
