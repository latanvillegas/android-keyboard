package org.futo.inputmethod.localai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File

/**
 * Serialized LiteRT-LM model owner. All functions run on the serial
 * LocalAiService inference executor, never on the Android main thread.
 *
 * LiteRT-LM 0.18 passes message content to Jinja as a sequence of parts.
 * Some Qwen2.5 LiteRT model revisions embed an earlier template that expects
 * a string and crashes nativeSendMessage with "string and sequence".
 * Override only the conversation chat template, not model weights or tokenizer,
 * with the upstream v0.18.0 Qwen2.5 template supporting multipart content.
 */
internal class LiteRtEngine(private val context: Context) {
    private var engine: Engine? = null
    private var stamp = ""

    // Load once from the runtime's own assets; the Qwen model remains external.
    private val qwen2p5Template: String by lazy(LazyThreadSafetyMode.NONE) {
        context.assets.open("qwen2_5_chat_template.jinja").bufferedReader(
            Charsets.UTF_8
        ).use { it.readText() }.also { source ->
            check(source.contains("macro format_content(content)") &&
                source.contains("item['type']") &&
                source.contains("add_generation_prompt")) {
                "The Qwen2.5 chat template asset is incomplete"
            }
        }
    }

    fun generate(model: File, prompt: String): String {
        require(model.isFile && model.length() >= 1048576L) {
            "Import a compatible .litertlm model first"
        }
        val currentStamp = model.absolutePath + ":" +
            model.length() + ":" + model.lastModified()

        if (engine == null || currentStamp != stamp) {
            close()
            val next = Engine(
                EngineConfig(
                    modelPath = model.absolutePath,
                    backend = Backend.CPU()
                )
            )
            // initialize is synchronous and can take time for 1.6 GB models.
            // An Engine whose initialization fails must not be closed.
            next.initialize()
            engine = next
            stamp = currentStamp
        }

        val current = checkNotNull(engine)
        val conversation = current.createConversation(
            ConversationConfig(
                chatTemplate = qwen2p5Template,
                maxOutputToken = 768
            )
        )
        val result = conversation.use { chat ->
            chat.sendMessage(prompt, maxOutputToken = 768).toString().trim()
        }
        if (result.isBlank()) throw IllegalStateException("No response from local model")
        return result
    }

    fun close() {
        val old = engine
        engine = null
        stamp = ""
        if (old != null) {
            try {
                old.close()
            } catch (_: Exception) {
                // A failed native shutdown must not take down the IME.
            }
        }
    }
}
