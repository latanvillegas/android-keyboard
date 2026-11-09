# Core APK sin archivos Fluent 3D; colección externa íntegra

Rama de integración: feature/fluent3d-optional-hq-pack-lite-apk
PR apilado sobre #10; master y PR #9 no cambian.

## Los cuatro catálogos se eliminan del APK

| Catálogo | Destino |
| --- | --- |
| 2.980 WebP de 128 px para el panel | ZIP externo |
| 2.980 WebP RGBA sin pérdida de 256 px | ZIP externo |
| 30 WebP animados sin pérdida de 256 px | ZIP externo |
| 30 GIF animados compatibles | ZIP externo |

El APK base contiene únicamente código Kotlin, recursos normales del
teclado, interfaz Media Hub, motor Unicode y las capacidades preexistentes.
No requiere red ni paquete multimedia para permitir escribir.

El generador scripts/build_fluent3d_hq_pack.py toma los catálogos Fluent
producidos por fuentes Microsoft MIT fijadas en los scripts existentes y
crea fluent3d-full-offline-collection.zip (v2). Copia cada imagen byte a byte,
sin cambiar compresión, transparencia, resolución ni duración de animaciones.
Cada archivo tiene SHA-256, el manifest tiene recuentos verificables y el
archivo ZIP incluye las tres licencias/metadatos de los catálogos.

## Instalación desde el teclado

Media Hub → Importar → elegir fluent3d-full-offline-collection.zip.

El importador detecta el manifest fluent-hq.json, valida tipo, tamaños,
recuentos, todos los checksums, firmas de imagen y rutas; publica el paquete
mediante intercambio atómico en almacenamiento privado.
El teclado detecta la instalación sin reiniciar: invalida la caché de índices
y miniaturas al instalar o desinstalar el paquete. El envío de emojis prueba
Animated WebP, GIF, PNG lossless y, si falta soporte, Unicode.

Para quitar la colección: Media Hub → Quitar paquete completo.
Los GIF personales, stickers y packs importados permanecen intactos.

## Comportamiento sin paquete

El selector de emojis funciona con Unicode, no intenta descargar contenido,
ni añade círculos o relieve falso. No hay envío 3D ni GIF de Microsoft hasta
instalar el ZIP; los GIF personalizados del Media Hub sí funcionan.
No se promete que WhatsApp o Telegram reproduzcan todos los formatos: la
aplicación receptora controla el resultado de commitContent.

## Compilación y consumo GitHub Actions

Los trabajos de este PR tienen guardas para evitar builds automáticos en
cada commit. Los archivos de los pipelines de PR #9 y #10 permanecen
sin cambios. Una vez completadas y revisadas las funciones, se habilitará
una sola compilación final del APK Core y del ZIP completo con validación
del contenido y firma. Los assets originales se mantienen en cachés
independientes y se eliminan del árbol java/assets ANTES de Gradle.

La compilación Stable Release (cuando el código se incorpore a master)
también genera el paquete externo, elimina los tres directorios de assets
y verifica que ninguna de las cuatro colecciones aparece dentro del APK.

No confundir el tamaño del ZIP externo con el del APK. Aunque se eliminan
~144 MiB de recursos multimedia preempaquetados, el tamaño del APK también
depende de módulos nativos, bibliotecas y recursos del teclado.

## Funciones por fase

Implementado en PR #10: biblioteca, importación PNG/JPEG/WebP/GIF/ZIP,
favoritos, envío MIME y creación de stickers estáticos, ZIP de copia de
seguridad y acceso al gestor KeyboardLM offline ya existente.
Implementado aquí: distribución externa, instalación íntegra y
conmutación segura del lookup para los cuatro catálogos Fluent.

Aún no implementado: motor generativo conversacional GGUF general,
edición temporal avanzada de GIF y packs nativos TGS/WebM.
No se presentan estas capacidades como terminadas.
