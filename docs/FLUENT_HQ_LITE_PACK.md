# Variante Fluent 3D Lite con paquete de alta fidelidad instalable

Rama hija: feature/fluent3d-optional-hq-pack-lite-apk
Base: feature/media-hub-stickers-gif-offline-ai (PR #10).
No modifica master, PR #9 ni la compilación completa original.

## Política de calidad

El APK Lite conserva el teclado, las 2.980 vistas previas 3D de 128px,
las 30 animaciones GIF de compatibilidad y todos los mecanismos de envío.
El catálogo externo opcional contiene **exactamente los masters originales**
de Fluent 3D en WebP 256px RGBA lossless y las 30 animaciones WebP lossless,
sin recomprimir ni modificar los frames.

Con paquete HQ instalado, FluentEmoji3D.loadShareBitmap y
FluentEmoji3DMotion.openWebp priorizan el almacenamiento privado verificado.
Sin paquete se usa el fallback existente: GIF animados o PNG desde los
128px del selector. Nunca se promete alta resolución sin el paquete instalado.

## Seguridad y despliegue

Los archivos originales se generan desde las mismas fuentes Microsoft MIT
fijadas por commits inmutables. El script build_fluent3d_hq_pack.py produce
un ZIP sin código ejecutable, con manifest de rutas y SHA-256 por miembro.
El instalador comprueba el inventario, hash, tipo real WebP, índice y licencia,
y publica el catálogo de forma atómica dentro de noBackupFilesDir/fluent-hq.
Se bloquean rutas arbitrarias, entradas adicionales, archivos de gran tamaño
y payloads ZIP incompletos.

La misma pantalla Importar del Media Hub detecta el paquete Fluent HQ.
No se requieren permisos de lectura globales ni una conexión a Internet.
Se puede eliminar el paquete HQ sin eliminar los stickers del usuario.

## Compilaciones

El workflow optimizado seguirá generando el APK completo en el PR #10.
La variante Lite de esta rama construye un APK separado y adjunta el
paquete HQ que corresponde a la misma versión del código.
Ambos artefactos se validan; no sustituir un APK completo ya instalado por
otro que use firma incompatible.
