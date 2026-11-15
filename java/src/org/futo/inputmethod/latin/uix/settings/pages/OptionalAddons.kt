package org.futo.inputmethod.latin.uix.settings.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.actions.media.FluentHqPackStore
import org.futo.inputmethod.latin.uix.actions.media.GifEditorActivity
import org.futo.inputmethod.latin.uix.actions.media.LocalAiClient
import org.futo.inputmethod.latin.uix.actions.media.MediaExportActivity
import org.futo.inputmethod.latin.uix.actions.media.MediaImportActivity
import org.futo.inputmethod.latin.uix.actions.media.MediaLibrary
import org.futo.inputmethod.latin.uix.actions.media.StickerEditorActivity
import org.futo.inputmethod.latin.uix.settings.ScreenTitle

/**
 * Sources are catalogue URLs opened explicitly in the browser, never downloaded
 * silently by the IME. User-selected files pass through existing SAF importers.
 * Fluent and media assets remain external to the release APK.
 */
enum class AddonKind(
    val titleRes: Int,
    val descriptionRes: Int,
    val preferenceKey: String,
    val defaultSource: String
) {
    MEDIA(
        R.string.addons_media_title,
        R.string.addons_media_description,
        "source_media",
        "https://tenor.com/"
    ),
    FLUENT(
        R.string.addons_fluent_title,
        R.string.addons_fluent_description,
        "source_fluent",
        "https://github.com/latanvillegas/android-keyboard/actions/runs/43757919595"
    ),
    AI(
        R.string.addons_ai_title,
        R.string.addons_ai_description,
        "source_ai",
        "https://ai.google.dev/gemma/docs/integrations/mobile"
    )
}

private fun openMediaActivity(context: Context, activity: Class<*>) {
    try {
        context.startActivity(Intent(context, activity).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (error: Exception) {
        Toast.makeText(
            context,
            error.localizedMessage ?: context.getString(R.string.addons_open_failed),
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun openVerifiedSource(context: Context, source: String) {
    val uri = try { Uri.parse(source.trim()) } catch (_: Exception) { null }
    if (uri == null || !uri.scheme.equals("https", ignoreCase = true) ||
        uri.host.isNullOrBlank() || uri.userInfo != null) {
        Toast.makeText(context, R.string.addons_https_required, Toast.LENGTH_LONG).show()
        return
    }
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        Toast.makeText(context, R.string.addons_open_failed, Toast.LENGTH_LONG).show()
    }
}

@Composable
fun OptionalAddonScreen(navController: NavHostController, kind: AddonKind) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("optional_addon_sources_v1", Context.MODE_PRIVATE)
    }
    var source by remember(kind, preferences) {
        mutableStateOf(preferences.getString(kind.preferenceKey, kind.defaultSource)
            ?: kind.defaultSource)
    }
    val scope = rememberCoroutineScope()
    val fluentInstalled by produceState(
        initialValue = FluentHqPackStore.isInstalled(context),
        context
    ) {
        MediaLibrary.changes.collect {
            value = FluentHqPackStore.isInstalled(context)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(stringResource(kind.titleRes), showBack = true, navController)
        Text(stringResource(kind.descriptionRes), style = MaterialTheme.typography.bodyMedium)

        Button(
            onClick = {
                when (kind) {
                    AddonKind.MEDIA, AddonKind.FLUENT ->
                        openMediaActivity(context, MediaImportActivity::class.java)
                    AddonKind.AI -> {
                        if (!LocalAiClient.openSetup(context)) {
                            Toast.makeText(
                                context, R.string.local_ai_install_runtime, Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.addons_import_manual))
        }

        Text(stringResource(R.string.addons_download_title),
            style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.addons_download_instruction),
            style = MaterialTheme.typography.bodySmall)

        OutlinedTextField(
            value = source,
            onValueChange = {
                source = it.take(2048)
                preferences.edit().putString(kind.preferenceKey, source).apply()
            },
            label = { Text(stringResource(R.string.addons_link_label)) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            maxLines = 3
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openVerifiedSource(context, source) }) {
                Text(stringResource(R.string.addons_open_download))
            }
            OutlinedButton(onClick = {
                preferences.edit().remove(kind.preferenceKey).apply()
                source = kind.defaultSource
            }) {
                Text(stringResource(R.string.addons_restore_link))
            }
        }

        when (kind) {
            AddonKind.MEDIA -> {
                Text(stringResource(R.string.addons_media_hint),
                    style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = { openMediaActivity(context, GifEditorActivity::class.java) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.gif_editor_title)) }
                OutlinedButton(
                    onClick = { openMediaActivity(context, StickerEditorActivity::class.java) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.media_create)) }
                OutlinedButton(
                    onClick = { openMediaActivity(context, MediaExportActivity::class.java) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.media_export)) }
            }
            AddonKind.FLUENT -> {
                Text(stringResource(R.string.addons_fluent_source_notice),
                    style = MaterialTheme.typography.bodySmall)
                Text(
                    if (fluentInstalled) stringResource(R.string.media_hq_ready)
                    else stringResource(R.string.media_hq_optional),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (fluentInstalled) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val removed = try {
                                    FluentHqPackStore.uninstall(context)
                                } catch (_: Exception) {
                                    false
                                }
                                Toast.makeText(
                                    context,
                                    if (removed) R.string.addons_removed
                                    else R.string.addons_remove_failed,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.media_hq_remove)) }
                }
            }
            AddonKind.AI -> {
                Text(stringResource(R.string.addons_ai_source_notice),
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { navController.navigate("models") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.addons_models_title)) }
                OutlinedButton(
                    onClick = {
                        openVerifiedSource(
                            context,
                            "https://github.com/latanvillegas/android-keyboard/tree/" +
                                "feature/fluent3d-optional-hq-pack-lite-apk/offline-ai-runtime"
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.addons_runtime_source)) }
            }
        }

        OutlinedButton(
            onClick = { navController.navigate("actionEdit") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.addons_actions_title))
        }
    }
}
