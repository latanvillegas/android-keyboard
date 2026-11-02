# FUTO Keyboard Fork — vfork-stable-56 (pruebas)

**Compilación Stable Release verificada, firmada con clave de desarrollo (`test-signed`).** Esta edición está dirigida a pruebas y validación del fork. **No es una actualización oficial de producción**, no está firmada con una clave de distribución y puede no instalarse sobre una APK firmada con otro certificado. Antes de sustituir la instalación, respalda la configuración y los datos importantes. No se recomienda distribuirla como versión estable de producción.

- **Código fuente compilado:** [`ea8acd0125205245b88d219ef6331a700f2857f4`](https://github.com/latanvillegas/android-keyboard/commit/ea8acd0125205245b88d219ef6331a700f2857f4).
- **Compilación original verificada:** [GitHub Actions #56](https://github.com/latanvillegas/android-keyboard/actions/runs/41537785104), ejecutada el 2 de noviembre de 2026; completada satisfactoriamente.
- **Variante:** `Stable Release`, sin compilación Debug.
- **Verificación:** tests JVM de seguridad de temas, auditoría de traducción española, firma APK, integridad del paquete, recursos Fluent Emoji 3D y SHA-256.
- **Instalación de temas:** los paquetes Aurora 3D y Obsidian 3D son temas ZIP que se importan por separado; **no se convierten en temas preinstalados por instalar esta APK**.

## Novedades del motor de temas — V2, V2.1 y V2.2

- **Barra de sugerencias adaptable:** opción de un solo fondo continuo con PNG de bordes 9-slice, que ocupa el ancho real disponible en orientación vertical u horizontal. Las palabras permanecen separadas funcionalmente para selección individual.
- **Fondos por sugerencia (V2.1):** imágenes normales y presionadas para cada palabra, con marcos 3D, espaciado visual, radio, colores de respaldo y separación opcional. La predicción y el comportamiento de las sugerencias no cambian.
- **Botones circulares personalizados (V2.2):** imágenes 9-slice normales y presionadas para los botones de expansión y micrófono; soporte de un estilo genérico para acciones y de recursos específicos por botón.
- **Iconos de acciones personalizables (V2):** imágenes PNG/WebP propias, escala por acción, tinte opcional y color global; mecanismo de sustitución con fallback al recurso original si falta la imagen.
- **Botón de expansión:** el chevron pasa a resolverse por el sistema de iconos V2; conserva la rotación al expandir/contraer y los textos de accesibilidad.
- **Configuración de apariencia de la barra:** color de fondo, tipografía y escala de las sugerencias, color de texto, separadores, fondo de acciones expandidas y colores de los botones.
- **Temas TOML y JSON:** nuevos campos opcionales y compatibilidad con las opciones preexistentes. Los temas oficiales V1 mantienen su funcionamiento y estilo de origen.
- **Editor integrado:** selección de iconos individuales, importación acotada de imágenes, vista previa y ajustes de color/escala para las acciones.
- **Tamaño adaptable:** mejoras al escalado de etiquetas, iconos y sugerencias, respetando los límites de la barra y las áreas táctiles.
- **Compatibilidad con Aurora/Obsidian:** los ZIP pueden aprovechar el nuevo soporte 9-slice y de botones; sus imágenes permanecen recursos independientes de esta APK.

## Teclado y elementos visuales

- **Microsoft Fluent Emoji 3D reales:** recursos prerenderizados y transparentes sin placas verdes artificiales, disponibles sin conexión para paneles de emoji y sugerencias compatibles.
- **Tonos de piel y variaciones:** soporte para el arte original en las variantes de emoji compatibles.
- **Sugerencias con emoji:** posibilidad de mostrar relieve 3D y controles de tamaño de celdas de emoji; conservación de su representación en color real.
- **Redimensionado del teclado:** mejoras para alturas, distribución, vistas previas y orientación horizontal; pruebas de humo diseñadas para verificar el teclado visible.
- **Interfaz limpia:** corrección de divisores horizontales indeseados en la barra de sugerencias.
- **Efectos visuales reducidos:** preferencia para disminuir movimiento y actividad gráfica de algunos temas animados, útil para accesibilidad y consumo de GPU.

## Idioma español y configuración

- **Interfaz Español / Inglés / Sistema:** selección del idioma de la interfaz independientemente de los idiomas utilizados para escribir.
- **Android 13 y posteriores:** integración con el selector de idioma por aplicación del sistema operativo.
- **Android 12 y anteriores:** anulación de idioma aplicada a la interfaz de Ajustes, conforme al alcance implementado en el fork.
- **Traducciones ampliadas:** textos de ajustes, portapapeles, gestos, temas, acciones y entrada por voz.
- **Auditoría de localización:** control automatizado de traducciones y parámetros de formato para evitar textos incompletos.
- **Ajustes adaptados al tema seleccionado:** la aplicación ya utiliza `THEME_KEY` y `UixThemeAuto` para aplicar la paleta del tema. No se añadió un mecanismo redundante de tematización.

## Seguridad, importación y mantenimiento

- **Importación ZIP endurecida:** límites de tamaño y descompresión, validación de entradas y rutas, prevención de traversal/duplicados y reemplazo atómico de temas.
- **Decodificación limitada:** límites de imágenes y metadatos para controlar el consumo de recursos y evitar errores al procesar ZIPs.
- **Compatibilidad hacia atrás:** pruebas de los formatos anteriores y fallback a los recursos originales cuando faltan opciones V2.
- **Compilaciones reproducibles:** CI Android 35 con compilación Stable Release, firma y validación, comprobación de licencia y empaquetado de Fluent Emoji 3D.
- **Informes incluidos en esta Release:** manifiesto de compilación, resultado de firma, auditoría española, lista de hashes SHA-256 y licencia de recursos Fluent.

## Instalación y advertencias

1. Descarga `futo-keyboard-vfork-stable-56-test-signed.apk` de los archivos adjuntos en esta página.
2. Comprueba el hash SHA-256 con `SHA256SUMS` antes de instalar.
3. **Verifica la firma:** esta APK se generó con el certificado de desarrollo del repositorio. Si la versión instalada tiene una firma distinta, Android rechazará la actualización directa; **no desinstales sin respaldar los datos**.
4. Después de instalar, importa por separado tu tema Aurora 3D u Obsidian 3D compatible con V2.2 para disponer de las barras y controles personalizados.

**Estado:** compilación y verificaciones automáticas superadas. La representación final de cada tema y las actualizaciones sobre instalaciones con otras firmas deben validarse en el dispositivo.

**Recursos adjuntos:** APK, `SHA256SUMS`, `BUILD-INFO.txt`, `apk-signature.txt`, `apk-manifest.txt`, `spanish-audit.json`, `spanish-audit.md`, `emoji-3d-verification.txt` y `FLUENT_EMOJI_LICENSE.txt`.
