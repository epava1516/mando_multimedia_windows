# Mando

**Versión estable 0.4.1.** App Android para manejar un PC Windows y un televisor LG desde el móvil. Validada por el usuario con Windows 11, Firefox/HBO Max y el perfil infrarrojo LGTV1.

La rama de gestos prepara **0.4.2-rc.2**: zoom visual de Firefox mediante el complemento opcional [MandoTouchZoom](windows-zoom/README.md), más tres dedos hacia arriba para abrir la Vista de tareas. El pellizco se distingue del scroll. Pendiente de confirmación física; `v0.4.1` sigue siendo la release estable.

## Funciones

- Control: touchpad, clic izquierdo/derecho, scroll con dos dedos y teclado Android accesible desde el botón central.
- Reproducción: anterior/siguiente, retroceder/avanzar, play/pausa y volumen/silencio de Windows.
- Teclado extendido: funciones, navegación, modificadores y atajos.
- Bloqueo del PC mediante el ajuste opcional MandoLock. Cursor y teclado comprobados en la pantalla de bloqueo.
- IR: volumen, silencio, Exit y encender/apagar con LGTV1. Funciona sin Wi-Fi ni conexión al PC. La pestaña se puede ocultar desde Ajustes y empieza oculta en móviles sin emisor.
- Sensibilidad persistente. Conexión, desconexión y estado agrupados en Ajustes.

## Requisitos e instalación

Android 9 o posterior. Para controlar el PC, instala por separado el [servidor oficial Remote Mouse](https://www.remotemouse.net/downloads/RemoteMouse.exe) en Windows, versión 5.30 o posterior. Mando no modifica ni incluye esa aplicación. El PC y el móvil deben poder comunicarse por la Wi-Fi local, TCP/UDP 1978.

Instala la APK, abre el servidor en Windows y pulsa **Buscar en Wi-Fi** en Mando, o introduce la IP privada del PC y pulsa **Conectar**. Si una VPN bloquea la conexión local, excluye solo Mando de ese túnel. Al salir de la app se desconecta; vuelve a conectar cuando regreses.

Para bloquear el PC, sigue [la instalación de MandoLock](windows-lock/README.md). Para IR, apunta el emisor del móvil al televisor. No hace falta instalar Lean Remote: solo se incorporan las cinco señales del perfil solicitado.

## Compilar

Necesita Python 3, JDK 17, Android SDK Platform 36 y Build Tools 36.0.0 ya instalados.

```bash
ANDROID_HOME=/ruta/al/android-sdk python3 android/build.py
```

Produce `android/mando-personal.apk` y verifica la firma. No usa Gradle ni descarga dependencias durante la compilación. ADB permite instalarla con `adb install -r android/mando-personal.apk`.

La clave de firma se crea y conserva en `.state/`, excluida de Git. **Conserva una copia privada de esa carpeta:** actualizar la instalación existente requiere la misma clave; una clave nueva no permite sustituir el APK instalado. Los binarios de distribución se mantienen fuera del historial de código.

## Validación

El usuario confirmó el funcionamiento de todos los controles, bloqueo, teclado en pantalla bloqueada, cinco botones IR y preferencia de visibilidad IR. Se ejecutaron 33 comprobaciones de interfaz y 8 de visibilidad IR, sin errores JavaScript ni scroll en los tamaños comprobados. [Detalle de validación](android/VALIDATION-0.4.1.md).

Prueba reproducible del encuadre del protocolo:

```bash
mkdir -p android/build/test
javac --release 8 -encoding UTF-8 -d android/build/test android/src/net/mando/personal/Protocol.java android/ProtocolTest.java
java -cp android/build/test net.mando.personal.ProtocolTest
```

## Alcance de seguridad

Esta versión admite servidores que anuncian `nop`; rechaza los que exigen contraseña, sin omitir su autenticación. El transporte de controles es UDP de compatibilidad sin cifrado. No incluye vault y no debe utilizarse para enviar contraseñas. Usa una red local de confianza. [Detalles](SECURITY.md).

Código y comportamiento de Android en [android/README.md](android/README.md); notas del protocolo y del perfil IR en [evidence/](evidence/README.md). Los prototipos previos, capturas, APK originales, volcados de ingeniería inversa y claves privadas no forman parte de este repositorio.

Prueba de gestos con Chromium y Playwright instalados:

```bash
node android/tests/pinch-zoom.cjs
```

El script usa `playwright` por defecto. `PLAYWRIGHT_MODULE` permite indicar un módulo local y `MANDO_CHROMIUM` la ruta de Chromium. Simula el puente Android; no emite controles a un PC.
