# FUTO Local AI — runtime opcional con LiteRT-LM y MediaPipe

La IA generativa se compila como **APK complementario independiente**, sin modificar el
pipeline ni el contenido del APK principal del teclado. Las clases de Keyvya no
se copian: el motor LiteRT-LM se integra desde la biblioteca oficial de Google.

## Implementación

- Paquete complementario: org.futo.inputmethod.localai.
- Kotlin 2.4.21, AGP 8.10.1, Android SDK 35, JDK 17.
- LiteRT-LM 0.18.0, modelo .litertlm, Backend.CPU.
- MediaPipe tasks-genai 0.10.27, modelo .task, conservado como alternativa.
- Los dos modelos se importan únicamente mediante SAF y no forman parte de ningún APK.
- No solicita permiso INTERNET ni utiliza servicios Gemini.
- Comunicación IPC Messenger 100/101 y permiso signature;
  FUTO y runtime deben estar firmados con el **mismo certificado**.
- Una única inferencia cada vez; executor en segundo plano, motor reutilizado,
  conversación efímera y cierre del motor desde el mismo executor.

La ruta de selección utiliza automáticamente model.litertlm cuando existe.
De lo contrario, emplea model.task. Esto permite retirar un modelo LiteRT
y recuperar el funcionamiento con el modelo MediaPipe previamente importado.

## Compilación y artefactos

Desde la raíz del repositorio:

    ./gradlew -p offline-ai-runtime --no-daemon --stacktrace :app:assembleRelease

Workflow GitHub Actions:
.github/workflows/futo-optional-ai-runtime.yml

El workflow publica un artefacto ZIP con **solo futo-local-ai-runtime.apk**.
Verifica la firma, integridad ZIP, ausencia del permiso INTERNET y ausencia de
modelos .litertlm/.task/.gguf empaquetados. El flujo principal
.github/workflows/android-release.yml sigue entregando solamente el APK FUTO.

La firma de pruebas usa java/shared.keystore y solo es compatible con el
FUTO test-signed del mismo repositorio. No actualizar un paquete instalado
con otra firma: Android rechazará la actualización. En producción ambos APK
deben compilarse y firmarse con el mismo certificado de producción.

## Instalación y configuración

1. Instalar FUTO Keyboard test-signed.
2. Instalar futo-local-ai-runtime.apk del workflow independiente.
3. Abrir Ajustes > Idiomas y Modelos > Otras opciones >
   Asistente IA offline > Importar manualmente.
4. Se abre la pantalla del complemento. Para LiteRT, seleccionar
   Qwen LiteRT (.litertlm): download / import / remove.
5. Descargar el modelo en el navegador, regresar y usar el selector
   de archivos Android (SAF) para importar el .litertlm.
6. Abrir Acciones > Asistente IA offline, introducir texto voluntariamente,
   elegir operación, revisar el resultado y pulsar Copiar o Insertar.

Modelo identificado en la auditoría Keyvya 1.7:

    Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm

Modelo LiteRT oficial de la comunidad:
https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/blob/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm

SHA-256 publicado: faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9.
El tamaño del modelo es ~1,6 GB y NO está incluido en los artefactos.
La importación acepta hasta 3 GiB, calcula SHA-256 y verifica el archivo
Qwen con nombre exacto contra el checksum publicado.

## Compatibilidad y límites

- LiteRT-LM solo CPU inicialmente; aceleración GPU/NPU no validada.
- Por la carga de memoria del modelo, el dispositivo debe tener RAM y
  espacio suficiente. La generación CPU puede ser lenta.
- Requiere hardware Android arm64 para las bibliotecas nativas empaquetadas.
- El teclado seguirá funcionando aunque no haya runtime o modelo instalado.
- El límite de entrada al servicio es 2500 caracteres.
- La generación real con Qwen en un Lenovo físico debe probarse en Android;
  el resultado exitoso de GitHub Actions prueba compilación y firma,
  no rendimiento, estabilidad térmica ni inferencia en hardware real.
- Los archivos MediaPipe .task, LiteRT .litertlm y FUTO KeyboardLM GGUF
  son formatos distintos y no deben intercambiarse.

## Auditoría de procedencia

Véase docs/KEYVYA_1_7_AI_AUDIT.md. El informe de Termux identificó
referencias a LiteRT-LM, Qwen2.5 1.5B y Gemini en Keyvya,
pero no demostró con ejecución qué ruta utiliza en cada acción.
FUTO no importa el código propietario de Keyvya ni usa Gemini.

API oficial:
https://developers.google.com/edge/litert-lm/android
