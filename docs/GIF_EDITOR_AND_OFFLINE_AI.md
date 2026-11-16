# Editor de GIF y stickers (sin servicios externos)

## Funciones

Desde Media Hub → GIF → Editar GIF se puede abrir un archivo importado
directamente. También existe el botón Editar GIF para elegir uno de SAF.

La edición se aplica a una **copia nueva** del original:
- Seleccionar inicio y fin de un intervalo (0–100 % de la duración).
- Velocidad de reproducción entre 50 y 200 %.
- Recorte cuadrado centrado o reescalado proporcional.
- Texto superpuesto en todos los fotogramas.
- Exportación como GIF89a real, con repetición, transparencia de 1 bit,
  intervalos individuales y compresión GIF LZW de 12 bits.

El decodificador es \`android.graphics.Movie\` (API 24+).
El codificador \`Gif89aWriter\` está implementado en Kotlin, sin FFmpeg,
sin acceso a red y sin bibliotecas nativas adicionales.

Para evitar falta de memoria y latencia, la edición está limitada a
intervalos de 15 segundos, máximo 60 cuadros, salida con lado máximo 360px,
entrada máxima 24 MiB y cuota de biblioteca de 512 MiB. Una operación
que sobrepasa los límites falla explícitamente, sin sobrescribir originales.

**Limitación del GIF:** este formato usa paletas de 256 colores y
transparencia binaria, no RGBA de 24 bits. El original WebP/GIF importado
se mantiene sin alterar. La exportación editada no puede preservar
RGBA completo porque el formato GIF no lo soporta.

## Validación

\`Gif89aWriterTest\` usa el decodificador independiente Java ImageIO
solo en pruebas JVM para comprobar múltiples cuadros, LZW cruzando
límites de anchura de código y generación determinista.

La misma ejecución final de GitHub Actions compila y ejecuta la suite
\`testStableReleaseUnitTest\`, construye el Core APK, verifica la ausencia
de los cuatro catálogos Fluent 3D en él, publica el ZIP Fluent completo,
y construye el runtime de IA aparte con firma idéntica y sin Internet.
