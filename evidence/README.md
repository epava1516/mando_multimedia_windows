# Notas de interoperabilidad

## Servidor Windows

Mando se desarrolló frente al servidor oficial Remote Mouse 5.31. El saludo TCP tiene prefijo SIN y longitud decimal de tres bytes; se comprueba Windows, versión >=530 y modo nop. TCP se conserva para supervisar la conexión.

Las acciones se envían como un frame UTF-8 completo por datagrama UDP 1978, con tipo y longitud decimal de tres caracteres. El receptor TCP analizado no despacha teclas; el dispatcher UDP también contempla el escritorio de bloqueo y reenvía al componente oficial de Winlogon por loopback 11978 cuando corresponde. Por eso ratón y teclado usan UDP. No se crean reglas de firewall ni se omite autenticación.

La selección UDP coincide con la app Android original para versiones de servidor >=280. Se comprobó el formato de texto, combinaciones y multimedia mediante análisis estático; el usuario confirmó el comportamiento con su PC, también bloqueado. No hay ACK ni reintentos automáticos de acciones.

## IR LGTV1

[Extracción y conversión del perfil](LEAN_IR.md). [Datos mínimos de los cinco comandos](lgtv1-exact-codes.json). El usuario confirmó que todos responden en su televisor.

Las notas citan hashes e identificadores REA del análisis. Los APK originales, bases de datos completas y volcados de herramientas se conservaron localmente durante el desarrollo y no se redistribuyen aquí.
