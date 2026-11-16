package org.futo.inputmethod.localai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File

/**
 * Sequential LiteRT-LM model owner. All entrypoints run on the
 * LocalAiService's single inference executor, never the Android main thread.
 */
internal class LiteRtEngine {
    private var engine: Engine? = null
    private var stamp = ""

    fun generate(model: File, prompt: String): String {
        require(model.isFile && model.length() >= 1048576L) {
            "Import a compatible .litertlm model first"
        }
        val currentStamp = model.absolutePath + ":" +
            model.length() + ":" + model.lastModified()
        if (engine == null || currentStamp != stamp) {
            close()
            val next = Engine(EngineConfig(
                modelPath = model.absolutePath,
                backend = Backend.CPU()
            ))
            // LiteRT-LM explicitly forbids closing an engine that failed to initialize.
            next.initialize()
            engine = next
            stamp = currentStamp
        }
        val current = checkNotNull(engine)
        // Conversations never retain the text from another editor or request.
        val result = current.createConversation().use { chat ->
            chat.sendMessage(prompt, maxOutputToken = 768).toString().trim()
        }
        if (result.isBlank()) throw IllegalStateException("No response from local model")
        return result
    }

    fun close() {
        val old = engine
        engine = null
        stamp = ""
        if (old != null) try { old.close() } catch (_: Exception) { }
    }
}
