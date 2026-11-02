# Lean Remote: extracción estática del perfil IR LGTV1

## Perfil integrado

Datos mínimos exactos: [lgtv1-exact-codes.json](lgtv1-exact-codes.json).

- Perfil: `LGTV1`, marca `LG`, dispositivo `TV`.
- Botones: `POWER`, `VOLUME_UP`, `VOLUME_DOWN`, `MUTE`, `EXIT`.
- Carrier: **38400 Hz** en la columna SQLite y en el encabezado empaquetado de cada botón.
- Cada patrón contiene **72 duraciones positivas en microsegundos**, alternadas como patrón IR original; conservar las 72 entradas completas, incluidas pausas finales/repetición.
- JSON: `buttons[KEY].pattern_us` y `buttons[KEY].frequency_hz`.
- Los cinco patrones obtenidos de la base actual coinciden **entrada por entrada** con los int32 microsegundos de la base legacy. No se reconstruyeron a partir de comandos NEC supuestos ni se normalizaron tiempos.

El análisis fue estático. Tras la integración nativa, el usuario confirmó que los cinco botones funcionan en su televisor.

## Artefacto y origen de datos

APK instalado suministrado: `/tmp/lean-remote-analysis/base.apk`, paquete `co.leanremote.universalremotecontrol.remotecontrol`, SHA-256 `d71dd910c7667471656a9afe39e7686556755bc2f827ad0386886d8087830014`, tamaño14383970 bytes.

El ZIP APK contiene ocho assets. Solo se abrieron los dos ZIP de bases de datos; no se leyeron las credenciales/certificados de otros assets:

| Asset | Miembro interno | Formato | SHA-256 ZIP |
|---|---|---|---|
|assets/databases/visio.zip|visio|SQLite3, 22614016 bytes|9baca95d9744bb86a0e4ef98d2de15336fccf98b26d9fa9fdc15e2c29387ab2c|
|assets/databases/legacy_v52.zip|legacy_v52|SQLite3, 13545472 bytes|5d41f6e85e84d55e2c5d98063b35087aba0c03446360a771c685bb49438d05bc|

Bases extraídas en `/tmp/lean-remote-analysis/visio.db` y `legacy.db`, abiertas con sqlite3 en `mode=ro&immutable=1`. Huellas DB en `lgtv1-proof.json`: visio607f0ddb7f05f52d0d1a1d626adf62b6c0dfc4831cadc069138df0910d03426a; legacy e443b4699b2d63cf7146f577bb9f39751f51a5bf133d94f999f516fb8758dc36.

Schema relevante: `remote(fragment,button_fragment,device,brand,frequency,main_frame)` (visio añade `_id`). Selección exacta y parametrizada de `fragment='LGTV1'` y los nombres de botón de la tabla siguiente; cada selección tiene exactamente una fila con brandLG/deviceTV. No se usaron coincidencias por prefijo o por otra marca.

| Clave propia | button_fragment exacto | rowid visio | rowid legacy | Duraciones | Suma microsegundos |
|---|---|---:|---:|---:|---:|
|POWER|Power|29279|4497|72|218556|
|VOLUME_UP|Volume_Up|29277|4495|72|218582|
|VOLUME_DOWN|Volume_Down|29286|4504|72|218582|
|MUTE|Mute|29260|4478|72|218582|
|EXIT|Exit|29262|4480|72|218556|

## Formatos y conversión exacta

Clase original `co.leanremote.universalremotecontrol.remotecontrol.E`:

- `<clinit>` declara firmas byte[] `IRU\0`, `IR1\0`, `IRZ\0` (smali retenido por REA).
- `p(byte[])` reconoce IRU, lee carrier y cantidad como ushort little-endian en offsets4 y6; cada tick se lee mediante `ByteBuffer.getShort()` y se multiplica por el período entero.
- `c(int,byte[])` usa el carrier recibido como parámetro y aplica el mismo cálculo para generar texto; exige longitud exacta de los ticks.
- `h(byte[])` decodifica IR1 como int32 little-endian, sin transformación adicional.

Formato IRU de estos cinco blobs, todos152 bytes:

| Offset | Bytes | Valor |
|---:|---:|---|
|0|4|49 52 55 00 = IRU NUL|
|4|2|00 96 LE16 = 38400 Hz|
|6|2|48 00 LE16 = 72 ticks|
|8|144|72 ticks int16 little-endian|

**La división es entera:** `period_us = 1000000 / frequency`, después `pattern_us[i] = signed_tick[i] * period_us`. Para38400 Hz el período usado por Lean es26 microsegundos. **No** usar `round(tick*1000000.0/frequency)` ni redondear el período flotante: eso cambiaría el patrón original.

Ejemplo Power: ticks `[345,171,23,19,23,19,23,62,…]` → `[8970,4446,598,494,598,494,598,1612,…]` microsegundos. Volume_Up empieza `[8996,4420,598,494,598,520,572,1612,…]`.

Comprobaciones completadas para los cinco botones: carrier columna=carrier encabezado=38400; count72; blob size8+2*count; todos los tiempos positivos; array convertido exactamente igual a IR1 legacy. La igualdad cruzada es evidencia adicional frente a errores de endianness/unidades.

## Evidencia REA y archivos reproducibles

- `lean-classes-evidence.json`, búsqueda de clases propias: `ev_9ac572e1d7abe3126234aa6252b3c9e38799f194e99a3e2ee90c811c4aaed9cd`.
- `ir-codec-class-evidence.json`, inventario de E: `ev_b07332ac24f58ab5af7c986403e82155e1c89db8a1e0775a0068b4742196a3d3`.
- `ir-codec-p-evidence.json`, IRU→int[]: `ev_8852feedb9913283fd628842fa2469a8fc019dd0edd0794479296cf8c260dc82`.
- `ir-codec-h-evidence.json`, IR1 int32LE: `ev_39c510c6038716d115590c69efb92eb1f88c6e4084d27f07bf4dc91cac8fdd79`.
- `ir-codec-c-evidence.json`, frecuencia externa y división entera: `ev_4dad0df44ddede1ee36eb69d61985ebf74c5604586a283a1d725dfdd9bda61d2`.
- `ir-codec-clinit-evidence.json`, firmas mágicas: `ev_6f867f8868d8bdfe13981127e8d0bf97b348fd361574167804a72366d4ed1e8c`.
- `ir-codec-c-refs-evidence.json`: `ev_290dc5c665c0bb3134498bc1308b0d222bf92c67615ab926057330253e9cc24c`, llamadas directas del lector de cursor D.P (DEX offset23) y FragmentLoadTempleteActivity.k (offset41) hacia E.c. Estas relaciones estáticas vinculan el convertidor con consumidores de filas de la base; no son ejecución observada.
- Datos exactos y hashes: `lgtv1-exact-codes.json`, `lgtv1-source-data.json`, `lgtv1-proof.json` bajo `/tmp/lean-remote-analysis`.

No se alteró la base, no se editó la aplicación Android y no se transmitió señal alguna. La integración nativa y prueba física corresponden al agente principal y al usuario.
