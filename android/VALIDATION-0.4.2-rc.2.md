# Mando 0.4.2-rc.2

## Cambios

El pellizco de rc.1 enviaba Ctrl+más/menos y el usuario confirmó que hacía zoom de página. Rc.2 retira esos atajos. El zoom visual usa un complemento opcional en Windows con dos contactos táctiles alrededor del cursor de Firefox, activado por Ctrl+Alt+F12/F11 y confirmado desde Ajustes antes de enviar gestos.

Tres dedos hacia arriba abren Vista de tareas mediante una combinación Win+Tab completa. Los nuevos gestos caducados (>750ms) se descartan, sin desconectar la sesión ni reproducir acciones atrasadas.

## Comprobado

- 21 comprobaciones de frames de protocolo, incluidos los accesos directos del complemento y Win+Tab.
- Regresión táctil en Chromium mediante CDP: ampliar, reducir, scroll, pequeños movimientos, cancelación, cursor y clic; Vista de tareas una vez por gesto, ausencia de acción con tres dedos abajo y ausencia de zoom alternativo cuando falta configurar el complemento.
- 33 comprobaciones de interfaz previas, sin overflow ni errores JavaScript en los tamaños comprobados.
- ABI del complemento comprobada independientemente: POINTER_INFO 88/96 y POINTER_TOUCH_INFO 136/144 bytes (x86/x64), con comprobación de offsets en el propio programa.
- Compilación y firma v3 del APK verificadas. Instalada mediante ADB y confirmada versionName0.4.2-rc.2/versionCode12.

## Pendiente

Compilación/ejecución del complemento en Windows y respuesta visual real de Firefox; no se ejecuta Windows desde el entorno Linux. La combinación que lanza Vista de tareas también necesita confirmación física del usuario. La release 0.4.1 sigue siendo estable.
