# Arranque del servidor original frente a disponibilidad de Winlogon

Investigacion estatica con evidencia REA retenida; no se ejecutaron binarios, entradas remotas ni consultas al PC del usuario. No demuestra el estado real de su instalacion.

## Evidencia

- RemoteMouse.exe SHA256 `e9b98e6cce2cb7dbdaec44828aafd6be17c5ffe30f24b17bcaa95edf5d2cd4a7`, miembros completos REA `ev_21ab3f299b569e851d29851c9630a2af107cfe94a917f3b52bef3acd4d1182b4`; extraccion local `/tmp/remote-mouse-analysis/windows-members-local.json`.
- RemoteMouseService.exe SHA256 `f41f56d631e8ea2f8d467a4a52a2d8b6f93fe0393184656e25a7d9c0ce644761`, miembros REA `ev_5efda303d4bf6f9032da628b828cd0f7a184eed290dfdce0738c296c272df0bf`; extraccion local `/tmp/remote-mouse-analysis/service-members-local.json`.
- windows_api.dll SHA256 `af15d114d93df4cc935456105d3379b59668b21cf6c8cb769ffa936d5ac4a5c1`; desensamblado retenido `create-rm-process.asm` y analisis previo `WINDOWS-SERVER.md`, `LOCK_DESKTOP.md`.

## El arranque normal no depende de un preflight de Winlogon

`RemoteMouse.Form_Main..ctor`0x0600001a inicializa formulario/icono y oculta NotifyIcon (IL18–25). En su manejador de carga0x0600001b, oculta la ventana (IL11–16), prepara credenciales (IL43–48) y llama al arranque de red0x0600001e (IL49–54). Este ultimo inicia TcpServer.Start y UdpServer.Start inmediatamente (IL0–15), y luego configura opciones/broadcast. No comprueba disponibilidad de servicio, proceso Core, SID, desktop Winlogon o respuesta de broker antes de abrir esos canales.

El callback0x06000036 activa NotifyIcon.set_Visible(true) (IL0–11) e inicia el servidor Dock en otra llamada (IL12–23). La aplicacion normal y el servicio son componentes diferentes. Su arquitectura permite que el proceso del usuario mantenga la entrada normal aunque falle la parte destinada al escritorio de bloqueo. Esto respalda separar en Mando disponibilidad normal y disponibilidad de bloqueo; no respalda omitir controles de identidad del broker.

## Inicio automatico y servicio

Preferencias `LoadAutoStartSetting`0x06000122 lee `AutoStart` de AppRegistry.GetLocalMachineString y compara con `yes` (IL0–25). `ChkAutoStart_Changed`0x06000128 guarda `yes` o null para esa misma clave mediante SetLocalMachineString (IL46–62). Las cadenas son literales #US0x70000049=`AutoStart` y0x7000005d=`yes`. El setter AppRegistry.SetLocalMachineString0x06000236 usa la subclave `SOFTWARE\\Remote Mouse` (helper0x0600046d), y guarda/elimina el valor; este camino no registra una entrada Windows Run. Esto confirma una opcion persistente, pero no establece el tipo de inicio SCM ni todas las acciones del instalador. No se debe afirmar que el original use RegisteredRun: en los caminos inspeccionados la GUI cambia su preferencia HKLM y el servicio consulta una preferencia antes del segundo lanzamiento; el registro Run del instalador no fue demostrado.

En el servicio, OnStart0x06000007 crea un hilo de fondo (IL7–31), cuyo callback0x0600000e llama A(true), token0x0600000b. Este obtiene el directorio del ensamblado y hace una llamada nativa CreateRMProcess con argumento bool false (IL28–40), luego opcionalmente otra con true segun una preferencia HKLM (IL41–73). Ambos valores de retorno son descartados mediante `pop`: no se propaga disponibilidad confirmada al servidor normal.

La creacion nativa utiliza seleccion entre Winsta0\\Winlogon y Winsta0\\Default (CreateRMProcess VA0x100011a0, seleccion0x1000129a–0x100012af, CreateProcessAsUserA0x10001350). Esto es lanzamiento en un escritorio, no una simple aplicacion de bandeja que gana esas facultades al reinstalarse. Tokens, permisos, sesion y escritorio efectivos siguen siendo condiciones de ejecucion. El analisis no prueba privilegios o sesion del servicio en el PC del usuario.

## Recuperacion: watchdog limitado, no readiness verificado

A(true) inicia un watchdog de fondo0x06000009 (IL74–101). Este:

1. Duerme1000ms y consulta dos grupos de procesos con GetProcessesByName (IL0–31).
2. Sale si se marco parada (IL32–40).
3. Si cualquiera de los dos grupos tiene procesos, vuelve a esperar (IL41–47).
4. Solo cuando **ambos grupos estan vacios**, llama A(false) para relanzar (IL49–55).

No equivale a comprobar handshake, capacidad de inyeccion, SID, sesion o PID esperado. Tampoco recupera cada componente ausente individualmente: la presencia de uno basta para evitar el relanzamiento conjunto. OnSessionChange0x06000006 termina sin iniciar o recuperar procesos (IL0–16). Por tanto no debe describirse al original como una referencia de readiness estricto o recuperacion completa.

El dispatcher UDP normal A.e.A0x0600007d comprueba el escritorio de entrada y reenvia frames a loopback11978 cuando no es Default (IL134–180; evidencia detallada LOCK_DESKTOP.md). Es envio UDP, no confirmacion de que un helper recibio o ejecuto el comando. No se encontro en esos caminos un preflight de Core que condicione el arranque de los listeners normales, ni una respuesta de salud que deba imitarse.

## Diferencia relevante para Mando

Si `secure_desktop_available()==false` abortaba el main antes de iniciar bandeja/listener normal, Mando acoplaba dos capacidades que el original mantiene separadas. Reinstalar los mismos binarios no corrige esa condicion: hace falta que el proceso normal pueda arrancar y declarar por separado que bloqueo esta pendiente/no disponible. Un arranque normal satisfactorio no autoriza mostrar el broker como listo.

Los controles propios de SID/sesion/PID del broker aportan una validacion que el watchdog nominal del original no demuestra. Deben conservarse; el diagnostico debe identificar cual falla y distinguir proceso normal funcionando de broker no disponible. Un indicador no listo puede reflejar esa condicion legitima en lugar de un fallo de reinstalacion. No hay evidencia suficiente para atribuir la situacion concreta del usuario a una causa de permisos, identidad o sesion sin sus comprobaciones locales.
