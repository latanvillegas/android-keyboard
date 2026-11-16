package org.futo.inputmethod.latin.uix.actions.media

import android.content.Context

/**
 * The IPC contract accepts 2500 chars per call; bounded segmentation permits
 * a user to edit up to 12000 chars without silently truncating their input.
 * Only explicit generation sends text to the signed offline runtime.
 */
internal object AiTextProcessing {
    const val MAX_EDITOR_CHARS = 12000
    private const val IPC_SAFE_LIMIT = 2350
    private const val MAX_PARTS = 12

    fun split(text: String, limit: Int): List<String> {
        require(limit > 0)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val chunks = ArrayList<String>()
        var rest = trimmed
        while (rest.isNotEmpty()) {
            if (rest.length <= limit) {
                chunks += rest
                break
            }
            val newline = rest.lastIndexOf('\n', limit)
            val space = rest.lastIndexOf(' ', limit)
            val candidate = maxOf(newline, space)
            val splitAt = if (candidate >= limit / 2) candidate + 1 else limit
            chunks += rest.substring(0, splitAt).trim()
            rest = rest.substring(splitAt).trimStart()
        }
        return chunks.filter { it.isNotBlank() }
    }

    suspend fun generate(
        context: Context,
        variant: AiVariant,
        input: String,
        progress: (Int, Int) -> Unit
    ): String {
        require(input.isNotBlank()) { "Escribe un texto antes de generar" }
        require(input.length <= MAX_EDITOR_CHARS) { "El texto supera 12000 caracteres" }
        val instruction = variant.instruction.trim()
        require(instruction.length in 1..AiVariantStore.MAX_INSTRUCTION)
        val promptPrefix = instruction + "\n\nTexto:\n"
        val maxPart = IPC_SAFE_LIMIT - promptPrefix.length - 95
        require(maxPart >= 500) { "Instrucción demasiado larga para este modelo" }
        val parts = split(input, maxPart)
        require(parts.isNotEmpty() && parts.size <= MAX_PARTS) {
            "El texto necesita demasiados fragmentos; divide el documento"
        }
        val answers = ArrayList<String>(parts.size)
        for ((index, part) in parts.withIndex()) {
            progress(index + 1, parts.size)
            val prefix = if (parts.size > 1)
                "Parte " + (index + 1) + " de " + parts.size +
                    ". Procesa solo este fragmento sin inventar ni omitir información.\n"
            else ""
            val prompt = prefix + promptPrefix + part
            check(prompt.length <= 2500) { "Petición excede límite de IPC" }
            val reply = LocalAiClient.generate(context, prompt).trim()
            if (reply.isBlank()) throw IllegalStateException(
                "Qwen devolvió texto vacío en el fragmento " + (index + 1)
            )
            answers += reply
        }
        val joined = answers.joinToString("\n\n")
        if (parts.size == 1 || variant.category !in
            setOf(AiCategory.SUMMARIZE, AiCategory.ANSWER)) return joined

        // One additional map-reduce pass makes long-form summaries coherent.
        // Only do so when the intermediate answer fits the same IPC contract.
        val combineInstruction =
            "Une estos resultados parciales de manera coherente, sin inventar datos:\n\n"
        if (joined.length + combineInstruction.length > IPC_SAFE_LIMIT) return joined
        progress(parts.size, parts.size)
        return LocalAiClient.generate(context, combineInstruction + joined).trim()
            .ifBlank { joined }
    }
}
