# Zoom visual opcional de Firefox para Mando

Este componente experimental convierte dos atajos en un pellizco tactil nativo de dos contactos. Su objetivo es el zoom visual de Firefox/APZ, sin usar Ctrl+rueda, cambiar el zoom de pagina ni activar la Lupa de Windows. La respuesta real debe comprobarse en tu Firefox/Windows: no se ha ejecutado ni compilado este componente en Windows durante su preparacion.

Copia esta carpeta completa al PC. Abre **Windows PowerShell 5.1** como usuario normal y ejecuta `./Instalar-MandoZoomVisual.ps1` desde ella. Si la politica local impide ejecutar scripts, revisa esa politica; el instalador no la modifica. Compila el fuente local con `Add-Type`, instala en `%LOCALAPPDATA%\MandoTouchZoom` y crea dos accesos directos en el menu Inicio. No requiere administrador, servicio, puertos, conexion de red ni contrasenas. No ejecuta gestos durante la instalacion.

Desde la carpeta extraida, despues de revisar los archivos:

```powershell
Get-ChildItem *.ps1 | Unblock-File
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\Instalar-MandoZoomVisual.ps1
```

La politica `RemoteSigned` de este comando se limita al proceso; no cambia la politica permanente ni sustituye politicas de una organizacion.

Antes de activar la opcion de zoom visual en Ajustes de Mando:

1. Enfoca fisicamente Firefox y deja el puntero sobre el contenido de una pagina, lejos de los bordes, las pestanas y la barra de direcciones.
2. Pulsa **Ctrl+Alt+F12** para acercar y **Ctrl+Alt+F11** para alejar. Suelta las teclas. Cada ejecucion produce un unico gesto de unos 150 ms y factor aproximado 1,18.
3. Comprueba que cambia la ampliacion visual del contenido sin redistribuir el texto. Si no ocurre, no confirmes que el helper esta listo en Mando. Los atajos del menu Inicio pueden tener un pequeno retardo de Windows.
4. Activa la opcion de Mando solo despues de validar ambos atajos. El servidor oficial debe poder entregar esas teclas al escritorio normal.

El helper acepta exclusivamente `in` u `out`. Solo actua si el proceso en primer plano se llama Firefox y ambos contactos caben dentro de su area cliente y del monitor del puntero. No distingue una pagina de otras zonas del area cliente de Firefox: coloca el puntero sobre la pagina. Espera la liberacion de los modificadores y descarta llamadas simultaneas mediante un mutex local. Interrumpe el gesto si cambia la ventana o su geometria e intenta liberar/cancelar ambos contactos en `finally`. No mantiene Ctrl pulsado. Windows puede mover el cursor al promover entrada tactil: comprueba la posicion entre gestos; el helper no restaura el cursor ni envia movimiento adicional.

Si no responde, revisa el foco, el espacio hasta los bordes, conflictos con otros programas y el soporte de entrada tactil sintetica de esa instalacion de Firefox. No funciona en la pantalla de bloqueo ni sobre ventanas elevadas. El instalador detecta conflictos de atajos en accesos directos habituales; otros programas pueden registrar los mismos atajos sin un acceso directo detectable.

Para quitarlo ejecuta `./Desinstalar-MandoZoomVisual.ps1`. Solo elimina sus dos accesos directos reconocidos y los archivos marcados; conserva archivos ajenos. Una version distinta requiere desinstalar primero. No modifica Firefox, el servidor Remote Mouse ni las politicas de Windows.

Validacion disponible: revision estatica y comprobacion independiente de tamanos/alineacion nativa con empaquetado 8: POINTER_INFO 88/96 bytes y POINTER_TOUCH_INFO 136/144 bytes (x86/x64). El ejecutable comprueba esos tamanos y offsets antes de inyectar. **Compilacion con Windows PowerShell 5.1 y comportamiento en Windows pendientes de prueba.**

Referencias: [InjectTouchInput y estados/reintentos](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-injecttouchinput), [entrada y zoom visual de Firefox en Windows](https://firefox-source-docs.mozilla.org/widget/windows/windows-pointing-device/index.html).
