# Seguridad

Mando sirve únicamente sus recursos locales en el WebView y rechaza navegación y contenido remoto. El puente nativo permite un conjunto cerrado de acciones; no admite ejecución de comandos arbitrarios. Los perfiles IR son constantes y no se aceptan pulsos ni frecuencias desde la interfaz.

## Servidor oficial

La conexión al servidor oficial valida el saludo y la versión, pero no autentica criptográficamente al servidor. Solo se acepta `nop`; los servidores que requieren contraseña se rechazan. El canal UDP de compatibilidad transmite texto y controles sin cifrar. No se incorpora un vault y no debe usarse para transmitir contraseñas. Limita su uso a una red local de confianza y no publiques sus puertos en Internet.

La app no requiere root, accesibilidad, cámara ni almacenamiento. TRANSMIT_IR es necesario para el emisor físico; IR es opcional. La APK no incorpora binarios originales de otras aplicaciones. Las cinco señales LGTV1 se documentan como datos de interoperabilidad.

`.state/` contiene la clave de firma y su contraseña: nunca se versiona ni distribuye. Los archivos compilados, paquetes ZIP y volcados originales tampoco se versionan. Una actualización del APK requiere la misma clave privada usada al instalarlo.

MandoLock instala un ejecutable por usuario que llama a LockWorkStation y un acceso directo Ctrl+Alt+L. No abre puertos, no guarda credenciales y no instala un servicio.

## Servidor propio experimental

TLS>=1.2 y pinning SHA-256 de la identidad mostrada por el PC, sin trust-all ni degradación automática. PIN temporal y límites de intentos/sesiones/frames; secuencias estrictas y ninguna repetición de input tras un error.

Las autorizaciones son secretos aleatorios de 256 bits: Windows almacena hashes y Android cifra el perfil con AES-256-GCM y una clave no exportable de Android Keystore. No son contraseñas de Windows. REVOKE elimina dispositivos y rota el PIN de forma atómica; cada petición comprueba que su dispositivo sigue autorizado. El cliente valida de nuevo el certificado al reconectar.

El servidor de red no se eleva. El broker SYSTEM está en Program Files con ACL protegida, no escucha en red y solo acepta el SID autorizado de la consola activa. Su pipe rechaza acceso remoto y su PID se verifica contra SCM. El trabajador usa exclusivamente la sesión/Winlogon que decide el servicio, con canal privado SYSTEM, y valida bloqueo/escritorio antes de inyectar. Solo hay acciones de entrada permitidas; no rutas ni ejecución arbitraria de órdenes. El zoom ejecuta una ruta fija como usuario normal.

La instalación privilegiada se hace de forma explícita con Instalar-MandoCompleto.ps1. UAC, otras sesiones, RDP y primer login quedan fuera de esta fase. Compilación e instalación IPC pasan en CI; entrada física Winlogon sigue pendiente. La identidad TLS y los hashes viven en LocalAppData/MandoServer y no se distribuyen.
