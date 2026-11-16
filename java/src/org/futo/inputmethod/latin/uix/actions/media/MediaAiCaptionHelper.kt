package org.futo.inputmethod.latin.uix.actions.media

import android.app.AlertDialog
import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R

/**
 * Optional, strictly text-only on-device assistance inside sticker/GIF editors.
 * No image pixels, editor contents or chat text are sent automatically.
 *
 * Generated text must be reviewed and selected before changing the caption;
 * sticker and GIF rendering remain local and unmodified.
 */
internal object MediaAiCaptionHelper {
    private val modes = listOf(
        R.string.media_ai_funny to
            "Crea textos divertidos, claros y originales.",
        R.string.media_ai_friendly to
            "Crea textos amistosos y naturales.",
        R.string.media_ai_romantic to
            "Crea frases románticas breves y respetuosas.",
        R.string.media_ai_professional to
            "Crea textos profesionales y concisos.",
        R.string.media_ai_translate to
            "Traduce el texto al español; si ya es español, tradúcelo al inglés.",
        R.string.media_ai_correct to
            "Corrige gramática y ortografía sin cambiar el sentido."
    )

    fun button(activity: ComponentActivity, target: EditText, targetName: String): Button =
        Button(activity).apply {
            setText(R.string.media_ai_caption)
            isAllCaps = false
            setOnClickListener { open(activity, target, targetName) }
        }

    private fun dp(activity: ComponentActivity, value: Int) =
        (value * activity.resources.displayMetrics.density + .5f).toInt()

    private fun open(activity: ComponentActivity, target: EditText, targetName: String) {
        val original = target.text.toString().take(80)
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 10),
                dp(activity, 18), dp(activity, 8))
        }
        form.addView(TextView(activity).apply {
            setText(R.string.media_ai_prompt)
            textSize = 14f
        })
        val description = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            maxLines = 4
            hint = activity.getString(R.string.media_ai_prompt)
            filters = arrayOf(InputFilter.LengthFilter(350))
            setText(original)
        }
        form.addView(description)
        val spinner = Spinner(activity)
        spinner.adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            modes.map { activity.getString(it.first) }
        )
        form.addView(spinner)
        AlertDialog.Builder(activity)
            .setTitle(R.string.media_ai_caption)
            .setView(form)
            .setNegativeButton(R.string.ai_variant_cancel, null)
            .setPositiveButton(R.string.media_ai_generate) { _, _ ->
                val instruction = modes[spinner.selectedItemPosition.coerceIn(modes.indices)].second
                val text = description.text.toString().trim()
                if (text.isBlank()) {
                    Toast.makeText(activity, R.string.media_ai_prompt, Toast.LENGTH_SHORT).show()
                } else {
                    suggest(activity, target, targetName, text, instruction)
                }
            }
            .show()
    }

    private fun suggest(
        activity: ComponentActivity,
        target: EditText,
        targetName: String,
        description: String,
        style: String
    ) {
        val progress = ProgressBar(activity)
        val box = LinearLayout(activity).apply {
            gravity = Gravity.CENTER
            setPadding(dp(activity, 30), dp(activity, 25),
                dp(activity, 30), dp(activity, 25))
            orientation = LinearLayout.VERTICAL
            addView(TextView(activity).apply {
                setText(R.string.media_ai_wait)
                textSize = 15f
            })
            addView(progress)
        }
        val waitDialog = AlertDialog.Builder(activity)
            .setTitle(R.string.media_ai_caption)
            .setView(box)
            .setNegativeButton(R.string.ai_variant_cancel, null)
            .create()
        waitDialog.show()
        activity.lifecycleScope.launch {
            try {
                val prompt = "Estás creando texto superpuesto para un " + targetName +
                    ". " + style + "\n" +
                    "Sugiere cuatro frases distintas en español, máximo 55 caracteres cada una. " +
                    "Devuelve cuatro líneas numeradas, sin explicación adicional." +
                    "\nIdea o texto original:\n" + description
                val response = LocalAiClient.generate(activity, prompt)
                if (activity.isFinishing || activity.isDestroyed || !waitDialog.isShowing) return@launch
                val choices = response.lines().map { line ->
                    line.trim().replace(Regex("^[0-9]+[.)\\s-]+"), "")
                        .trim().trim('"', '“', '”')
                }.filter { it.isNotBlank() }.distinct().take(4)
                waitDialog.dismiss()
                if (choices.isEmpty()) {
                    Toast.makeText(activity, R.string.local_ai_empty_response,
                        Toast.LENGTH_LONG).show()
                } else {
                    AlertDialog.Builder(activity)
                        .setTitle(R.string.media_ai_preview)
                        .setItems(choices.toTypedArray()) { _, position ->
                            // Explicit selection changes the editor's caption.
                            target.setText(choices[position].take(80))
                            target.setSelection(target.text.length)
                        }
                        .setNegativeButton(R.string.ai_variant_cancel, null)
                        .show()
                }
            } catch (cancelled: CancellationException) {
                waitDialog.dismiss()
                throw cancelled
            } catch (error: Exception) {
                waitDialog.dismiss()
                if (!activity.isFinishing && !activity.isDestroyed) {
                    Toast.makeText(activity,
                        activity.getString(R.string.media_ai_error) + "\n" +
                            (error.localizedMessage ?: error.javaClass.simpleName),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
