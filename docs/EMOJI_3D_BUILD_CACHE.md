# Cache de compilación y empaquetado Fluent 3D

## Estado comprobado

La ejecución [#42623590831](https://github.com/latanvillegas/android-keyboard/actions/runs/42623590831)
terminó correctamente con firma, pruebas y verificación de recursos *dentro del APK*:
2.980 ilustraciones de interfaz, 2.980 ilustraciones RGBA WebP sin pérdida de
256 px para compartir, y 30 animaciones genuinas en WebP y GIF.

El pipeline ya restauró correctamente **dos cachés versionadas de imágenes**.
Estas cachés no son equivalentes al APK compilado: el APK se vuelve a construir
después de cada modificación que afecte al binario.

## Capas de caché

| Capa | Contenido | Cuándo se invalida |
| --- | --- | --- |
| `static-assets` | `java/assets/emoji3d` y `emoji3d-share` | Cambia `scripts/build_emoji3d_assets.py`, la clave o el commit Fluent fijado |
| `motion-assets` | `java/assets/emoji3d-motion`, 30 WebP y GIF | Cambia `scripts/build_emoji3d_motion.py`, la clave o el commit Microsoft fijado |
| Gradle User Home | Distribución Gradle, dependencias, transformaciones, tareas cacheables | Cambian las entradas de cada tarea o las dependencias/compilador |

Los catálogos estáticos y animados se generan en jobs independientes. Tras
generarlos y validarlos, `actions/cache@v4` los guarda por claves que incluyen
el hash del script y la revisión de origen. El job `animated-preview` los
restaura con `fail-on-cache-miss: true`: jamás publica un APK incompleto
porque falten assets.

La caché Gradle usa `gradle/actions/setup-gradle@v4`. En GitHub Actions
los pull requests eran **de solo lectura** de manera predeterminada;
ahora los jobs Kotlin y APK habilitan `cache-read-only: false` para guardar
también el estado de compilación. `org.gradle.caching=true` y `--build-cache`
activan la reutilización de resultados de tareas que Gradle declara
cacheables. El almacenamiento se mantiene por entradas e invalidación de
inputs, no por copiar resultados arbitrarios de builds antiguos.

## Evitar ejecuciones obsoletas

Solo el job `animated-preview` tiene concurrencia
`cancel-in-progress: true` por PR o ref: al llegar un commit nuevo, se
interrumpe el ensamblado de APK obsoleto. Los trabajos **static-assets** y
**motion-assets** se mantienen independientes para que finalicen y guarden
sus cachés, incluso si cambia el código de la aplicación. Para generar una
compilación a petición se puede usar **Actions → Build FUTO lossless 3D
RGBA animated emoji preview APK → Run workflow** en la rama del PR.

## Límites y seguridad

- **No existe caché perpetua garantizada.** GitHub puede eliminar entradas
  por expiración, capacidad o limpieza. Si hay un fallo de caché, el pipeline
  regenera los recursos desde commits fijados y verifica las licencias.
- El caché creado para `refs/pull/9/merge` puede reutilizarse en ejecuciones
  del mismo PR, pero no está garantizado para PR distintos. La caché de la
  rama predeterminada sí puede beneficiar a nuevas ramas según las reglas de
  acceso de GitHub.
- No se cachean APK firmados como *inputs* del compilador, ni se reutilizan
  directorios temporales de CMake/R8 sin comprobación de entradas. NDK, CMake,
  R8 y los pasos no cacheables pueden seguir tardando varios minutos.
- El artefacto APK tiene una retención configurada de 30 días y **no** es una
  caché de compilación permanente.
- Para depurar una compilación reproducible y limpia, usar
  `./gradlew --no-build-cache` o invalidar la caché pertinente.

No es necesario descargar o archivar manualmente las 3.000 imágenes en el
repositorio. Los scripts y commits fijados sirven de fuente reproducible.
