# Mando 0.5.0-alpha.4

Bordes internos de 44px para rueda vertical (derecha) y horizontal (abajo). Los toques no generan clic ni movimiento. Dos dedos esperan ambas actualizaciones antes de clasificar scroll/pinch. Servidor oficial usa muestras de rueda nativas, sin Shift mantenido.

- 25 checks de protocolo, incluidas las cuatro direcciones de rueda.
- CDP: arrastres por ambos bordes, horizontal en ambos sentidos, toque de borde sin clic y dos dedos verticalmente alineados con actualizaciones desfasadas. Regresiones de zoom, cursor, gestos y sesión incluidas.
- 33 checks de distribución y 8 de IR correctos.
- 21 tests Python, incluida dirección/flags de ambas ruedas.
- Windows CI run41715826415 (d098e68): compilación/instalación de broker, autoprueba de horizontal, TLS del EXE en dry-run y construcción de ejecutables correctos.
- Compilación Android, firma v3 e instalación ADB verificadas: code16/version0.5.0-alpha.4.

Pendiente confirmación física del desplazamiento en el PC del usuario. El scroll horizontal necesita contenido desbordado lateralmente en la ventana con foco. El periodo de recuperación de 20 minutos se conserva.
