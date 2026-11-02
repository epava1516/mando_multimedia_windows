# Ajuste opcional de bloqueo de Windows

Mando controla el ratón y multimedia con el servidor oficial. En el PC probado, el atajo Win+L inyectado no bloquea, aunque el físico funciona. No encontramos un comando de bloqueo fiable en el servidor.

Este ajuste propone otra ruta: un programa local mínimo que llama directamente a `user32!LockWorkStation`, y un acceso directo de la sesión del usuario con **Ctrl+Alt+L**. La APK solo envía ese atajo después de que confirmes haber instalado el ajuste. No hay servicio nuevo ni puerto de red, no almacena contraseñas y no requiere administrador. No se ejecuta el bloqueo durante la instalación.

## Instalación en el PC Windows 11

1. Revisa `Instalar-MandoBloqueo.ps1` y ejecútalo una vez con **Windows PowerShell 5.1** desde tu sesión normal. Si Windows marca el archivo descargado, revisa su contenido y usa Propiedades → Desbloquear únicamente para este archivo, sin cambiar la política de ejecución del sistema.
En Windows PowerShell, desde la carpeta extraída y después de revisar el archivo:

```powershell
Unblock-File .\Instalar-MandoBloqueo.ps1
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\Instalar-MandoBloqueo.ps1
```

`RemoteSigned` solo se aplica a ese proceso; no cambia la política permanente del sistema. Si una política de la organización impide ejecutarlo, conserva esa restricción y comunica el error.

2. El script compila el programa en `%LOCALAPPDATA%\MandoLock`, crea `Mando - Bloquear PC.lnk` en tu menú Inicio y asigna Ctrl+Alt+L. Si ese atajo está ya asignado en los accesos directos examinados, se detiene sin sustituirlo.
3. Comprueba Ctrl+Alt+L en Windows. Puede que Explorer necesite unos segundos para registrar el acceso directo.
4. En Mando, pulsa el candado y confirma **Ya está instalado**. Después pulsa otra vez el candado para bloquear.

El usuario **confirmó el bloqueo en su Windows 11** después de instalar el ajuste. No se ejecutó Windows desde el entorno de compilación Linux. Windows puede aplicar políticas a los atajos o al bloqueo; el resultado debe comprobarse en el PC. La API solicita el bloqueo de forma asíncrona y requiere una sesión en el escritorio interactivo. [Documentación de Microsoft](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-lockworkstation).

Para retirarlo ejecuta `Desinstalar-MandoBloqueo.ps1` y desactiva el ajuste desde la configuración de bloqueo de la APK. Se eliminan solo los archivos creados por este instalador. El servidor oficial y tus aplicaciones quedan intactos.
