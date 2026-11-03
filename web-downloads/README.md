# Descargas privadas de Mando

Sirve únicamente los artefactos seleccionados en `public/` bajo https://www.sherlockhomeless.net/apps/mando/. El router tiene prioridad sobre Homepage para esa ruta y conserva CrowdSec + Authentik; no expone el repositorio, claves ni material de firma. No requiere DNS nuevo.

Copia `.env.example` a `.env`, prepara los archivos de `public/` y ejecuta desde este directorio:

```bash
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d
```

`public/` contiene exclusivamente APK actual, APK estable anterior, ZIP Windows, instrucciones, hashes y página estática. Binarios generados y `.env` no se guardan en Git. Para actualizar versiones, reemplaza los artefactos seleccionados, sus enlaces y SHA256SUMS; nunca montes `dist/`, `.state/` o el repositorio completo como raíz web.

El enlace de Homepage se añade al YAML activo conservando las demás entradas. Se mantiene una copia de la configuración anterior antes de editarla. Para retirar esta publicación: `docker compose --env-file .env down` y elimina solo la entrada Mando de Homepage.
