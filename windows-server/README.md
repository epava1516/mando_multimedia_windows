# Servidor propio de Mando — experimental v1

Servidor independiente para Windows 11. No utiliza ni requiere Remote Mouse. La APK 0.5.0-alpha.1 incorpora un selector para usarlo; las APK estables anteriores siguen utilizando su servidor oficial.

## Funciones preparadas

Ratón, clics, scroll, texto Unicode, teclado, multimedia y volumen mediante SendInput; Vista de tareas mediante Win+Tab y bloqueo directo mediante LockWorkStation. El zoom visual usa el complemento propio opcional MandoTouchZoom ya incluido en el repositorio. IR sigue ejecutándose en el móvil.

**Estado:** protocolo y transporte probados con TLS real en Linux y un adaptador sin entrada. APK compilada y firmada. El servidor no se ha ejecutado en Windows desde este entorno: entrada real, bloqueo y zoom necesitan prueba en el PC.

## Iniciar en Windows

1. Instala Python 3.11 con el lanzador `py` y extrae esta carpeta en una ubicación de tu usuario.
2. Abre Windows PowerShell en la carpeta, revisa los archivos y ejecuta:

```powershell
Get-ChildItem *.ps1 | Unblock-File
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\Iniciar-MandoServidor.ps1
```

El script crea un entorno virtual local e instala la dependencia fijada `cryptography==50.0.2`; requiere Internet para esa primera instalación. No cambia la política permanente de PowerShell.

3. Introduce la IP Wi-Fi del PC (`ipconfig`). El servidor escucha exclusivamente en esa IPv4 privada, TCP **1979**, y muestra un código `mando1:...`. Si Windows solicita permiso de firewall, permite únicamente la red privada que vayas a usar. No crea reglas ni abre puertos del router.
4. En la APK alfa, elige **Mando propio (experimental)** y pega el código completo que muestra ese PC. Incluye IP, puerto, huella SHA-256 del certificado y PIN. Solo se envía el PIN después de verificar el certificado por TLS. El código no se guarda en la app ni se imprime en registros de red.
5. Las sesiones duran hasta ocho horas y terminan al salir de Mando o cerrar el servidor. El código admite hasta cuatro sesiones simultáneas y caduca en diez minutos. Para reconectar con un código nuevo, escribe **R** y Enter en la consola; conserva las sesiones activas. No hay reconexión ni repetición automática de acciones.

Comando manual, desde esta carpeta:

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe server.py --host 192.168.1.105
```

La dirección es un ejemplo: usa la de tu propio PC. El servidor oficial puede seguir abierto porque utiliza otro puerto. El selector de búsqueda Wi-Fi corresponde todavía al servidor oficial; el propio se localiza mediante el código del PC.

## Alcance de escritorio

Esta primera versión se ejecuta dentro de la sesión normal del usuario. Puede bloquear Windows, pero **todavía no controla la pantalla bloqueada ni el escritorio UAC**. No lo ejecutes como administrador para intentar resolverlo. La opción del servidor oficial se conserva para ese uso mientras se desarrolla un componente específico de escritorio seguro.

Para zoom instala [MandoTouchZoom](../windows-zoom/README.md); en el paquete distribuido también se incluye dentro de `windows-zoom/`. Solo se ejecuta su ruta fija `%LOCALAPPDATA%\MandoTouchZoom\MandoTouchZoom.exe` con `in` u `out`; no existe ejecución arbitraria de rutas o comandos recibidos por red. La capacidad de zoom se anuncia si el archivo está disponible; su comportamiento real en Firefox sigue pendiente de prueba.

## Datos y seguridad

TLS 1.2 o posterior, certificado verificado por huella exacta del código que muestra tu PC y emparejamiento limitado. El protocolo JSON v1 tiene longitudes máximas, secuencias estrictamente crecientes, límites de intentos/acciones/conexiones y una lista cerrada de acciones. El ACK confirma que Windows aceptó la llamada; no garantiza la reacción de la aplicación con foco.

La identidad privada está en `.state/` y no se incluye en Git ni en los ZIP. No compartas esa carpeta. El certificado dura un año; su renovación queda pendiente de automatizar. No se almacenan textos, códigos ni credenciales de dispositivos; el código se borra del formulario después de conectar y al abandonar la app. No incluye vault ni gestión persistente de dispositivos emparejados.

## Pruebas sin controlar un equipo

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -m unittest discover -s tests -v
.venv/bin/python server.py --host 127.0.0.1 --dry-run
```

El modo `--dry-run` no produce entradas ni bloquea el ordenador y lo anuncia como capacidad al cliente. La APK acepta únicamente IP privadas, por lo que para probarla con un servidor en simulación usa su IP LAN en vez de loopback.

## Trabajo pendiente antes de sustituir el oficial

- Validación completa en Windows, instalador empaquetado y ejecutable sin Python externo.
- Servicio/broker por sesión y trabajador restringido para el escritorio seguro, con permisos y canal local revisados. No basta con iniciar este servidor con privilegios altos.
- Emparejamiento persistente con revocación y secretos protegidos por Android Keystore/DPAPI.
- Descubrimiento de servidores propios y renovación del certificado.

Contrato versionado en [PROTOCOL.md](PROTOCOL.md).
