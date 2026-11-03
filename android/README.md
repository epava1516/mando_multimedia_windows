# Mando Android · experimental 0.5.0-alpha.3

APK propio, paquete `net.mando.personal`, nombre **Mando**. No sustituye ni modifica Remote Mouse. Requiere Android 9 o posterior y el servidor oficial de Windows 5.30 o posterior con TCP y UDP 1978 accesibles.

La versión **0.4.1** incluye panel táctil con sensibilidad regulable y persistente (0,15–2,5×), multimedia, volumen y silencio de Windows, teclado Android nativo, teclas extendidas y acceso a la configuración de bloqueo del PC. La sensibilidad se configura en **Ajustes** y conserva las fracciones de movimiento para evitar perder desplazamientos pequeños. La pantalla Control reúne touchpad, clics, reproducción y volumen; el scroll se hace con dos dedos.

En Control, el botón de teclado entre los clics izquierdo y derecho permite abrir y cerrar el teclado Android sin cambiar de pestaña. Mientras está abierto se ocultan los controles multimedia para conservar espacio para el touchpad. En Teclado, pulsa **Abrir teclado Android**; la pestaña también lo abre directamente. Las pulsaciones confirmadas por el método de entrada se envían al PC; la composición todavía sin confirmar permanece en el editor local. Retroceso, Suprimir y Enter tienen manejo nativo. El campo se vacía después del envío y no se guarda. **Cerrar** oculta el teclado. Las teclas extendidas se dividen en grupos F1–F12, Navegación, Atajos y Texto. La interfaz se adapta a la altura disponible sin desplazar el panel completo.

Multimedia usa un único modo global de Windows, confirmado por el usuario con HBO Max en Firefox. Tiene anterior/siguiente, reproducción/pausa, volumen y silencio. Los botones **Retroceder** y **Avanzar** son distintos de anterior/siguiente y envían las flechas al vídeo seleccionado. No hay selector de modo.

Corrección de transporte: TCP sirve para el saludo y la supervisión de conexión; el handler del servidor analizado descarta los frames `key` recibidos por TCP. El ratón, las teclas, el texto, multimedia y el atajo de MandoLock usan la ruta **UDP legacy compatible** del servidor, un frame UTF-8 por datagrama. Esa ruta conserva el mismo control de autenticación del servidor; esta APK solo la habilita si el saludo anuncia `nop`. No hay reintentos automáticos ni confirmación de recepción UDP. Resumen del protocolo en [evidence/README.md](../evidence/README.md).

## Conectar y buscar

1. Abre el servidor oficial en Windows.
2. En Mando, introduce la IP privada del PC y pulsa **Conectar**. Usa la dirección de tu propio ordenador; el campo se puede editar.
3. Para descubrirlo si cambia de red, pulsa **Buscar en Wi-Fi**: comprueba únicamente TCP 1978, hasta 254 direcciones del segmento Wi-Fi, y muestra servidores cuyo saludo `SIN` anuncia Windows compatible. En redes mayores que /24 la búsqueda se limita al /24 que contiene al móvil; puedes introducir cualquier otra IP privada manualmente.

El socket se vincula a la red Wi-Fi física, no a la VPN. Si la VPN no permite ese acceso, excluye únicamente **Mando** en la configuración del túnel; no es necesario desactivar la VPN para las otras aplicaciones. Al salir de Mando se cierra el transporte y se descartan las entradas pendientes. Si vuelves antes de 20 minutos se recupera automáticamente el último PC, sin repetir acciones; solo se muestra Conectar al fallar o caducar el plazo. Desconectar cancela la recuperación.

Esta versión admite el modo `nop` anunciado por el servidor del usuario. Si el servidor requiere su propia contraseña, la app lo indica y cierra la conexión: no omite autenticación ni desactiva contraseñas del servidor.

## Contraseña de Windows

**Esta versión no incluye vault.** La ruta UDP de compatibilidad envía los comandos y el texto sin cifrado. No envíes contraseñas por este canal. La existencia de un servicio oficial con acceso a Winlogon no sustituye la protección del transporte. Para esa función falta una ruta cifrada autenticada y almacenamiento nativo ligado a Android Keystore/biometría.

## Construcción

Necesita JDK 17, Android SDK Platform 36 y Build Tools 36.0.0 ya instalados:

```bash
ANDROID_HOME=/ruta/al/android-sdk python3 build.py
```

Genera `mando-personal.apk`, lo alinea, firma con una clave propia guardada en `../.state/` y verifica su firma. No compartas `.state/`: conserva la clave para poder actualizar la instalación. El APK no contiene esa clave privada, contraseñas, compras ni código de los binarios originales.

Pruebas del encuadre de protocolo:

```bash
mkdir -p build/test
javac --release 8 -encoding UTF-8 -d build/test src/net/mando/personal/Protocol.java ProtocolTest.java
java -cp build/test net.mando.personal.ProtocolTest
```

La interfaz se carga de los recursos incluidos en el APK y solo esos recursos pueden acceder al puente nativo. No se ejecuta contenido recibido del servidor. La conexión valida el saludo, longitud y versión; no autentica criptográficamente al servidor. No necesita root, accesibilidad, cámara ni permisos de almacenamiento.

## Bloqueo del PC

El usuario confirmó que el Win+L físico funciona y el inyectado no. El candado no vuelve a enviar ese atajo fallido: ofrece configurar el ajuste opcional de [Windows](../windows-lock/README.md). Una vez instalado y probado el ajuste, confirma en la APK que está instalado; los siguientes toques envían Ctrl+Alt+L al acceso directo local. La app no da por configurado el ajuste por defecto ni ejecuta el bloqueo durante la configuración. El usuario confirmó que el ajuste bloquea su Windows 11 y que el cursor, Enter y escritura funcionan también en la pantalla bloqueada.

## Televisor por infrarrojos

La pestaña **IR** usa el emisor físico del móvil y el perfil **LGTV1** de Lean Remote. Está disponible sin Wi-Fi y sin vincular el PC. Incluye subir/bajar volumen, silencio, Exit y encender/apagar (botón de alternancia). Apunta la parte superior del móvil al receptor del televisor.

Solo se incorporan las cinco señales de ese perfil; no hace falta instalar ni ejecutar Lean Remote para enviarlas. Cada toque emite una señal, sin repeticiones en segundo plano. La interfaz confirma el envío de Android, no una respuesta del televisor. Hardware ausente o incompatible deja los botones desactivados. Permiso normal `TRANSMIT_IR`, sin root ni permisos de accesibilidad. Detalles del perfil y conversión en `../evidence/LEAN_IR.md`.

La conexión, el botón Desconectar y el estado de operaciones se muestran únicamente en Ajustes. Los errores fuera de Ajustes aparecen como avisos temporales; no hay registro permanente bajo Control, Teclado o IR.

En Ajustes, **Mostrar pestaña IR** permite ocultar o mostrar el mando de TV. La preferencia se guarda en cada móvil. Sin emisor compatible, la pestaña empieza oculta por defecto; se puede mostrar manualmente para consultar el panel (sus botones permanecen desactivados).

## Gestos (0.4.2-rc.2)

Separa dos dedos sobre el touchpad para ampliar visualmente una zona de Firefox y júntalos para reducir. El centro del zoom es la posición del cursor en el PC: coloca primero el ratón sobre la zona deseada. Instala y prueba una vez [MandoTouchZoom](../windows-zoom/README.md) en Windows y confírmalo desde Ajustes → Configurar zoom visual. Sin esa confirmación no se envían atajos de zoom de página como alternativa.

La APK envía Ctrl+Alt+F12/F11 completos al servidor oficial; los accesos directos de Windows lanzan un gesto táctil de dos contactos. El complemento actúa solo con Firefox en primer plano y el cursor en su área cliente; su recepción por Firefox necesita prueba física. No añade un servidor de red.

El movimiento paralelo de dos dedos conserva el scroll. El gesto se clasifica tras un umbral y no mezcla zoom con scroll hasta levantar los dedos. Tres dedos hacia arriba abren una vez la Vista de tareas mediante Win+Tab para seleccionar ventanas o escritorios virtuales. La sensibilidad del cursor no altera los umbrales de gestos.

## Servidor propio integrado (0.5.0-alpha.2)

Selecciona **Mando propio (experimental)** y pega una vez el código del paquete [Windows completo](../windows-server/README.md). Android verifica la huella del certificado por TLS antes de enviar el PIN. Después guarda únicamente la autorización de dispositivo con AES-256-GCM y Android Keystore, no una contraseña de Windows.

Para volver, selecciona el servidor propio y pulsa Conectar con el código vacío. Esto permite reconectar estando el PC bloqueado si el servidor sigue abierto. Desde alpha.3, la recuperación de conexión es automática durante 20 minutos; nunca se repiten acciones. Ajustes → Olvidar PC propio borra esa autorización local; REVOKE en la consola Windows revoca dispositivos y sesiones.

El paquete Windows integra bloqueo, zoom y un broker local restringido para Winlogon. Se anuncia la disponibilidad del componente; eso no sustituye la prueba física de entrada en Windows. El servidor debe iniciarse en la cuenta autorizada antes de bloquear; no hay control de primer login, UAC ni RDP. La opción oficial y su búsqueda Wi-Fi se conservan; descubrimiento del servidor propio queda pendiente.

## Recuperación de sesión (0.5.0-alpha.3)

El plazo se cuenta desde la última salida con una conexión activa, con reloj monotónico. Un intento interrumpido o fallido no renueva el plazo. Los metadatos no secretos del destino se conservan de forma privada, asociados al contador de arranques del móvil; un reinicio invalida el plazo. La autorización del servidor propio sigue en Android Keystore.

Durante la recuperación se mantiene la vista anterior y se deshabilita la entrada al PC. Si la actividad se recrea, muestra Recuperando conexión hasta conocer el resultado. Solo un fallo real o caducidad muestra la pantalla de conexión. No hay sockets ni comandos activos en segundo plano, ni reenvío de pulsaciones o gestos pendientes. Desconectar interrumpe un intento en curso y elimina el destino de recuperación. IR sigue siendo independiente.
