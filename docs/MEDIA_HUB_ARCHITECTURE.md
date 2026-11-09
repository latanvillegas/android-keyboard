# Media Hub — implementación aislada

Rama: feature/media-hub-stickers-gif-offline-ai
Base: feature/fluent-emoji-3d-animated (PR #9, no master).

## Funcionalidad implementada

- Acción Media Hub en el panel de acciones, accesible desde el selector Emoji.
- Importación mediante Android Storage Access Framework: PNG, JPEG, WebP, GIF y ZIP con imágenes.
- Copia exacta de bytes originales a noBackupFilesDir/media-hub, fuera del APK y persistente entre actualizaciones; identificador de contenido SHA-256.
- Detección del formato por cabeceras y validación de dimensiones: 4096 px por lado y 16,7 megapíxeles, 24 MiB por archivo, 500 archivos y cuota total de 512 MiB.
- ZIP: solo imágenes en raíz o media/, máximo 200 entradas, 128 MiB comprimidos y 256 MiB importados. Sin extracción de rutas del ZIP al filesystem.
- Panel con filtros GIF, stickers y favoritos, miniaturas muestreadas, caché de memoria limitada a 8 MiB y borrado local.
- ContentProvider privado de solo lectura y MIME originales: GIF, WebP, PNG y JPEG; envío protegido ante cambios de sesión de editor.
- Editor estático sin Internet: imagen de origen + texto; exporta PNG de 512px sin sobrescribir el original.

## Limitaciones

Esta primera fase conserva el catálogo Fluent de PR #9 dentro del APK. Para reducir realmente el APK sin degradar calidad falta desarrollar un paquete de alta calidad instalable y hacer que el lookup existente pueda leer de almacenamiento externo o catálogo integrado.

El motor KeyboardLM ya predice offline. La integración de un LLM conversacional GGUF, traducción generativa y edición animada GIF/TGS/WebM queda para fases independientes; no se presenta un falso backend IA. ZIP importa imágenes, pero no instala packs nativos de WhatsApp o Telegram.

## Validación

Ejecutar testStableReleaseUnitTest y assembleStableRelease en GitHub Actions, verificar firma y contenido del APK. Probar manualmente SAF, provider privado, envío a editores reales y conservación del original.

No subir contenido privado del usuario al repositorio ni empaquetarlo en el APK.
