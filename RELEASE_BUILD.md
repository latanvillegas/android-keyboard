# Compilación Stable Release del fork

El workflow `.github/workflows/android-release.yml` compila la variante
`assembleStableRelease`, valida la firma con Android `apksigner`, comprueba
el manifiesto y el contenido ZIP, y publica el APK con su SHA-256.

**Descarga:** GitHub -> Actions -> Build FUTO Stable Release APK ->
última ejecución correcta -> Artifacts.

En el archivo ZIP aparecen:
- `futo-keyboard-stable-release.apk`
- `SHA256SUMS`
- `apk-signature.txt`
- `apk-manifest.txt`
- `zip-integrity.txt`
- `BUILD-INFO.txt`

## Tipos de firma

`futo-keyboard-stable-release-test-signed`: paquete Release compilado
con optimización, pero firmado con la clave de DESARROLLO incorporada en el
repositorio, no con la clave de publicación. Permite pruebas independientes,
pero NO sustituye instalaciones oficiales ni se debe publicar como una
actualización de producción.

`futo-keyboard-stable-release-production-signed`: firmado con la clave
privada suministrada mediante GitHub Actions Secrets.

Para habilitar firma de producción, configurar **los cuatro** secretos
en Settings -> Secrets and variables -> Actions:

- `ANDROID_RELEASE_KEYSTORE_BASE64`: archivo keystore existente convertido
  a Base64, sin saltos de línea.
- `ANDROID_RELEASE_STORE_PASSWORD`: contraseña del keystore.
- `ANDROID_RELEASE_KEY_PASSWORD`: contraseña de la clave.
- `ANDROID_RELEASE_KEY_ALIAS`: alias de la clave.

Conservar la MISMA clave utilizada por la versión instalada si se pretende
actualizar esa instalación. No subir el keystore ni las contraseñas al código
fuente ni compartirlos mediante la conversación.

Este fork mantiene **solo el workflow Release**. Se eliminó
`.github/workflows/android-verify.yml` para evitar generar APK Debug.
Las pruebas en un teléfono físico requieren acceso ADB al dispositivo.
