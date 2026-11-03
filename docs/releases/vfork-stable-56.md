# FUTO Keyboard Fork vfork-stable-56 — Fluent Emoji 3D + Temas V2.2

> **Pre-release de pruebas.** APK Stable Release compilada correctamente, pero firmada con el certificado de **desarrollo** del repositorio (`test-signed`). **No es una actualización oficial de FUTO ni una versión firmada para distribución en producción.** Si tu APK instalada fue firmada con otra clave, Android no permitirá instalar esta versión directamente encima. Respalda configuración y datos antes de cambiar de instalación.

**Esta versión reúne las mejoras del fork en gráficos de emoji 3D, personalización avanzada del teclado y barra de sugerencias, controles de tamaño, idiomas, accesibilidad y seguridad de los temas ZIP.** La novedad visual del teclado no modifica las palabras que escribe, el contenido insertado ni la lógica de predicción.

## 1. Emojis 3D reales — Microsoft Fluent Emoji 3D

La APK incorpora **2.980 ilustraciones originales Microsoft Fluent Emoji 3D**, con sus colores y transparencia originales. No son emojis Unicode planos con una sombra dibujada encima, ni círculos añadidos por el tema.

### Qué cambia al utilizar emojis

- **Selector de emojis:** el panel muestra ilustraciones Fluent 3D en color real, con volumen, reflejos, materiales y profundidad ya incluidos en el arte original.
- **Búsqueda de emojis:** los resultados compatibles muestran los mismos gráficos Fluent 3D.
- **Variantes y tonos de piel:** se incluyen ilustraciones para variantes compatibles con el paquete; la selección y los valores Unicode insertados no cambian.
- **Sugerencias de emojis:** cuando la predicción propone un emoji con recurso disponible, puede mostrarse la ilustración 3D en la barra de sugerencias, respetando el tamaño de celda configurado.
- **Emoticones de texto / kaomoji:** algunas entradas ASCII pueden visualizarse con una cara 3D equivalente en el selector, pero **al escribir se inserta exactamente el emoticón de texto original**, no la imagen.
- **Emojis no incluidos:** se emplea el renderizado Unicode normal de Android como fallback, sin discos de color ni tarjetas falsas.
- **Conmutador de visualización:** la opción que antes ofrecía «relieve 3D» permite usar el arte 3D real; al desactivarla, se muestra el emoji Unicode del sistema.
- **Tamaño personalizable:** las celdas de emoji/emoticones se pueden ajustar entre **28 y 72 dp** (valor base: 42 dp). La escala también se aplica, proporcionalmente, a las sugerencias de emojis.

### Calidad gráfica, rendimiento y licencias

- **2.980 archivos WebP de 128 píxeles**, con canal alfa; el recurso visual se conserva a color y **no se tiñe según los colores del tema**.
- Recursos **empaquetados dentro de la APK** bajo `assets/emoji3d/`: los gráficos se pueden usar **sin Internet** y sin descarga en tiempo de ejecución.
- El contenido visual es **arte 3D prerenderizado**, no modelos GLB interactivos ni renderizado 3D mediante GPU en tiempo real.
- Se retiraron los antiguos fondos verdes, placas artificiales y sombras falsas que alteraban la presentación de emojis.
- **Fuente:** [Microsoft Fluent Emoji](https://github.com/microsoft/fluentui-emoji), distribuida mediante el [espejo organizado por Unicode](https://github.com/shuding/fluentui-emoji-unicode) fijado en el commit `7a40f1a2d064d76e436813edc0f09b6c8cde5da8`.
- **Licencia del arte:** MIT, copyright Microsoft Corporation. El texto completo se incluye en `assets/emoji3d/LICENSE.txt` y como adjunto `FLUENT_EMOJI_LICENSE.txt`.
- El pipeline comprueba que la APK contiene los recursos 3D y sus documentos de procedencia.

**Importante:** la apariencia Fluent 3D solo afecta a la visualización *dentro del teclado*. Al insertar un emoji, se envía el carácter Unicode normal: la aplicación receptora lo representará según su fuente y plataforma. Esto no convierte los emojis de otras aplicaciones en Fluent 3D.

## 2. Temas avanzados V2, V2.1 y V2.2

### Barra de sugerencias 3D y orientación horizontal

- **V2.2 — Fondo único para toda la barra de sugerencias:** una imagen PNG 9-slice se estira al ancho real del contenedor, en vertical y horizontal, sin incrustarla artificialmente en el fondo del teclado.
- **Tres palabras, una barra visual:** las sugerencias pueden compartir el mismo marco 3D continuo. Aunque no se vean divisiones, cada palabra conserva su área táctil, selección independiente y pulsación prolongada.
- **V2.1 — Fondos individuales alternativos:** también se admite una cápsula 9-slice por palabra, con imagen normal y presionada, colores de respaldo, esquinas, separación y divisores configurables.
- Los ajustes estéticos de la barra no cambian el modelo lingüístico, la autocorrección ni la generación de sugerencias.
- **Color y tipografía:** fondo, color del texto, tamaño relativo de palabras, divisores y zona de acciones expandida configurables por tema.

### Expansión, micrófono e iconos de acciones

- **V2.2 — Fondos circulares personalizables:** imágenes 9-slice independientes para el **botón de expansión (izquierda)** y el **micrófono (derecha)**, con estados normal y presionado.
- Fallback opcional común para otros botones circulares: `action_button_border` y `action_button_pressed_border`.
- **Iconos personales:** sustitución de símbolos mediante PNG/WebP del ZIP; escala individual, color y tinte configurables.
- El botón que expande/contrae la barra ahora resuelve `chevron_right` desde el motor de temas, en lugar de usar siempre un dibujo fijo; mantiene orientación, función y accesibilidad.
- Cuando falta una imagen personalizada, se conserva el icono nativo o el color de respaldo.

### Editor, formatos y temas existentes

- **Editor de temas integrado:** permite escoger imágenes para acciones, ajustar el tamaño de los iconos, colores de los botones y de la barra y previsualizar los cambios.
- **Formatos TOML/ZIP y JSON:** nuevos campos opcionales. Temas V1 y opciones previas siguen funcionando; no es obligatorio usar las funciones V2.
- **Bordes 9-slice:** aprovechan metadatos de estiramiento para conservar esquinas y relieve al cambiar de ancho.
- **Aurora 3D y Obsidian 3D:** sus ZIP se importan por separado y pueden utilizar las nuevas opciones. **La APK no incluye automáticamente las variantes V7, V8 o V9 de esos ZIP; tampoco sustituye imágenes de un tema ya instalado.**
- El editor web externo de FUTO puede no conservar las extensiones exclusivas de este fork; conserva el ZIP/TOML original.

## 3. Teclado adaptable y controles de tamaño

- **Altura de la barra de sugerencias independiente:** 24–200 dp, persistente.
- **Tamaño de texto de sugerencias:** 8–144 sp, separado de las letras del teclado.
- **Escala de los iconos de acciones:** 0,2–5 veces, limitada además por el tamaño efectivo de la barra.
- **Escala de letras, números y pistas secundarias de teclas:** 50–220 %; no obliga a modificar la geometría de cada tecla.
- **Tamaño de celda de emojis:** 28–72 dp, incluido el efecto sobre sugerencias compatibles.
- **Redimensionado del teclado:** mejoras de cálculo según el espacio visible y compatibilidad para escenarios vertical/horizontal, flotante y dividido.
- Se eliminan las líneas horizontales no deseadas de la barra de sugerencias que podían aparecer incluso en vistas previas de tamaño.
- La barra de sugerencias utiliza el espacio disponible para evitar elementos visuales fijos que solo funcionaban en vertical.

## 4. Configuración, idioma español y accesibilidad

- **Idioma de interfaz configurable:** `Sistema`, `Español` o `English`, independientemente de los idiomas de escritura.
- **Android 13 o posterior:** selección integrada con el idioma por aplicación del sistema.
- **Android 12 o anterior:** aplicación de idioma a la interfaz de Ajustes, según el alcance implementado.
- **Traducciones de la interfaz:** más cadenas en ajustes, gestos, portapapeles, temas, predicción, acciones y voz; comprobación de parámetros de formato.
- **Paleta del tema en Ajustes:** FUTO ya observa `THEME_KEY` y aplica `UixThemeAuto`, sin duplicar el sistema existente de tematización.
- **Reducir efectos visuales/movimiento:** opción para evitar algunos shaders animados, reducir actividad gráfica y mejorar accesibilidad o consumo de GPU. No se afirma que elimine todos los efectos de la interfaz.
- **Accesibilidad de acciones:** se mantienen descripciones de expandir/contraer y funcionamiento de botones personalizados.

## 5. Seguridad de temas ZIP y robustez

- Importación ZIP con validación de estructura antes de instalar; evita nombres duplicados, rutas absolutas, traversal `..` y barras invertidas no permitidas.
- Límites documentados: **48 MiB comprimidos**, **160 MiB descomprimidos**, máximo **512 entradas**, **16 MiB por recurso** y **2 MiB por configuración**.
- El tema importado se reemplaza de forma **atómica** después de pasar las validaciones; una importación inválida no debe reemplazar el paquete válido anterior.
- Decodificación de imágenes/metadatos con límites para reducir errores de memoria y entradas malformadas.
- Compatibilidad con temas V1 y recuperación del icono original cuando falta un recurso V2.
- Pruebas JVM para seguridad de temas, compatibilidad y algunos escenarios de errores en ZIP.

## 6. Compilación, pruebas y archivos verificados

| Propiedad | Valor |
|---|---|
| Versión del fork | `vfork-stable-56` |
| Código fuente compilado | [`ea8acd0125205245b88d219ef6331a700f2857f4`](https://github.com/latanvillegas/android-keyboard/commit/ea8acd0125205245b88d219ef6331a700f2857f4) |
| Ejecución de compilación | [GitHub Actions #56](https://github.com/latanvillegas/android-keyboard/actions/runs/41537785104) |
| Fecha de compilación | 2 de noviembre de 2026 |
| Variante | `assembleStableRelease` — Release optimizada, no Debug |
| Firma | **test-signed** (desarrollo, no producción) |
| Plataforma de CI | Android SDK 35; pruebas JVM, auditoría de español y verificación de APK |
| Estado de la ejecución | **Correcta** |
| Archivo APK | `futo-keyboard-vfork-stable-56-test-signed.apk` |

El flujo de compilación verificó la firma mediante `apksigner`, inspeccionó el manifiesto, comprobó la integridad ZIP de la APK, confirmó los recursos y licencia Fluent 3D, ejecutó la auditoría de español y produjo un hash SHA-256.

**Adjuntos de esta publicación:** APK, `SHA256SUMS`, `BUILD-INFO.txt`, `apk-signature.txt`, `apk-manifest.txt`, `spanish-audit.json`, `spanish-audit.md`, `emoji-3d-verification.txt` y `FLUENT_EMOJI_LICENSE.txt`.

## 7. Instalación y compatibilidad de firma

1. Descarga `futo-keyboard-vfork-stable-56-test-signed.apk` de esta publicación y compara su SHA-256 con `SHA256SUMS`.
2. **Antes de actualizar:** comprueba el certificado con el que se firmó la APK actualmente instalada. Un certificado distinto impide la actualización directa; no desinstales ni borres datos sin respaldo.
3. Activa el teclado desde los ajustes de Android solo después de instalarlo de forma satisfactoria.
4. Importa tu tema Aurora 3D u Obsidian 3D **como ZIP separado** para utilizar las nuevas opciones de barra 9-slice e iconos.
5. Verifica en el dispositivo el diseño en orientación horizontal/vertical, las sugerencias táctiles y los permisos de voz.

**Alcance de la validación:** compilación y pruebas automatizadas superadas. La apariencia de cada tema de terceros y la posibilidad de actualizar sobre firmas distintas requieren validación en el dispositivo. El arte 3D no altera los caracteres de texto insertados.

**Referencias de implementación:** [CUSTOMIZATION.md](https://github.com/latanvillegas/android-keyboard/blob/ea8acd0125205245b88d219ef6331a700f2857f4/CUSTOMIZATION.md) · [THIRD_PARTY_EMOJI_3D.md](https://github.com/latanvillegas/android-keyboard/blob/ea8acd0125205245b88d219ef6331a700f2857f4/THIRD_PARTY_EMOJI_3D.md) · [THEME_EXTENSIONS_V2.md](https://github.com/latanvillegas/android-keyboard/blob/ea8acd0125205245b88d219ef6331a700f2857f4/THEME_EXTENSIONS_V2.md).
