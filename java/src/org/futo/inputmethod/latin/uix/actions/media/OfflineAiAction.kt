package org.futo.inputmethod.latin.uix.actions.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.text.InputFilter
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionTextEditor
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.AiEditorGrowthModeSetting
import org.futo.inputmethod.latin.uix.AiEditorMaxHeightSetting
import org.futo.inputmethod.latin.uix.AiEditorMinHeightSetting
import org.futo.inputmethod.latin.uix.AiPromptEditorHeightSetting
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import java.util.concurrent.atomic.AtomicReference

/**
 * The existing signature-authenticated local Qwen/MediaPipe IPC is unchanged.
 * Only text explicitly written or pasted by the user is sent for inference.
 */
val OfflineAiAction = Action(
    icon = R.drawable.cpu,
    name = R.string.local_ai_title,
    canShowKeyboard = true,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        object : ActionWindow() {
            override val onlyShowAboveKeyboard = true

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

/** Long-press IA actions open the matching variants, not an older fixed prompt. */
enum class AiQuickMode {
    ANSWER, CORRECT, REWRITE, TRANSLATE, SUMMARIZE, FORMAL, INFORMAL
}

object AiQuickLaunch {
    private val pending = AtomicReference<AiQuickMode?>(null)
    fun enqueue(operation: AiQuickMode) { pending.set(operation) }
    fun consume(): AiQuickMode? = pending.getAndSet(null)
}

@Composable
private fun RowScope.PrimaryAiItem(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
            .padding(horizontal = 2.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
    ) {
        Text(
            label,
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
    val variantsStore = remember(context) { AiVariantStore(context) }
    val uiPrefs = remember(context) {
        context.applicationContext.getSharedPreferences("ai_panel_navigation", Context.MODE_PRIVATE)
    }
    val shortcut = remember { AiQuickLaunch.consume() }
    val sourceState = remember { mutableStateOf(AiDraftBridge.takePending().orEmpty()) }
    val source by sourceState
    var category by remember {
        mutableStateOf(shortcut?.name?.let { runCatching { AiCategory.valueOf(it) }.getOrNull() })
    }
    var selectedVariantId by remember { mutableStateOf<String?>(null) }
    var moreExpanded by remember { mutableStateOf(false) }
    var lastMore by remember {
        mutableStateOf(uiPrefs.getString("lastMoreCategory", "").orEmpty())
    }

    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var targetSession by remember { mutableStateOf<Any?>(null) }

    var visualLines by remember { mutableIntStateOf(1) }
    val editorMode = useDataStoreValue(AiEditorGrowthModeSetting)
    val manualHeight = useDataStoreValue(AiPromptEditorHeightSetting)
    val minHeight = useDataStoreValue(AiEditorMinHeightSetting)
    val maxHeight = useDataStoreValue(AiEditorMaxHeightSetting)
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val editorHeight = AiEditorSizing.resolve(
        mode = editorMode,
        visualLines = visualLines,
        manual = manualHeight,
        hybridMin = minHeight,
        hybridMax = maxHeight,
        screenHeight = screenHeight
    )

    // An Activity can edit a variant. Refresh on SharedPreferences changes
    // without ever writing the user's draft text to disk.
    var variantRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val prefs = context.applicationContext.getSharedPreferences(
            AiVariantStore.PREF_FILE, Context.MODE_PRIVATE
        )
        val observer = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
            if (changedKey == AiVariantStore.PREF_KEY) variantRevision++
        }
        prefs.registerOnSharedPreferenceChangeListener(observer)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(observer) }
    }
    val available = remember(category, variantRevision) {
        category?.let { variantsStore.list(it) }.orEmpty()
    }
    val currentVariant = available.firstOrNull { it.id == selectedVariantId }
        ?: available.firstOrNull()

    val showError: (Throwable) -> Unit = { cause ->
        error = cause.localizedMessage ?: cause.javaClass.simpleName
    }

    val generate: (AiVariant) -> Unit = { variant ->
        if (!busy && source.isNotBlank()) {
            targetSession = manager.captureEditorSession()
            busy = true
            output = ""
            error = ""
            progress = ""
            val content = source
            scope.launch {
                try {
                    output = AiTextProcessing.generate(context, variant, content) { part, total ->
                        progress = context.getString(R.string.ai_chunk_progress, part, total)
                    }
                } catch (cause: Exception) {
                    showError(cause)
                } finally {
                    busy = false
                    progress = ""
                }
            }
        }
    }

    val test: () -> Unit = {
        if (!busy) {
            targetSession = null
            busy = true
            output = ""
            error = ""
            progress = ""
            scope.launch {
                try {
                    output = LocalAiClient.generate(
                        context, "Responde solo con una palabra: LISTO."
                    )
                    if (output.isBlank()) {
                        error = context.getString(R.string.local_ai_empty_response)
                    }
                } catch (cause: Exception) {
                    error = context.getString(R.string.local_ai_test_failed) +
                        ": " + (cause.localizedMessage ?: cause.javaClass.simpleName)
                } finally {
                    busy = false
                }
            }
        }
    }

    val openVariantManager: (Boolean) -> Unit = { create ->
        context.startActivity(
            Intent(context, AiVariantManagerActivity::class.java)
                .putExtra(AiVariantManagerActivity.EXTRA_CATEGORY,
                    (category ?: AiCategory.REWRITE).name)
                .putExtra(AiVariantManagerActivity.EXTRA_CREATE, create)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    val openCategory: (AiCategory) -> Unit = { chosen ->
        selectedVariantId = null
        category = chosen
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // One line of editing controls, not a permanent second action toolbar.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(R.string.local_ai_input) + "  " +
                    source.length + " / " + AiTextProcessing.MAX_EDITOR_CHARS,
                style = MaterialTheme.typography.labelSmall
            )
            Row {
                TextButton(
                    onClick = {
                        val token = AiDraftBridge.open(sourceState)
                        context.startActivity(
                            Intent(context, AiLongTextActivity::class.java)
                                .putExtra(AiLongTextActivity.EXTRA_TOKEN, token)
                                .putExtra(AiLongTextActivity.EXTRA_TEXT, source)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.height(32.dp)
                        .semantics {
                            contentDescription = context.getString(R.string.ai_editor_expand)
                        },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) { Text("⤢", fontSize = 18.sp) }
                TextButton(
                    onClick = {
                        if (!LocalAiClient.openSetup(context)) {
                            Toast.makeText(context, R.string.local_ai_install_runtime,
                                Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.height(32.dp)
                        .semantics {
                            contentDescription = context.getString(R.string.ai_ai_settings)
                        },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) { Text("⚙", fontSize = 18.sp) }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().height(editorHeight.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                ActionTextEditor(
                    text = sourceState,
                    multiline = true,
                    autocorrect = true,
                    autofocus = true,
                    inputFilters = arrayOf(
                        InputFilter.LengthFilter(AiTextProcessing.MAX_EDITOR_CHARS)
                    ),
                    placeholder = stringResource(R.string.local_ai_input),
                    dynamicHeight = true,
                    onVisualLineCountChanged = { lines ->
                        if (lines != visualLines) visualLines = lines
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (category == null) {
            // One compact row only: three primary tasks + a pop-up More menu.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PrimaryAiItem(stringResource(R.string.ai_nav_answer)) {
                    openCategory(AiCategory.ANSWER)
                }
                PrimaryAiItem(stringResource(R.string.local_ai_correct)) {
                    openCategory(AiCategory.CORRECT)
                }
                PrimaryAiItem(stringResource(R.string.local_ai_rewrite)) {
                    openCategory(AiCategory.REWRITE)
                }
                Box(Modifier.weight(1f)) {
                    TextButton(
                        onClick = { moreExpanded = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    ) {
                        Text(
                            stringResource(R.string.ai_nav_more) +
                                if (lastMore.isNotBlank()) " •" else "",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false }
                    ) {
                        for (option in listOf(
                            AiCategory.TRANSLATE, AiCategory.SUMMARIZE,
                            AiCategory.FORMAL, AiCategory.INFORMAL
                        )) {
                            DropdownMenuItem(
                                text = { Text(stringResource(option.labelRes)) },
                                onClick = {
                                    moreExpanded = false
                                    lastMore = option.name
                                    uiPrefs.edit().putString(
                                        "lastMoreCategory", option.name
                                    ).apply()
                                    openCategory(option)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.local_ai_test_engine)) },
                            onClick = { moreExpanded = false; test() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_variant_manage)) },
                            onClick = { moreExpanded = false; openVariantManager(false) }
                        )
                    }
                }
            }
        } else {
            val selectedCategory = category!!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { category = null; selectedVariantId = null }) {
                    Text("‹ " + stringResource(R.string.ai_nav_back))
                }
                Text(
                    stringResource(selectedCategory.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
                TextButton(onClick = { openVariantManager(false) }) {
                    Text("⚙", fontSize = 17.sp)
                }
            }

            // Swiping lets nine rewriting styles fit ONE row without making
            // the keyboard or text editor smaller.
            Row(
                modifier = Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (variant in available) {
                    val selected = currentVariant?.id == variant.id
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        onClick = { selectedVariantId = variant.id },
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text(
                            (if (variant.favorite) "★ " else "") + variant.title,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 11.dp),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }
                TextButton(
                    onClick = { openVariantManager(true) },
                    modifier = Modifier.heightIn(min = 44.dp)
                ) { Text(stringResource(R.string.ai_variant_add_short)) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { currentVariant?.let(generate) },
                    enabled = !busy && source.isNotBlank() && currentVariant != null,
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                ) { Text(stringResource(R.string.ai_variant_generate)) }
                TextButton(
                    onClick = {
                        currentVariant?.let {
                            variantsStore.toggleFavorite(it.id)
                            variantRevision++
                        }
                    },
                    enabled = currentVariant != null && !busy,
                    modifier = Modifier.heightIn(min = 44.dp)
                ) {
                    Text(if (currentVariant?.favorite == true) "★" else "☆", fontSize = 19.sp)
                }
            }
            if (available.isEmpty()) {
                Text(stringResource(R.string.ai_variants_empty))
            }
        }

        if (busy) {
            Text(
                if (progress.isNotBlank()) progress
                else stringResource(R.string.local_ai_busy),
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (output.isNotBlank()) {
            Text(stringResource(R.string.local_ai_preview))
            Text(output.take(12000))
            Row {
                Button(onClick = {
                    val clipboard = context.getSystemService(
                        Context.CLIPBOARD_SERVICE
                    ) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("FUTO Local AI", output))
                }) { Text(stringResource(R.string.local_ai_copy)) }
                TextButton(
                    enabled = !busy && targetSession != null &&
                        manager.isEditorSessionCurrent(targetSession),
                    onClick = {
                        if (manager.isEditorSessionCurrent(targetSession)) {
                            manager.typeText(output)
                        } else {
                            Toast.makeText(context, R.string.local_ai_editor_changed,
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text(stringResource(R.string.local_ai_insert)) }
            }
        }
    }
}
