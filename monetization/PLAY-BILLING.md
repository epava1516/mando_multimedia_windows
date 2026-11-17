# Quitar publicidad · compra única preparada, no operativa

Producto propuesto: `mando_remove_ads`, INAPP no consumible para `net.mando.publicapp`. Compra única, sin suscripción. No fija un importe X en código: el precio y moneda localizados se obtendrán de ProductDetails de Google Play. Configurar el importe en Play Console antes de publicar. La edición personal no muestra ni requiere la compra.

La titularidad pertenece a la cuenta compradora de Google Play y al paquete público. No requiere leer su correo ni añadir Google Sign-In. No transfiere la compra a la web ni al servidor Windows. Con varias cuentas en el dispositivo, restaurar con la cuenta que realizó la compra. Se necesita publicar/configurar el producto en Play Console y probar mediante un canal de prueba de Play; la APK directa actual no demuestra que facturación funcione.

## Estado actual

`RemoveAdsPurchase` es una frontera desactivada (UNAVAILABLE). No cobra, no consulta Play y no concede derechos públicos desde preferencias locales. Configuración enabled=false y compilación bloqueada si se intenta activarla. No se muestran botones que simulen compras o restauraciones. El SDK, la interfaz y el backend aún no están integrados.

## Flujo de integración pendiente

1. Añadir Play Billing a la variante pública exclusivamente con un build que resuelva AAR y manifest merging. Consultar ProductDetails del producto configurado; ocultar compra si producto/precio no están disponibles.
2. Ajustes → Quitar publicidad: mostrar precio formateado de Play y texto «Pago único». Añadir Restaurar compras y estado accesible. Compra solo tras pulsación explícita, nunca al abrir la app.
3. Al iniciar, reanudar y reconectar BillingClient, consultar queryPurchasesAsync(INAPP); usar la misma ruta para Restaurar. La ausencia de conectividad es ERROR/CHECKING, no equivale a revocación confirmada.
4. PENDING no concede el derecho. PURCHASED envía token al backend por HTTPS; servidor consulta Google Play Developer API, comprueba paquete, producto y estado, y deduplica el token. No confiar en isAcknowledged, un booleano del cliente ni un ID de pedido como prueba de propiedad. No registrar tokens. Credenciales de Google solo en el backend.
5. Tras validación, conceder el derecho de forma idempotente y reconocer la compra (acknowledge); nunca consumir el producto. Reintentar reconocimiento sin repetir compra/cobro. Respetar el plazo de reconocimiento de Play.
6. Retirar banners existentes inmediatamente tras concesión. Consentimiento y publicidad no deben inicializarse mientras se restaura titularidad. Un derecho validado puede conservarse para uso offline; definir caché firmada y su vigencia antes de activar. Una respuesta temporal fallida no elimina una compra confirmada.
7. Backend: RTDN y revalidación para reembolsos/revocaciones. No usar únicamente una marca permanente en SharedPreferences; tampoco exigir siempre Internet para mantener la experiencia sin anuncios. Definir la política de caché y cambio de cuenta en el adaptador antes de publicar.

## Validación obligatoria antes de activar

Compra de prueba, cancelación, pendiente/completada, restauración tras reinstalar y en segundo móvil, producto ya adquirido, desconexión de Play, token repetido y ajeno/incorrecto, reembolso, recuperación de acknowledge fallido, cuenta distinta y ausencia total de Billing/Ads SDK en personal. Ningún anuncio debe interferir con las entradas al PC.

Referencias oficiales:
- https://developer.android.com/google/play/billing/integrate
- https://developer.android.com/google/play/billing/security
