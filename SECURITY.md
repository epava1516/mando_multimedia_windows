# Seguridad

Mando sirve únicamente sus recursos locales en el WebView y rechaza navegación y contenido remoto. El puente nativo permite un conjunto cerrado de acciones; no admite ejecución de comandos arbitrarios. Los perfiles IR son constantes y no se aceptan pulsos ni frecuencias desde la interfaz.

La conexión al servidor oficial valida el saludo y la versión, pero no autentica criptográficamente al servidor. Solo se acepta `nop`; los servidores que requieren contraseña se rechazan. El canal UDP de compatibilidad transmite texto y controles sin cifrar. No se incorpora un vault y no debe usarse para transmitir contraseñas. Limita su uso a una red local de confianza y no publiques sus puertos en Internet.

La app no requiere root, accesibilidad, cámara ni almacenamiento. TRANSMIT_IR es necesario para el emisor físico; IR es opcional. La APK no incorpora binarios originales de otras aplicaciones. Las cinco señales LGTV1 se documentan como datos de interoperabilidad.

`.state/` contiene la clave de firma y su contraseña: nunca se versiona ni distribuye. Los archivos compilados, paquetes ZIP y volcados originales tampoco se versionan. Una actualización del APK requiere la misma clave privada usada al instalarlo.

MandoLock instala un ejecutable por usuario que llama a LockWorkStation y un acceso directo Ctrl+Alt+L. No abre puertos, no guarda credenciales y no instala un servicio.
