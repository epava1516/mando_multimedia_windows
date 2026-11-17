# Preparación de publicidad (desactivada)

`config.json` contiene identificadores públicos de AdSense y AdMob vacíos. No incluye secretos. Ambas redes están desactivadas; poner enabled=true no es suficiente para activarlas.

## Android

- `python3 android/build.py --variant personal`: paquete existente net.mando.personal, conserva los datos y su firma. Sin SDK de anuncios, Advertising ID ni solicitudes publicitarias.
- `python3 android/build.py --variant public`: paquete independiente net.mando.publicapp, nombre Mando Pública y salida android/mando-public.apk. Puede coexistir con la personal; no hereda su emparejamiento. En esta preparación tampoco incluye SDK ni anuncios.
- La compilación falla si se intenta habilitar AdMob antes de incorporar su SDK y el adaptador de consentimiento. Edition se genera por variante y no es una preferencia modificable desde la interfaz.
- Ubicación prevista: banner nativo únicamente en Ajustes. Nunca sobre el touchpad, teclado, controles multimedia, escáner o introducción de texto. No intersticiales ni anuncios al conectar/desconectar.
- Antes de activar: resolver dependencias oficiales mediante un build que gestione AAR y manifest merging, añadir Google Mobile Ads y UMP, validar app ID y unidad, solicitar consentimiento y comprobar canRequestAds antes de inicializar/cargar; permitir revisar privacidad. Usar anuncios de prueba durante QA. Mantener estos SDK completamente fuera de personal. El build manual actual no resuelve esas dependencias: no se ha simulado una integración funcional.

## Web

La landing tiene un espacio oculto que no ocupa altura. `web/adsense.js` es una preparación fuera de la raíz pública: valida identificadores y rechaza activación incompleta. No se referencia ni se carga en la landing; la CSP sigue prohibiendo scripts de anuncios. No hay cookies ni conexiones de publicidad.

Antes de activar: registrar el sitio y sus identificadores, implementar CMP compatible, revisar privacidad y CSP, servir ads.txt con el editor autorizado y cargar AdSense solamente cuando proceda según consentimiento. Un bloque identificado como Publicidad, separado de botones de descarga. No publicar IDs ficticios en ads.txt.

## Publicación

No sustituir el APK personal por el público en los enlaces existentes. Añadir una descarga pública diferenciada cuando su integración esté terminada. El usuario creador conserva su variante personal sin publicidad permanentemente.

Referencias: https://developers.google.com/admob/android/quick-start y https://developers.google.com/admob/android/privacy ; https://support.google.com/adsense/answer/9042142

## Compra para quitar publicidad

[Diseño de Google Play Billing](PLAY-BILLING.md): producto no consumible `mando_remove_ads`, precio desde Play y restauración para la cuenta compradora. Preparación desactivada: aún no hay cobros, SDK, backend verificador ni interfaz de compra operativa.
