# Runtime opcional de IA sin conexión

Este subproyecto es una aplicación Android autónoma. No forma parte de
\`settings.gradle\` del teclado principal y por tanto NO suma sus bibliotecas
nativas ni modelos al APK Core.

## Compilación

Desde la raíz del repositorio (JDK 17, SDK 35):

\`\`\`bash
./gradlew -p offline-ai-runtime --no-daemon --build-cache :app:assembleRelease
\`\`\`

Artefacto: \`offline-ai-runtime/app/build/outputs/apk/release/app-release.apk\`.
El workflow final construye este APK una sola vez junto al APK principal y
verifica que ambos estén firmados con **el mismo certificado**.

El motor se basa en \`com.google.mediapipe:tasks-genai:0.10.27\`
(LlmInference). Sus binarios están únicamente en el APK complementario.
La API está en modo mantenimiento; no empleamos ML Kit GenAI nuevo en el
APK Core porque requiere Kotlin 2.3+ y Android 26+, incompatible con la
base actual de Kotlin 2.1 / minSdk 24.

## Instalación y modelo

1. Instalar primero el teclado y después el APK complementario firmado.
2. Abrir \`Media Hub → Asistente IA offline → Configurar modelo IA offline\`.
3. Importar explícitamente un modelo **MediaPipe LLM .task** compatible.
   No incluye modelos preinstalados ni conexión a Internet.
4. Introducir el texto deseado, seleccionar operación y revisar la salida.
5. Elegir Copiar o Insertar; Insertar requiere que el editor siga siendo el
   mismo que estaba activo al iniciar la generación.

**No aceptar GGUF genérico:** los modelos KeyboardLM \`.gguf\` de FUTO
tienen un formato de entrenamiento/metadata propio y no son equivalentes
a los bundles \`.task\` de MediaPipe. La compatibilidad debe verificarse
para cada modelo y dispositivo.

## Protocolo protegido

- Paquete: \`org.futo.inputmethod.localai\`.
- Servicio explícito: \`LocalAiService\`, Messenger IPC, códigos 100/101.
- Permiso: \`org.futo.inputmethod.latin.permission.LOCAL_AI\`,
  con protección **signature**, por lo que ambos APK deben usar el
  mismo certificado.
- Request: \`Bundle(prompt: String, token: Long)\`. Respuesta:
  \`Bundle(token: Long, text: String | error: String)\`.
- Máximo 2.500 caracteres de entrada y 12.000 de salida; inferencia en
  un único hilo de fondo, con espera del teclado limitada a 120 segundos.
- La IA procesa exclusivamente texto introducido voluntariamente en su
  panel. No inspecciona contraseñas, conversaciones ni el contenido de
  otros campos.
- El runtime rechaza solicitudes sin modelo. No inventa respuestas.
- Ninguno de los dos componentes necesita acceso de red para inferencia;
  el APK complementario se verifica sin permiso \`INTERNET\`.

## Seguridad y límites

MediaPipe está optimizado para equipos compatibles y el paquete de modelo
puede ocupar gigabytes. Un modelo \`.task\` importado puede ser rechazado por
la API nativa o no funcionar en determinados dispositivos/GPUs; el error
se muestra y no se sustituye por un servidor.

La importación mediante Storage Access Framework realiza una copia privada
con cuota máxima de 3 GiB y SHA-256. El runtime solo carga un modelo de forma
secuencial. El teclado conserva su KeyboardLM nativo para sugerencias
independientemente de la presencia del runtime.

Pruebas pendientes en dispositivo: importación de un modelo .task específico,
inferencia real, cancelación durante el cierre del panel, cambio de foco,
memoria y consumo en teléfonos con hardware diverso.
