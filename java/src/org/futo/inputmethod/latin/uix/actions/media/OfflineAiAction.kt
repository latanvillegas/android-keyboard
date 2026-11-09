package org.futo.inputmethod.latin.uix.actions.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
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

private enum class AiOperation(val instruction: String) {
    CORRECT("Correct spelling and grammar without changing meaning."),
    REWRITE("Rewrite clearly and naturally, retaining the original meaning."),
    TRANSLATE("Translate into Spanish, or English when the input is already Spanish."),
    SUMMARIZE("Summarize accurately in two short sentences."),
    FORMAL("Rewrite in a professional and formal style.")
}

@Composable
private fun OfflineAiPanel(manager: KeyboardManagerForAction) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var targetSession by remember { mutableStateOf<Any?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.local_ai_privacy))
        OutlinedTextField(
            value = source,
            onValueChange = { source = it.take(2200) },
            label = { Text(stringResource(R.string.local_ai_input)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (task in listOf(AiOperation.CORRECT, AiOperation.REWRITE,
                AiOperation.TRANSLATE)) {
                TextButton(enabled = !busy && source.isNotBlank(), onClick = {
                    val token = manager.captureEditorSession()
                    targetSession = token
                    busy = true
                    output = ""
                    error = ""
                    scope.launch {
                        try {
                            output = LocalAiClient.generate(context,
                                task.instruction + "\n\nText:\n" + source)
                        } catch (cause: Exception) {
                            error = cause.message ?: "Local model unavailable"
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(when (task) {
                    AiOperation.CORRECT -> R.string.local_ai_correct
                    AiOperation.REWRITE -> R.string.local_ai_rewrite
                    else -> R.string.local_ai_translate
                })) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (task in listOf(AiOperation.SUMMARIZE, AiOperation.FORMAL)) {
                TextButton(enabled = !busy && source.isNotBlank(), onClick = {
                    val token = manager.captureEditorSession()
                    targetSession = token
                    busy = true
                    output = ""
                    error = ""
                    scope.launch {
                        try {
                            output = LocalAiClient.generate(context,
                                task.instruction + "\n\nText:\n" + source)
                        } catch (cause: Exception) {
                            error = cause.message ?: "Local model unavailable"
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(stringResource(if (task == AiOperation.SUMMARIZE)
                    R.string.local_ai_summarize else R.string.local_ai_formal)) }
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
