# Remote Mouse frente a Mando: bloqueo y UAC

Comparación estática del 17-11-2026. No se ejecutó el instalador original ni se enviaron órdenes a Windows. No prueba el estado de servicios del PC del usuario. No se modificó la implementación para ampliar privilegios.

## Identidad y evidencia nueva

Descarga oficial: https://www.remotemouse.net/downloads/RemoteMouse.exe . SHA256 del instalador: `41726ab97685867865bcd796818458feac0836db8d168b83cd6b1ebf3b607613`. Extraído con innoextract 1.9. El instalador actual difiere del analizado en ORIGINAL_STARTUP.md; no reutilizar sus hashes como identidad actual.

- GUI SHA256 `d9bd6af05b47d02755114b9f2bec5388696e204b194ce1e8513e238e093787d3`, MVID `0728c0d2-7d51-4bd2-88fc-7aa46eb045c6`. Miembros REA `ev_d824a2a0fa16ec8fc8981aa215f53b77ddb05f11069188930017d43d17bce610`.
- Servicio SHA256 `565e0741516af54b98e42f03c26563eabf9948eb683930ded44db2c3cd901912`, MVID `a5026868-8cea-4521-93ce-8704d01dcff0`. Miembros REA `ev_d369cdf48832cbb27fbfa3da7f00d31f7633aa5197677bc25ae8f2db1620993f`.
- Core SHA256 `71f5397f9e5b6d9c4577b07735c2254887067db0b6b111b61232d791436e2191`, MVID `45059904-629b-4a1a-9e33-6ce944a65ca1`. Miembros REA `ev_96960644bca60ed085e3872b5528e67802d91036e8b06ddfd2c56ca940ffcbff`.
- Declaraciones nativas GUI `ev_52231bb6393c5d0e76214301ec63aa0b4dad779b7ec448188d1278432444bc84`; Core `ev_ba76889d29f4fe57dff4fffd8d1be81726980ef2a0d2789197f983e669864f4e`.
- Bundle íntegro local de esta sesión: `/tmp/mando-original-compare/rea-comparison-evidence.json` (temporal; no contiene prueba de ejecución en Windows).

## Hallazgos

### 1. UAC rechazado expresamente por Mando — confirmado en fuente

Original GUI `A.e.A`, token 0x0600007d, IL134 llama `CheckIfDefaultDesktop` (0x060000eb); si devuelve falso, IL141–182 reenvía la trama por UDP al puerto local11978 y retorna. Core `RemoteMouse.MainForm` 0x06000004 crea listener11978; 0x06000006 recibe datagramas. El servicio OnStart 0x06000007 inicia trabajo en segundo plano; 0x0600000b llama al lanzamiento nativo primero con false y opcionalmente true. El helper y selección Winlogon/Default están documentados históricamente en ORIGINAL_STARTUP.md; no se redesensambló la DLL nativa actual en esta pasada. La ruta observada se basa en escritorio de entrada, no demuestra por sí sola una prueba de UAC exitosa.

Mando `windows_input.py:100` también deriva entrada cuando el escritorio no es Default. Sin embargo, `desktop/MandoDesktopBroker.cs:239` rechaza si `Locked(session)` es falso; el worker repite esa condición en línea265 además de exigir Winlogon. Un aviso UAC no exige sesión bloqueada. Esto explica que nuestra implementación no dé soporte a UAC; no hay que relajar identidad/SID/sesión ni desactivar el escritorio seguro para solucionarlo.

### 2. Disponibilidad aparente sin probar el worker — confirmado

Broker línea235 responde available=true sin EnsureWorker, duplicación de token, CreateProcessAsUser ni conexión al worker. Python desktop_bridge.available refleja esa respuesta. Puede declararse disponible mientras falla la creación o entrada de bloqueo. El status debe distinguir servicio accesible, worker preparado y capacidad de entrada comprobada; un sondeo pasivo no debe inyectar teclas.

### 3. Presupuesto temporal incompatible — confirmado; efecto físico posible

`desktop_bridge.py:98`: entrada tiene3s totales. Broker línea208 permite5s para conectar al worker recién creado; línea240 añade hasta3s para su respuesta. El worker se crea bajo demanda (línea240), frente al lanzamiento de Core desde el servicio original. Un arranque lento puede agotar la espera del cliente antes de completar el arranque. Esto puede explicar una acción rechazada en frío, pero no prueba que sea la causa de este PC. No reintentar entradas automáticamente, especialmente clics o texto de login.

### 4. Se pierde el motivo del fallo — confirmado

Broker línea243 captura excepciones y para el worker; línea268 oculta errores de ejecución. No expone etapa/Win32Error al servidor. La app termina mostrando rechazo genérico y cierra la conexión. Hace falta diagnóstico acotado de creación de token, asignación de sesión, creación de proceso, conexión IPC y SendInput, sin registrar textos, contraseñas, tokens ni comandos arbitrarios.

### 5. Recuperación del worker — diferencia, no causa demostrada

Worker línea261 termina tras30s sin entrada; siguiente uso exige nuevo arranque. Mando no comprueba ese ciclo mediante su status. El watchdog del original inspeccionado históricamente no equivale a disponibilidad verificada ni es necesariamente más robusto.

## Límites y siguiente trabajo

No se encontró un error confirmado de ABI en INPUT, índice7 del PID de SERVICE_STATUS_PROCESS o offset16 de SessionFlags WTSINFOEX; no atribuir el fallo a estos sin evidencia Windows. Los controles de SID, sesión activa, PID del servicio y pipe local deben preservarse.

Respondido: discrepancia de política UAC, arquitectura de arranque y fallos de readiness/deadline/diagnóstico. Parcial: equivalencia completa de escritorio seguro con la versión original actual. Pendiente: causa exacta de bloqueo en el PC real y reproducción de UAC allí. Próximo cambio aconsejado: diagnóstico por etapas y comprobación de disponibilidad real, presupuestos coherentes, y diseño explícito de UAC por separado. Validación final requiere Windows11 con el servicio instalado y prueba física de bloqueo/desbloqueo.
