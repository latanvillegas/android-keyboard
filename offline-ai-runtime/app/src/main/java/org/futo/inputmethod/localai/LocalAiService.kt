package org.futo.inputmethod.localai

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional local model APK. IPC is signature-protected in the manifest.
 * No network APIs, logging of user text, or cloud fallback.
 */
class LocalAiService : Service() {
    companion object {
        const val MSG_GENERATE = 100
        const val MSG_RESPONSE = 101
        const val MAX_PROMPT_CHARS = 2500
        const val MAX_RESULT_CHARS = 12000
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "futo-local-inference").apply { isDaemon = true }
    }
    private var engine: LlmInference? = null
    private val liteRtEngine = LiteRtEngine()
    private val requestBusy = AtomicBoolean(false)
    private var activeStamp = -1L

    private val incoming by lazy {
        Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.what != MSG_GENERATE) return@Handler true
            val reply = message.replyTo ?: return@Handler true
            val prompt = message.data.getString("prompt").orEmpty()
            val token = message.data.getLong("token", -1L)
            if (!requestBusy.compareAndSet(false, true)) {
                val busyResponse = Bundle().apply {
                    putLong("token", token)
                    putString("error", "Another local inference request is running")
                }
                try {
                    reply.send(Message.obtain(null, MSG_RESPONSE).apply { data = busyResponse })
                } catch (_: Exception) { }
                return@Handler true
            }
            executor.execute {
                val response = Bundle().apply {
                    putLong("token", token)
                    try {
                        if (prompt.isBlank() || prompt.length > MAX_PROMPT_CHARS)
                            throw IllegalArgumentException("Prompt must be 1–2500 chars")
                        val folder = File(filesDir, "llm")
                        val liteRt = File(folder, "model.litertlm")
                        val result = if (liteRt.isFile) {
                            // User-imported LiteRT Qwen takes precedence. Removing it
                            // falls back to the existing MediaPipe .task backend.
                            liteRtEngine.generate(liteRt, prompt)
                        } else {
                            val model = File(folder, "model.task")
                            if (!model.isFile || model.length() < 1024)
                                throw IllegalStateException(
                                    "Import a compatible .litertlm or .task model first"
                                )
                            if (engine == null || activeStamp != model.lastModified()) {
                                engine?.close()
                                engine = null
                                val options = LlmInference.LlmInferenceOptions.builder()
                                    .setModelPath(model.absolutePath)
                                    .setMaxTokens(768)
                                    .setMaxTopK(40)
                                    .build()
                                engine = LlmInference.createFromOptions(
                                    this@LocalAiService, options
                                )
                                activeStamp = model.lastModified()
                            }
                            requireNotNull(engine).generateResponse(prompt)
                        }
                        putString("text", result.take(MAX_RESULT_CHARS))
                    } catch (error: Exception) {
                        putString("error", (error.message ?: error.javaClass.simpleName)
                            .take(400))
                    } catch (error: LinkageError) {
                        putString("error", "LiteRT native library unavailable: " +
                            error.javaClass.simpleName)
                    } finally {
                        requestBusy.set(false)
                    }
                }
                try {
                    reply.send(Message.obtain(null, MSG_RESPONSE).apply {
                        data = response
                    })
                } catch (_: Exception) {
                    // The editor may have been closed or the request cancelled.
                }
            }
            true
        })
    }

    override fun onBind(intent: Intent?): IBinder = incoming.binder

    override fun onDestroy() {
        // Native generateResponse() is synchronous. Never close the engine
        // from the main thread while a worker is using its memory.
        executor.execute {
            try { engine?.close() } catch (_: Exception) {}
            engine = null
            liteRtEngine.close()
        }
        executor.shutdown()
        super.onDestroy()
    }
}
