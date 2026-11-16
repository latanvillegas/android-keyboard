package org.futo.inputmethod.latin.uix.actions.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.InputFilter
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import org.futo.inputmethod.latin.uix.AiPromptEditorHeightSetting
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import java.util.concurrent.atomic.AtomicReference
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.ActionTextEditor
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction

/**
 * Generative rewriting never reads passwords or chat histories.
 * Only explicitly entered text reaches the on-device, signature-verified AI.
 */
val OfflineAiAction = Action(
    icon = R.drawable.cpu,
    name = R.string.local_ai_title,
    canShowKeyboard = true,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        object : ActionWindow() {
            // The input editor belongs above the live keyboard, not instead of it.
            override val onlyShowAboveKeyboard: Boolean = true

            @Composable
            override fun windowName(): String = stringResource(R.string.local_ai_title)

            @Composable
            override fun WindowTitleBar(rowScope: RowScope) {
                super.WindowTitleBar(rowScope)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                OfflineAiPanel(manager)
            }
        }
    }
)


/** Operation preselected by the one-row IA shortcut's long-press menu. */
enum class AiQuickMode(val titleRes: Int, val instruction: String) {
    ANSWER(R.string.local_ai_write,
        "Responde de manera clara y breve a esta petición en español."),
    CORRECT(R.string.local_ai_correct,
        "Correct spelling and grammar without changing meaning."),
    REWRITE(R.string.local_ai_rewrite,
        "Rewrite naturally and clearly while retaining original meaning."),
    TRANSLATE(R.string.local_ai_translate,
        "Translate into Spanish, or English when the input is in Spanish."),
    SUMMARIZE(R.string.local_ai_summarize,
        "Summarize accurately in two short sentences."),
    FORMAL(R.string.local_ai_formal,
        "Rewrite the text in a formal professional style.")
}

/** One-shot operation hand-off from shortcut menu to a fresh action window. */
object AiQuickLaunch {
    private val pending = AtomicReference<AiQuickMode?>(null)
    fun enqueue(operation: AiQuickMode) { pending.set(operation) }
    fun consume(): AiQuickMode? = pending.getAndSet(null)
}

private enum class AiOperation(val instruction: String) {
    CORRECT("Correct spelling and grammar without changing meaning."),
    REWRITE("Rewrite clearly and naturally, retaining the original meaning."),
    TRANSLATE("Translate into Spanish, or English when the input is already Spanish."),
    SUMMARIZE("Summarize accurately in two short sentences."),
    FORMAL("Rewrite in a professional and formal style.")
}


/**
 * An equal-weight cell fills its share of the entire panel width.
 * The short two-line label avoids a third row on narrow screens.
 */
@Composable
private fun RowScope.AiActionCell(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
    ) {
        Text(
            title,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            maxLines = 2
        )
    }
}

@Composable
private fun OfflineAiPanel(manager: KeyboardManagerForAction) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceState = remember { mutableStateOf("") }
    val source by sourceState
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var targetSession by remember { mutableStateOf<Any?>(null) }
    val quickOperation = remember { AiQuickLaunch.consume() }
    val editorHeight = useDataStoreValue(AiPromptEditorHeightSetting).coerceIn(40, 240)

    // Preserve the original editor-session check before permitting insertion
    // into the application after asynchronous on-device generation.
    val runPrompt: (String, Boolean) -> Unit = { prompt, diagnostic ->
        if (!busy) {
            targetSession = if (diagnostic) null else manager.captureEditorSession()
            busy = true
            output = ""
            error = ""
            scope.launch {
                try {
                    output = LocalAiClient.generate(context, prompt)
                    if (output.isBlank()) {
                        error = context.getString(R.string.local_ai_empty_response)
                    }
                } catch (cause: Exception) {
                    val details = cause.localizedMessage ?: cause.javaClass.simpleName
                    error = if (diagnostic) {
                        context.getString(R.string.local_ai_test_failed) + ": " + details
                    } else details
                } finally {
                    busy = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // A Compose TextField inside an InputMethodService cannot receive
        // its own IME input. FUTO ActionTextEditor supplies an InputConnection
        // override so taps and physical/software keys reach this editor.
        Surface(
            modifier = Modifier.fillMaxWidth().height(editorHeight.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                ActionTextEditor(
                    text = sourceState,
                    multiline = true,
                    autocorrect = true,
                    autofocus = true,
                    inputFilters = arrayOf(InputFilter.LengthFilter(2200)),
                    placeholder = stringResource(R.string.local_ai_input),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Text(
            stringResource(R.string.local_ai_usage_hint),
            style = MaterialTheme.typography.bodySmall
        )
        if (quickOperation != null) {
            Text(
                stringResource(R.string.ai_quick_selected_operation,
                    stringResource(quickOperation.titleRes)),
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(enabled = !busy && source.isNotBlank(), onClick = {
                targetSession = manager.captureEditorSession()
                busy = true
                output = ""
                error = ""
                scope.launch {
                    try {
                        output = LocalAiClient.generate(
                            context,
                            quickOperation.instruction + "\n\nTexto:\n" + source
                        )
                        if (output.isBlank()) {
                            error = context.getString(R.string.local_ai_empty_response)
                        }
                    } catch (cause: Exception) {
                        error = cause.localizedMessage ?: cause.javaClass.simpleName
                    } finally {
                        busy = false
                    }
                }
            }) { Text(stringResource(R.string.ai_quick_execute)) }
        }
        // All seven commands share the FULL panel width in just two rows.
        // Operations remain explicitly triggered; no automatic chat reading.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AiActionCell(
                stringResource(R.string.local_ai_write),
                enabled = !busy && source.isNotBlank()
            ) {
                runPrompt(
                    "Responde a la siguiente petición en español, de forma clara." +
                        "\n\nPetición:\n" + source,
                    false
                )
            }
            for (task in listOf(
                AiOperation.CORRECT, AiOperation.REWRITE, AiOperation.TRANSLATE
            )) {
                AiActionCell(
                    stringResource(when (task) {
                        AiOperation.CORRECT -> R.string.local_ai_correct
                        AiOperation.REWRITE -> R.string.local_ai_rewrite
                        else -> R.string.local_ai_translate
                    }),
                    enabled = !busy && source.isNotBlank()
                ) {
                    runPrompt(task.instruction + "\n\nText:\n" + source, false)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            for (task in listOf(AiOperation.SUMMARIZE, AiOperation.FORMAL)) {
                AiActionCell(
                    stringResource(if (task == AiOperation.SUMMARIZE) {
                        R.string.local_ai_summarize
                    } else R.string.local_ai_formal),
                    enabled = !busy && source.isNotBlank()
                ) {
                    runPrompt(task.instruction + "\n\nText:\n" + source, false)
                }
            }
            AiActionCell(
                stringResource(R.string.local_ai_test_engine),
                enabled = !busy
            ) {
                runPrompt("Responde solo con una palabra: LISTO.", true)
            }
        }
        if (busy) Text(stringResource(R.string.local_ai_busy))
        if (error.isNotBlank()) Text(error)
        if (output.isNotBlank()) {
            Text(stringResource(R.string.local_ai_preview))
            Text(output.take(12000))
            Row {
                Button(onClick = {
                    val clipboard = context.getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Local AI result", output)
                    )
                }) { Text(stringResource(R.string.local_ai_copy)) }
                TextButton(
                    enabled = !busy && targetSession != null &&
                        manager.isEditorSessionCurrent(targetSession),
                    onClick = {
                        if (manager.isEditorSessionCurrent(targetSession)) {
                            manager.typeText(output)
                        } else {
                            Toast.makeText(context,
                                R.string.local_ai_editor_changed,
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text(stringResource(R.string.local_ai_insert)) }
            }
        }
        TextButton(onClick = {
            if (!LocalAiClient.openSetup(context)) {
                Toast.makeText(context, R.string.local_ai_install_runtime,
                    Toast.LENGTH_LONG).show()
            }
        }) { Text(stringResource(R.string.local_ai_models)) }
    }
}
