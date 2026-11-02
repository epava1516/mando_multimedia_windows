# Mando 0.5.0-alpha.2

- 20 tests Python: TLS real, controles simulados, validación, límites, secuencias, persistencia por hashes, reconexión tras caducar PIN y revocación de sesiones.
- 5 comprobaciones JVM de pinning; 33 comprobaciones de distribución y 8 de visibilidad IR; regresión táctil y selector propio/oficial con puente simulado.
- CI Windows ejecutado correctamente: compilación C#, ABI/autopruebas, parseo PowerShell, instalación/desinstalación real del servicio en runner y conexión al pipe verificando PID contra SCM.
- EXE Windows construido con Python3.11 y PyInstaller6.22.3. Smoke test del ejecutable real: TLS, huella, emparejamiento, acción en dry-run y apagado. Artifact del run41478275523, commit7989dde.
- APK compilada y firma v3 verificada. La persistencia Android Keystore está implementada y compilada, pendiente de validación física en el móvil/PC. No se instaló esta alfa automáticamente.

Pendiente: recepción física del ratón/teclado en Winlogon, zoom y Vista de tareas en el PC del usuario, con la matriz windows-server/TEST-MATRIX.md. El servicio solo cubre la consola autorizada ya iniciada y bloqueada; no primer login, UAC ni RDP.
