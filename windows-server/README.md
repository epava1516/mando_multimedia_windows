# Servidor propio completo — experimental 0.5.0-alpha.11

Paquete independiente de Remote Mouse para Windows 11. Incluye el servidor de red, zoom visual de Firefox, bloqueo directo y el servicio local para enviar ratón/teclado a una sesión ya iniciada y bloqueada. La APK alfa conserva la opción del servidor oficial.

**Estado:** compilación e instalación/desinstalación del servicio, identidad del pipe y pruebas del protocolo pasan en un runner Windows. La entrada real con la pantalla bloqueada y el zoom visual necesitan confirmación en el PC. No es todavía una versión estable.

## Instalador EXE (recomendado)

Descarga `MandoSetup-0.5.0-alpha.11.exe` desde https://www.sherlockhomeless.net/apps/mando/ y ábrelo con doble clic. Solicita elevación para configurar el servicio, instala los archivos en Program Files y añade **Mando → Mando Server** al menú Inicio. No requiere Python ni comandos de PowerShell.

Cierra Remote Mouse antes de iniciar Mando Server desde Inicio con tu cuenta normal, la IP se detecta automáticamente. Si hay varias redes, selecciona la de casa en la ventana. En Android pulsa Buscar en Wi-Fi, selecciona Mando propio y pega el código mostrado. El servidor queda en la bandeja al cerrar la ventana. Doble clic abre el código; Salir detiene el servidor. Arranca automáticamente, oculto en la bandeja, al iniciar la sesión de la cuenta autorizada.

Para actualizar basta con ejecutar el nuevo instalador: reemplaza el componente de escritorio reconocido y conserva la identidad TLS y autorizaciones en LocalAppData. Para desinstalar usa Configuración → Aplicaciones → Mando Server. Windows CI [41730608599](https://github.com/epava1516/mando_multimedia_windows/actions/runs/41730608599) verificó el arranque/parada del backend y cierre a bandeja, además de inicio automático por usuario, modo oculto, servidor sin broker, instalación, actualización, reglas privadas y desinstalación del EXE. El instalador todavía no tiene firma digital.

El ZIP y los siguientes comandos se conservan como alternativa manual.

## Instalar el paquete completo

1. Extrae todo el ZIP en una carpeta de tu usuario. No hace falta instalar Python si el paquete incluye `MandoServer.exe`.
2. Abre **Windows PowerShell 5.1 como administrador**, en esa carpeta, y ejecuta:

```powershell
Get-ChildItem *.ps1 | Unblock-File
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\Instalar-MandoCompleto.ps1
```

La elevación se usa una vez para instalar el servicio y los ejecutables protegidos en Program Files. No abre puertos ni cambia la política permanente de PowerShell. Autoriza la cuenta que ejecuta el instalador; si para elevar utilizas **otra cuenta administrativa**, obtén antes el SID con `whoami /user` en tu cuenta normal y pasa `-UserSid S-1-5-21-...` al instalador. Solo esa cuenta, en la consola activa, podrá usar el broker.

3. Cierra la consola elevada. Desde tu cuenta **normal**, abre `MandoServer.exe`, o ejecuta `Iniciar-MandoServidor.ps1`. Introduce la IP Wi-Fi del PC (`ipconfig`). Escucha exclusivamente en esa IPv4 privada, TCP1979. Si Windows solicita permiso de firewall, permite la red privada que uses. No hay que instalar MandoLock ni el zoom por separado.
4. Instala la APK **0.5.0-alpha.2 o posterior**. En Mando selecciona **Mando propio (experimental)** y pega una vez el código `mando1:...` que muestra el PC. El código contiene dirección, puerto, huella del certificado y PIN temporal; no lo compartas fuera de tus dispositivos.
5. Después del primer emparejamiento puedes salir y volver a abrir Mando: selecciona el servidor propio y conecta dejando vacío el campo de código. La autorización queda cifrada con Android Keystore. El transporte se cierra al salir; alpha.3 lo recupera automáticamente si vuelves antes de 20 minutos. Nunca se repiten acciones. Desconectar cancela esa recuperación.

El servicio local arranca con Windows, pero el servidor de red se ejecuta en la sesión del usuario y debe estar abierto antes de bloquear. No se ha implementado acceso antes del primer inicio de sesión o tras cerrar sesión, ni control del escritorio UAC, sesiones RDP o de otra cuenta.

## Controles incluidos

- Ratón, clics, scroll vertical/horizontal nativo, Unicode, teclas extendidas, multimedia y volumen de Windows.
- Dos dedos paralelos: scroll. Pellizco: zoom visual alrededor del cursor en Firefox con nuestro componente integrado. Tres dedos arriba: Vista de tareas.
- Bloqueo directo mediante LockWorkStation. Cuando la sesión está bloqueada, el broker restringido atiende ratón, clics, scroll, texto y teclas en Winlogon; no ejecuta gestos de ventanas/zoom allí.
- IR sigue funcionando localmente en el móvil y puede ocultarse desde Ajustes.

Para zoom, coloca primero el cursor sobre contenido de Firefox, lejos de barras y bordes. Los gestos nativos todavía necesitan prueba física. Windows puede promover entrada táctil a ratón: comprueba el punto de zoom entre gestos.

## Emparejamiento y revocación

Hasta cuatro dispositivos registrados y cuatro sesiones simultáneas. PIN de diez minutos y diez intentos/minuto; una autorización guardada permite reconectar aunque el PIN haya caducado. Cada sesión dura hasta ocho horas, con heartbeat y secuencias nuevas por conexión.

En la consola del servidor:

- `R`: nuevo código para emparejar, sin revocar dispositivos existentes.
- `REVOKE`: revoca todos los dispositivos y rota el PIN de forma atómica; las sesiones dejan de aceptar acciones en su siguiente petición.
- `STOP` o Ctrl+C: cierra el servidor de red.

Ajustes → **Olvidar PC propio** borra la autorización de ese móvil; `REVOKE` es la revocación efectiva del servidor. El PC guarda solo hashes de secretos aleatorios, no los tokens en claro. No se guardan contraseñas de Windows ni textos enviados.

## Componentes y límites de confianza

`MandoServer.exe` corre sin privilegios: TLS>=1.2, comprobación por huella exacta del código local, lista cerrada de acciones, longitudes y secuencias estrictas. El broker SYSTEM **no escucha en red**. Su pipe local rechaza clientes remotos, valida SID y sesión y el cliente verifica el PID contra el servicio registrado. Solo los binarios de Program Files pueden ejecutarse con privilegios; el broker no ejecuta código, rutas ni órdenes recibidas por red.

La ruta Winlogon utiliza un trabajador acotado en la sesión autorizada. Solo se inicia con la sesión bloqueada; comprueba de nuevo sesión, bloqueo y escritorio antes de cada SendInput. Los contactos del zoom se liberan/cancelan y las teclas se pulsan y sueltan juntas.

La identidad TLS y el registro de dispositivos viven en `%LOCALAPPDATA%\MandoServer`, fuera del directorio del ejecutable. No compartas esa carpeta. El certificado dura un año y su renovación sigue pendiente de automatizar. Android guarda la autorización con AES-256-GCM y una clave no exportable de Android Keystore. No hay vault de contraseñas.

## Desinstalar

Desde Windows PowerShell como administrador ejecuta `Desinstalar-MandoCompleto.ps1`. Verifica los binarios y quita el servicio y sus archivos reconocidos; conserva archivos ajenos. No borra la identidad TLS ni las autorizaciones del servidor de red. Cierra MandoServer.exe aparte; para revocar sus dispositivos usa REVOKE antes de cerrarlo.

## Desarrollo y pruebas

Si trabajas desde el repositorio sin el ejecutable, instala Python3.11; `Iniciar-MandoServidor.ps1` crea su entorno local y descarga `cryptography==50.0.2` la primera vez. El código se puede ejecutar así:

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe server.py --host 192.168.1.105
```

La IP es un ejemplo. El modo real exige instalar los componentes completos. Para probar protocolo sin entradas, ni bloqueo real:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -m unittest discover -s tests -v
.venv/bin/python server.py --host 127.0.0.1 --dry-run
```

En Linux la identidad de pruebas queda en `.state/`; en Windows usa LocalAppData. La capacidad dry_run impide cualquier entrada y lo indica al cliente. La APK admite solo IP privadas; para conectar la app a una simulación usa la IP LAN del host.

El workflow Windows compila ambos componentes, prueba el instalador y la identidad IPC sin enviar input, construye MandoServer.exe y prueba su TLS en modo simulado. [Contrato](PROTOCOL.md). Antes de sustituir el oficial faltan las pruebas físicas de la [matriz](TEST-MATRIX.md), descubrimiento propio, renovación de certificado y experiencia de inicio automático del proceso de red.

La APK alpha.4 y este paquete añaden los bordes de rueda. Para actualizar el componente de escritorio ya instalado, ejecuta su desinstalador y vuelve a ejecutar Instalar-MandoCompleto.ps1; la identidad TLS y las autorizaciones de LocalAppData se conservan.

Alpha.7 añade portapapeles de texto bajo demanda por TLS. Requiere actualizar MandoServer.exe; no cambia el broker. Solo Windows desbloqueado y hasta 1000 unidades UTF-16, sin sincronización automática ni historial.

## Copiar URLs directamente desde el navegador

En el modo propio se solicita directamente CF_UNICODETEXT a Windows. No se enumeran ni convierten DataObject, HTML o imágenes antes del texto. Por tanto, el formato adicional de un navegador no impide leer la URL si Windows ofrece su representación de texto. Se conserva el límite de 1000 unidades UTF-16.

Para pasar desde el oficial: cierra Remote Mouse en Windows antes de iniciar MandoServer.exe (ambos usan TCP1979); en Android pulsa Desconectar, selecciona Mando propio y pega el código del nuevo servidor. Puedes volver al oficial seleccionándolo de nuevo. El paquete completo requiere la instalación descrita arriba; no basta con actualizar la APK.

El workflow incluye dos pruebas con el portapapeles real de un runner Windows aislado: URL Unicode y URL con DataObject registrado antes de UnicodeText. Utilizan datos sintéticos de example.invalid y vacían el portapapeles de prueba al terminar. Solo se activan con MANDO_TEST_CLIPBOARD=1 en Windows. No inspeccionan el portapapeles del usuario.

## Bandeja, búsqueda y red privada (alpha.10)

El acceso de Inicio abre MandoTray.exe como usuario normal; detecta IPv4 privada en Ethernet/Wi-Fi, inicia el backend sin consola y muestra estado, código, copiar/renovar y reiniciar. Cerrar oculta la ventana; Salir detiene solo su propio proceso backend. Rechaza el arranque elevado y evita dos instancias del mismo usuario. Detecta el conflicto de puerto con Remote Mouse.

El instalador agrega reglas únicamente para MandoServer.exe, TCP1979 y UDP1980, redes privadas y origen LocalSubnet. El desinstalador retira únicamente esas reglas si su programa coincide. La red Wi-Fi de casa debe tener perfil privado en Windows.

El anuncio UDP solo devuelve presencia, puerto y nonce de la petición; nunca PIN, certificado, tokens ni códigos. Android correlaciona el nonce, limita respuestas y conserva el emparejamiento por código completo con pinning TLS. Encontrar un servidor no lo autoriza automáticamente.

## Inicio automático y diagnóstico (alpha.11)

El instalador configura una entrada Run de Windows únicamente en la cuenta autorizada, apuntando al ejecutable protegido de bandeja con --background. El desinstalador elimina solo su propio valor si coincide exactamente. Se conserva la política de ejecutar el servidor como usuario normal. Si la red tarda en estar lista, reintenta automáticamente. Salir detiene la sesión actual; el siguiente inicio de Windows vuelve a abrir Mando.

Abrir MandoServer.exe directamente también redirige a MandoTray.exe. Se reinicia el entorno de PyInstaller para que el backend no reutilice archivos temporales de un lanzador que ya terminó.

La comprobación del broker es una capacidad, no un requisito para escuchar y emparejar en el escritorio normal. Sin broker disponible, no funciona la entrada en la pantalla bloqueada; sus controles de SID, sesión y PID permanecen activos. La ventana muestra Bloqueo pendiente y Diagnóstico indica estado del servicio, coincidencia de la cuenta y código de fallo IPC sin texto del usuario, claves ni tokens. Esto permite diagnosticar el caso físico sin exigir reinstalaciones repetidas.

La separación entre red normal y Winlogon está contrastada con evidencia REA del original: [comparación de arranque](../evidence/ORIGINAL_STARTUP.md). La comparación no demuestra la causa del fallo físico del servicio en el PC del usuario.
