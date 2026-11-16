# Auditoría técnica Keyvya 1.7 — ruta para FUTO AI

Origen: análisis estático local del usuario con Termux. Archivo: Keyvya — Teclado com IA 1.7.apk+.
SHA-256 de entrada: 0139bd8f2a2810d59ae4b307598d3001cad345772b901d7939c91d3ab5a924db.
Tamaño: 24.217.853 bytes. DEX: classes.dex y classes2.dex.

## Hallazgos sustentados en informe.json

- Referencias de clases a com/google/ai/edge/litertlm, EngineConfig, Session, Backend, LiteRtLmJni.
- Referencias a GeminiService y mensajes que indican fallback Gemini mientras se descarga IA local.
- Referencias a Android DownloadManager y gestión de descarga de modelos.
- URL de modelo fijada en DEX:
  https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm
- Verificado en Hugging Face: Qwen2.5-1.5B-Instruct, variante Q8 LiteRT-LM, 1.597.931.520 bytes; licencia Apache-2.0.
  SHA-256 publicado en la revisión anterior: faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9.
- El escaneo del paquete base no encontró .so ni pesos LLM. Aparece un archivo de identificación de idioma TFLite, pero NO es el modelo generativo.
- Se detectaron menciones de InputMethodService, InputConnection y commitText.
- Se detectaron menciones de LupaAccessibilityService; no está confirmado su uso en la ruta de inferencia.

## Límites de la prueba

Esta auditoría extrae cadenas del DEX; no ejecuta el APK ni reconstruye su grafo de llamadas.
aapt no estaba instalado en Termux; no se descodificaron permisos ni el manifiesto.
No afirmar que Gemini se ejecuta siempre, que la IA local es funcional en todo dispositivo,
ni que se descargan bibliotecas nativas de una fuente específica sin más pruebas.

## Integración correcta con FUTO

El proyecto contiene:
- Teclado principal Kotlin 2.1, acción OfflineAiAction y cliente LocalAiClient.
- IPC Messenger 100/101 protegido con permiso signature.
- Complemento offline-ai-runtime separado: MediaPipe tasks-genai 0.10.27, modelos .task.

LiteRT-LM .litertlm NO es equivalente al modelo MediaPipe .task ni a GGUF de KeyboardLM.
Las versiones LiteRT-LM 0.17-0.18 usan metadata Kotlin 2.4. El backend nuevo debe
compilarse con toolchain propio (Kotlin >=2.3), sin subir el toolchain del teclado
principal de manera involuntaria.

Plan de integración:
1. Agregar backend LiteRT-LM al complemento de IA con selector de motor y soporte .litertlm.
2. Conservar funcionalidad MediaPipe .task existente, sin degradaciones.
3. Descargar en navegador, importar con Storage Access Framework y calcular SHA-256.
4. Iniciar Engine en worker exclusivo, limitar concurrencia/memoria y liberar recursos.
5. Reutilizar LocalAiClient; insertar solo tras verificar que no cambió el editor.
6. Mantener el teclado y el modelo como artefactos separados: ZIP principal = SOLO APK FUTO.
7. Desactivar fallback de red implícito: la inferencia por defecto permanece offline.
8. Añadir pruebas Gradle y Android sobre dispositivo antes de afirmar compatibilidad real.

Documentación externa:
- https://developers.google.com/edge/litert-lm/android
- https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
- https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct

Estado: AUDITADO. Backend LiteRT-LM aún NO implementado ni validado en FUTO.
