package org.futo.inputmethod.latin.uix.actions.media

import android.content.Context
import androidx.annotation.StringRes
import org.futo.inputmethod.latin.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Each action has independent, extensible style presets. */
enum class AiCategory(@StringRes val labelRes: Int) {
    ANSWER(R.string.ai_nav_answer),
    CORRECT(R.string.local_ai_correct),
    REWRITE(R.string.local_ai_rewrite),
    TRANSLATE(R.string.local_ai_translate),
    SUMMARIZE(R.string.local_ai_summarize),
    FORMAL(R.string.local_ai_formal),
    INFORMAL(R.string.ai_nav_informal)
}

data class AiVariant(
    val id: String,
    val category: AiCategory,
    val title: String,
    val instruction: String,
    val builtIn: Boolean,
    val visible: Boolean = true,
    val favorite: Boolean = false,
    val order: Int = 0
)

/**
 * Private, schema-versioned preference store. Preset instructions cannot be
 * overwritten: editing a preset creates a personal copy. Persisted values
 * contain no editor text or inference output.
 */
class AiVariantStore(context: Context) {
    companion object {
        const val PREF_FILE = "offline_ai_variants"
        const val PREF_KEY = "variants_v1"
        const val MAX_NAME = 48
        const val MAX_INSTRUCTION = 1200
        private const val MAX_CUSTOM = 80

        private fun defaults(): List<AiVariant> = buildList {
            fun group(category: AiCategory, vararg entries: Pair<String, String>) {
                entries.forEachIndexed { i, (name, prompt) ->
                    add(AiVariant(
                        id = category.name.lowercase() + "_default_" + i,
                        category = category,
                        title = name,
                        instruction = prompt + " Devuelve solamente el texto solicitado, sin encabezados ni explicaciones.",
                        builtIn = true,
                        order = i
                    ))
                }
            }
            group(AiCategory.ANSWER,
                "Breve" to "Responde a esta petición de forma clara y breve en español.",
                "Amigable" to "Redacta una respuesta cálida y amigable en español.",
                "Profesional" to "Redacta una respuesta profesional, precisa y respetuosa.",
                "Empática" to "Redacta una respuesta empática y comprensiva.",
                "Agradecimiento" to "Escribe una respuesta de agradecimiento natural.",
                "Rechazo amable" to "Responde negativamente de manera amable.",
                "Persuasiva" to "Redacta una respuesta persuasiva sin inventar hechos."
            )
            group(AiCategory.CORRECT,
                "Ortografía" to "Corrige errores ortográficos conservando el estilo.",
                "Gramática" to "Corrige errores gramaticales conservando el sentido.",
                "Puntuación" to "Corrige solamente la puntuación.",
                "Claridad" to "Corrige para mayor claridad sin alterar el significado.",
                "Corrección completa" to "Corrige ortografía, gramática y puntuación.",
                "Mantener estilo" to "Corrige manteniendo el tono y vocabulario originales."
            )
            group(AiCategory.REWRITE,
                "Mejorar" to "Mejora la redacción y claridad conservando el significado.",
                "Profesional" to "Reescribe de forma profesional y concisa.",
                "Amigable" to "Reescribe con tono amable y cercano.",
                "Informal" to "Reescribe con lenguaje informal natural.",
                "Formal" to "Reescribe con estilo formal y respetuoso.",
                "Más educado" to "Reescribe con más cortesía y tacto.",
                "Simplificar" to "Simplifica las frases sin perder la información esencial.",
                "Acortar" to "Acorta el texto preservando las ideas principales.",
                "Ampliar" to "Amplía con explicaciones útiles sin inventar hechos."
            )
            group(AiCategory.TRANSLATE,
                "Español" to "Traduce fielmente al español.",
                "Inglés" to "Translate the text faithfully into English.",
                "Portugués" to "Traduza fielmente para português.",
                "Francés" to "Traduisez fidèlement en français.",
                "Detectar idioma" to "Detecta el idioma y traduce al español o al inglés si ya está en español.",
                "Otro idioma" to "Traduce al idioma indicado en el texto; si no se indica, pide aclaración."
            )
            group(AiCategory.SUMMARIZE,
                "Una frase" to "Resume el texto en una sola frase.",
                "Tres puntos" to "Resume el contenido en tres puntos breves.",
                "Ejecutivo" to "Redacta un resumen ejecutivo y profesional.",
                "Detallado" to "Resume detalladamente sin omitir hechos importantes.",
                "Ideas principales" to "Extrae las ideas principales."
            )
            group(AiCategory.FORMAL,
                "Empresarial" to "Reformula con estilo empresarial.",
                "Académico" to "Reformula con registro académico.",
                "Institucional" to "Reformula con lenguaje institucional neutro.",
                "Muy respetuoso" to "Reformula con máxima cortesía."
            )
            group(AiCategory.INFORMAL,
                "Casual" to "Reescribe con un tono casual.",
                "Cercano" to "Reescribe con tono natural y cercano.",
                "Amistoso" to "Reescribe en tono amistoso y positivo.",
                "Conversacional" to "Reescribe como una conversación cotidiana.",
                "Redes sociales" to "Adapta a una publicación breve en redes sociales."
            )
        }
    }

    private val preferences = context.applicationContext.getSharedPreferences(
        PREF_FILE, Context.MODE_PRIVATE
    )

    fun all(): List<AiVariant> {
        val originals = defaults()
        val json = preferences.getString(PREF_KEY, null) ?: return originals
        return try {
            val root = JSONObject(json)
            if (root.optInt("version") != 1) return originals
            val stored = root.optJSONArray("items") ?: return originals
            val values = ArrayList<AiVariant>()
            for (index in 0 until minOf(stored.length(), 300)) {
                val item = stored.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.length !in 3..100) continue
                val category = runCatching {
                    AiCategory.valueOf(item.optString("category"))
                }.getOrNull() ?: continue
                values += AiVariant(
                    id = id,
                    category = category,
                    title = item.optString("title").take(MAX_NAME),
                    instruction = item.optString("instruction").take(MAX_INSTRUCTION),
                    builtIn = item.optBoolean("builtin"),
                    visible = item.optBoolean("visible", true),
                    favorite = item.optBoolean("favorite", false),
                    order = item.optInt("order", index).coerceIn(0, 10000)
                )
            }
            val saved = values.associateBy { it.id }
            val result = originals.map { original ->
                saved[original.id]?.takeIf { it.category == original.category }?.let {
                    original.copy(visible = it.visible, favorite = it.favorite, order = it.order)
                } ?: original
            }.toMutableList()
            val presetIds = originals.mapTo(HashSet()) { it.id }
            result += values.filter {
                !it.builtIn && it.id !in presetIds &&
                    it.title.isNotBlank() && it.instruction.isNotBlank()
            }.distinctBy { it.id }.take(MAX_CUSTOM)
            result.sortedWith(compareBy<AiVariant>({ it.category.ordinal }, { it.order }, { it.id }))
        } catch (_: Exception) {
            originals // Bad JSON must never prevent the keyboard opening.
        }
    }

    fun list(category: AiCategory): List<AiVariant> =
        all().filter { it.category == category && it.visible }
            .sortedWith(compareByDescending<AiVariant> { it.favorite }.thenBy { it.order })

    fun find(id: String): AiVariant? = all().firstOrNull { it.id == id }

    private fun save(items: List<AiVariant>) {
        val values = JSONArray()
        items.forEach { item ->
            values.put(JSONObject()
                .put("id", item.id).put("category", item.category.name)
                .put("title", item.title).put("instruction", item.instruction)
                .put("builtin", item.builtIn).put("visible", item.visible)
                .put("favorite", item.favorite).put("order", item.order))
        }
        val payload = JSONObject().put("version", 1).put("items", values)
        preferences.edit().putString(PREF_KEY, payload.toString()).apply()
    }

    fun add(category: AiCategory, name: String, instruction: String): AiVariant {
        val title = name.trim()
        val text = instruction.trim()
        require(title.isNotEmpty() && title.length <= MAX_NAME) {
            "Nombre: entre 1 y 48 caracteres"
        }
        require(text.isNotEmpty() && text.length <= MAX_INSTRUCTION) {
            "Instrucción: entre 1 y 1200 caracteres"
        }
        val items = all().toMutableList()
        require(items.count { !it.builtIn } < MAX_CUSTOM) {
            "Máximo 80 variantes personalizadas"
        }
        val order = (items.filter { it.category == category }.maxOfOrNull { it.order } ?: -1) + 1
        val added = AiVariant(
            id = "user_" + UUID.randomUUID().toString(),
            category = category, title = title, instruction = text,
            builtIn = false, order = order
        )
        items.add(added)
        save(items)
        return added
    }

    fun edit(id: String, name: String, instruction: String) {
        val original = find(id) ?: throw IllegalArgumentException("Variante no encontrada")
        require(!original.builtIn) { "Duplica una variante original para editarla" }
        val title = name.trim()
        val prompt = instruction.trim()
        require(title.isNotBlank() && title.length <= MAX_NAME)
        require(prompt.isNotBlank() && prompt.length <= MAX_INSTRUCTION)
        save(all().map {
            if (it.id == id) it.copy(title = title, instruction = prompt) else it
        })
    }

    fun duplicate(id: String): AiVariant {
        val source = find(id) ?: throw IllegalArgumentException("Variante no encontrada")
        return add(source.category, source.title.take(MAX_NAME - 6) + " copia", source.instruction)
    }

    fun toggleFavorite(id: String) =
        save(all().map { if (it.id == id) it.copy(favorite = !it.favorite) else it })

    fun toggleVisibility(id: String) =
        save(all().map { if (it.id == id) it.copy(visible = !it.visible) else it })

    fun delete(id: String) {
        val source = find(id) ?: return
        require(!source.builtIn) { "Las originales se ocultan, no se eliminan" }
        save(all().filterNot { it.id == id })
    }

    fun move(id: String, delta: Int) {
        val items = all()
        val source = items.firstOrNull { it.id == id } ?: return
        val group = items.filter { it.category == source.category }
            .sortedWith(compareBy<AiVariant> { it.order }.thenBy { it.id }).toMutableList()
        val index = group.indexOfFirst { it.id == id }
        val destination = (index + delta).coerceIn(0, group.lastIndex)
        if (destination == index) return
        val value = group.removeAt(index)
        group.add(destination, value)
        val newOrder = group.mapIndexed { order, item -> item.id to order }.toMap()
        save(items.map { item ->
            newOrder[item.id]?.let { item.copy(order = it) } ?: item
        })
    }
}
