# Mando 0.5.0-alpha.3

Recuperación automática al volver dentro de 20 minutos, cancelada por desconexión explícita. No se mantiene tráfico de control en background ni se repiten entradas.

- 14 comprobaciones JVM con reloj simulado: frontera exacta TTL, retorno previo al plazo, entrada obsoleta, cancelación, conexión tardía, intentos fallidos sin renovación, restauración de metadatos y sesión solo IR.
- Prueba CDP/UI de pausa/recuperación: formulario oculto mientras espera, recuperación correcta, formulario tras fallo y rechazo de acciones/errores antiguos sin deshacer una recuperación. Regresión de gestos, IR y selección de servidor incluida.
- 33 comprobaciones de distribución sin scroll y 8 de visibilidad IR correctas.
- Compilación, firma v3 e instalación ADB verificadas: versionCode15, versionName0.5.0-alpha.3.
- El usuario confirmó físicamente que al salir y volver recupera la conexión sin pulsar Conectar, usando su servidor oficial.

El plazo completo se comprueba con reloj simulado, no esperando veinte minutos reales. Recreación de proceso/arranque y la ruta con servidor propio están implementadas, pero no se han confirmado físicamente en esta versión. El servidor Windows alpha.2 no cambia.
