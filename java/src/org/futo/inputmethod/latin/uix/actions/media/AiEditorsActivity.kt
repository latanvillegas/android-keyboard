package org.futo.inputmethod.latin.uix.actions.media

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.runtime.MutableState
import org.futo.inputmethod.latin.R
import java.util.UUID

/**
 * Temporary in-memory bridge for long-form editing. Editor contents are never
 * persisted in preferences and are never exposed to another application.
 */
object AiDraftBridge {
    private var token: String? = null
    private var state: MutableState<String>? = null
    private var pending: String? = null

    fun open(source: MutableState<String>): String {
        val newToken = UUID.randomUUID().toString()
        token = newToken
        state = source
        pending = null
        return newToken
    }

    fun initial(requestToken: String): String? =
        if (token == requestToken) state?.value else null

    fun save(requestToken: String, edited: String) {
        if (token != requestToken) return
        state?.value = edited
        pending = edited
        state = null
        token = null
    }

    fun takePending(): String? = pending.also { pending = null }
}

/**
 * Full Android text editor for larger drafts. This is a real Activity, so the
 * FUTO IME can type normally; no nested InputConnection or clipboard transfer.
 */
class AiLongTextActivity : Activity() {
    companion object {
        const val EXTRA_TOKEN = "token"
        const val EXTRA_TEXT = "text"
        const val MAX_TEXT = 12000
    }

    private lateinit var input: EditText
    private lateinit var counter: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.ai_draft_editor_title)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(16))
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.ai_draft_editor_title)
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.ai_draft_editor_explanation)
            textSize = 14f
        })
        counter = TextView(this)
        root.addView(counter)
        input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 10
            gravity = Gravity.TOP or Gravity.START
            filters = arrayOf(InputFilter.LengthFilter(MAX_TEXT))
            setText(savedInstanceState?.getString("draft")
                ?: AiDraftBridge.initial(intent.getStringExtra(EXTRA_TOKEN).orEmpty())
                ?: intent.getStringExtra(EXTRA_TEXT).orEmpty())
            setSelection(text.length)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateCounter()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        root.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        updateCounter()
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(Button(this).apply {
            text = getString(R.string.ai_variant_cancel)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        actions.addView(Button(this).apply {
            text = getString(R.string.ai_draft_save)
            setOnClickListener {
                AiDraftBridge.save(intent.getStringExtra(EXTRA_TOKEN).orEmpty(), input.text.toString())
                finish()
            }
        }, LinearLayout.LayoutParams(0, dp(50), 1f))
        root.addView(actions)
        setContentView(root)
    }

    private fun updateCounter() {
        counter.text = getString(R.string.ai_draft_count, input.text.length, MAX_TEXT)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("draft", input.text.toString())
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}

/**
 * On-device variants manager. All operations are explicit; editing a factory
 * preset first duplicates it, so built-ins remain recoverable.
 */
class AiVariantManagerActivity : Activity() {
    companion object {
        const val EXTRA_CATEGORY = "category"
    }

    private val repo by lazy { AiVariantStore(applicationContext) }
    private var category = AiCategory.REWRITE
    private lateinit var scroll: ScrollView
    private lateinit var root: LinearLayout
    private var scrollBeforeUpdate = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        category = runCatching {
            AiCategory.valueOf(savedInstanceState?.getString("category")
                ?: intent.getStringExtra(EXTRA_CATEGORY).orEmpty())
        }.getOrDefault(AiCategory.REWRITE)
        title = getString(R.string.ai_variant_manage_title)
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("category", category.name)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun message(text: String) =
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun change(action: () -> Unit) {
        try {
            action()
            scrollBeforeUpdate = scroll.scrollY
            render()
        } catch (error: Exception) {
            message(error.localizedMessage ?: getString(R.string.ai_variant_error))
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setOnClickListener { action() }
        minHeight = dp(42)
    }

    private fun actionRow(vararg controls: Pair<String, () -> Unit>): HorizontalScrollView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        controls.forEach { (label, onClick) ->
            row.addView(button(label, onClick))
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun render() {
        val base = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(12))
        }
        base.addView(TextView(this).apply {
            text = getString(R.string.ai_variant_manage_title)
            textSize = 22f
        })
        base.addView(TextView(this).apply {
            text = getString(R.string.ai_variant_manage_description)
            textSize = 14f
            setPadding(0, dp(6), 0, dp(12))
        })
        base.addView(actionRow(*AiCategory.entries.map { c ->
            getString(c.labelRes) to { category = c; scrollBeforeUpdate = 0; render() }
        }.toTypedArray()))

        base.addView(button(
            getString(R.string.ai_variant_add, getString(category.labelRes))
        ) { editDialog(null) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ))
        val variants = repo.all().filter { it.category == category }
            .sortedWith(compareBy<AiVariant> { it.order }.thenBy { it.id })
        variants.forEach { preset ->
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(6), dp(10), dp(6), dp(10))
            }
            panel.addView(TextView(this).apply {
                text = (if (preset.favorite) "★ " else "☆ ") + preset.title +
                    if (!preset.visible) " (" + getString(R.string.ai_variant_hidden) + ")" else ""
                textSize = 17f
            })
            panel.addView(TextView(this).apply {
                text = preset.instruction
                textSize = 13f
                maxLines = 3
                alpha = 0.72f
            })
            panel.addView(actionRow(
                (if (preset.favorite) "★" else "☆") to {
                    change { repo.toggleFavorite(preset.id) }
                },
                (if (preset.visible) getString(R.string.ai_variant_hide)
                    else getString(R.string.ai_variant_show)) to {
                    change { repo.toggleVisibility(preset.id) }
                },
                "↑" to { change { repo.move(preset.id, -1) } },
                "↓" to { change { repo.move(preset.id, 1) } },
                getString(R.string.ai_variant_duplicate) to {
                    change {
                        val copied = repo.duplicate(preset.id)
                        editDialog(copied)
                    }
                },
                getString(R.string.ai_variant_edit) to {
                    if (preset.builtIn) {
                        change {
                            editDialog(repo.duplicate(preset.id))
                        }
                    } else {
                        editDialog(preset)
                    }
                },
                getString(R.string.ai_variant_delete) to {
                    if (preset.builtIn) {
                        message(getString(R.string.ai_variant_original_hide))
                    } else {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.ai_variant_delete))
                            .setMessage(preset.title)
                            .setNegativeButton(R.string.ai_variant_cancel, null)
                            .setPositiveButton(R.string.ai_variant_delete) { _, _ ->
                                change { repo.delete(preset.id) }
                            }.show()
                    }
                }
            ))
            base.addView(panel)
        }
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(base) }
        outer.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        outer.addView(button(getString(R.string.ai_variant_done)) { finish() },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        root = outer
        setContentView(outer)
        if (scrollBeforeUpdate != 0) {
            scroll.post { scroll.scrollTo(0, scrollBeforeUpdate) }
        }
    }

    private fun editDialog(existing: AiVariant?) {
        val name = EditText(this).apply {
            hint = getString(R.string.ai_variant_name)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(AiVariantStore.MAX_NAME))
            setText(existing?.title.orEmpty())
            setSingleLine(true)
        }
        val prompt = EditText(this).apply {
            hint = getString(R.string.ai_variant_instruction)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            maxLines = 8
            gravity = Gravity.TOP
            filters = arrayOf(InputFilter.LengthFilter(AiVariantStore.MAX_INSTRUCTION))
            setText(existing?.instruction.orEmpty())
        }
        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(name)
            addView(prompt)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.ai_variant_new else R.string.ai_variant_edit)
            .setView(editor)
            .setNegativeButton(R.string.ai_variant_cancel, null)
            .setPositiveButton(R.string.ai_variant_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    if (existing == null) {
                        repo.add(category, name.text.toString(), prompt.text.toString())
                    } else {
                        repo.edit(existing.id, name.text.toString(), prompt.text.toString())
                    }
                    dialog.dismiss()
                    scrollBeforeUpdate = scroll.scrollY
                    render()
                } catch (error: Exception) {
                    message(error.localizedMessage ?: getString(R.string.ai_variant_error))
                }
            }
        }
        dialog.show()
    }
}
