# Mando 0.5.0-alpha.1

Servidor propio experimental con cliente Android integrado y opción oficial conservada.

- 16 tests Python, incluidas conexiones TLS reales en loopback con adaptador que no produce entrada: emparejamiento, rechazo previo a autorización, código incorrecto/caducado, rate limits, tipos/rangos/Unicode, secuencias repetidas, rechazo de texto sin TLS y certificado no confiable. Pruebas del adaptador verifican bloqueo directo, protección del escritorio seguro y comando de zoom restringido.
- 5 comprobaciones JVM del pinning: certificado correcto, huella incorrecta, copia inmutable del pin, cadena vacía y rechazo de certificados cliente.
- 33 comprobaciones de distribución sin overflow y 8 del ajuste IR.
- Regresión táctil Chromium/CDP ampliada con selección del modo propio, código enviado al puente correcto y borrado tras conectar. Sin controles físicos.
- Compilación Android y firma v3 verificadas. No se ha instalado esta alfa automáticamente sobre la app del usuario.

Pendientes: ejecución Windows del servidor/launcher, entrada física y nuevo cliente Android sobre Wi-Fi real. El escritorio bloqueado/UAC, descubrimiento propio y dispositivos persistentes quedan expresamente fuera de esta primera base. La release 0.4.1 permanece estable.
