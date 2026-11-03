# Matriz para validar en Windows 11

La compilación/instalación y los tests de protocolo no acreditan recepción de entrada en Winlogon. Probar en el PC, conservando un teclado físico disponible:

- Con el escritorio normal: movimiento, clics, scroll, Unicode, teclas extendidas, volumen y multimedia.
- Firefox seleccionado y cursor en contenido: pellizco visual sin reflow; scroll con dos dedos no debe activar zoom.
- Tres dedos arriba: Vista de tareas, una sola apertura por gesto.
- Candado: LockWorkStation bloquea la sesión sin MandoLock.
- Bloqueado tras iniciar el servidor: cursor/clic y Enter muestran el acceso; comprobar escritura con texto de prueba sin enviar contraseña por el chat.
- Cerrar/reabrir la app con el PC bloqueado: reconexión mediante autorización guardada, sin código nuevo.
- Tras REVOKE: las sesiones anteriores y la reconexión guardada se rechazan; requiere emparejar otra vez desde el PC.
- Otra cuenta/sesión de Windows o UAC: el broker debe rechazar entrada; no se debe cruzar a otro escritorio.
- Reinicio/parada del servicio: no deben duplicarse acciones ni enviarse teclas atrasadas al recuperarlo.
- Quitar componentes: sin servicio integrado el modo real no debe arrancar; el modo simulado sigue sin emitir entradas.

Resultado actual: pendiente de prueba física en el PC del usuario. Los tests automatizados de Windows cubren compilación, ABI, instalador, servicio e identidad IPC; el smoke test del ejecutable usa input simulado.
