# Zoom: comportamiento observado y corrección

El usuario comprobó 0.4.2-rc.1: Ctrl+más/menos cambia el zoom de la página (reflow), mientras que el objetivo es ampliación visual en Firefox. Estos frames se retiraron de la candidata actual.

0.4.2-rc.2 envía Ctrl+Alt+F12/F11 como combinaciones completas para los accesos directos del complemento opcional MandoTouchZoom. Este utiliza InitializeTouchInjection e InjectTouchInput para un gesto táctil de dos contactos, con Firefox en primer plano y centro alrededor del cursor. La vía táctil hacia APZ se documenta en [Mozilla](https://firefox-source-docs.mozilla.org/widget/windows/windows-pointing-device/index.html) y la API en [Microsoft](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-injecttouchinput). La respuesta de Firefox en el PC no se ha observado desde el entorno Linux.

La Vista de tareas usa una combinación completa Win+Tab: `key 14[kld]tab[*]cmd`. Función de Windows documentada por [Microsoft](https://support.microsoft.com/en-gb/accessibility/windows/keyboard-shortcuts-in-windows).

## Evidencia histórica del intento de atajo (rc.1)

# Zoom de un solo paso: análisis del servidor

Inspección estática del servidor oficial; no se enviaron controles al PC. Ensamblado SHA-256 `e9b98e6cce2cb7dbdaec44828aafd6be17c5ffe30f24b17bcaa95edf5d2cd4a7`, evidencia REA `ev_21ab3f299b569e851d29851c9630a2af107cfe94a917f3b52bef3acd4d1182b4`. Selección del CIL: `/tmp/remote-mouse-analysis/zoom-selected-paths.json`.

## Frames derivados compatibles

Un gesto puede convertirse en pasos discretos, cada uno un frame completo y un datagrama:

```text
key 13[kld]+[*]ctrl
key 13[kld]-[*]ctrl
```

Los 13 bytes de cuerpo son ASCII. No mantener Ctrl pulsado en un datagrama y soltarlo en otro.

- `A.H.A` (`0x060000b3`) reconoce prefijo `[kld]` y marcador `[*]`.
- `A.H.a` (`0x060000b5`) retira el prefijo y separa los campos en `['+', 'ctrl']` o `['-', 'ctrl']`.
- `A.H.A(string[])` (`0x060000b7`) convierte los campos en tecla principal y modificadores.
- `UtilityHelper.getVirtualKeyCodes` (`0x060000ee`) reconoce `ctrl` como VK_CONTROL 17. Un símbolo de longitud uno se resuelve por `VkKeyScan` y se utiliza su byte bajo.
- `A.g.A(byte,byte[])` (`0x0600009f`) construye Ctrl abajo, tecla principal abajo, tecla principal arriba y Ctrl arriba, y los entrega juntos a `SendInput`. No existe un Ctrl mantenido en el cliente entre pasos.

## Referencia de zoom ya presente en el servidor

El helper `A.F.A(int)` (`0x06000088`) maneja valores 5 y 6 enviando internamente a su parser de teclado `ctrl[+]-` y `ctrl[+]=` respectivamente. Esto confirma que el servidor usa combinaciones Ctrl/tecla para esas acciones, pero no se ha identificado ni recomendado un frame externo específico de ese helper.

Los nombres `plus` y `minus` que aparecen en otro componente no están reconocidos como teclas en el mapper examinado: una palabra no reconocida de longitud mayor que uno devuelve cero. Por eso no usar `[kld]plus[*]ctrl`.

Se propone el carácter `+` en vez de `=` para el zoom de aumento: `VkKeyScan` depende del teclado de Windows, y en algunos teclados `=` está en una tecla numérica cuyo byte bajo no representa el mismo atajo que en US. El servidor ignora los bits de modificadores devueltos por VkKeyScan; no se garantiza equivalencia en todos los layouts. La combinación sigue el mecanismo confirmado del servidor y debe probarse en el PC.

## Límites

Estos mensajes envían atajos al programa con foco, no una API universal de zoom. El navegador/PDF u otra aplicación debe admitir Ctrl+más/menos; una aplicación con otros atajos puede ignorarlos o darles otro significado. No se ha comprobado el resultado de un gesto en Windows. La ruta de selección de escritorio es la universal UDP ya descrita, pero esta función está pensada para la aplicación del escritorio normal; no hay utilidad demostrada de zoom en Winlogon.
