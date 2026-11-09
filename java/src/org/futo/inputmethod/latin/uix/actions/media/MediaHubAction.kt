package org.futo.inputmethod.latin.uix.actions.media

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.settings.SettingsActivity

/** User-customizable action, added without rewriting the existing emoji panel. */
val MediaHubAction = Action(
    icon = R.drawable.smile,
    name = R.string.media_hub_title,
    canShowKeyboard = true,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        object : ActionWindow() {
            @Composable
            override fun windowName(): String = stringResource(R.string.media_hub_title)

            @Composable
            override fun WindowTitleBar(rowScope: RowScope) {
                super.WindowTitleBar(rowScope)
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                MediaHubPanel(manager)
            }
        }
    }
)

private enum class MediaFilter { ALL, GIF, STICKERS, FAVORITES }

@Composable
private fun MediaHubPanel(manager: KeyboardManagerForAction) {
    val context = LocalContext.current
    val items by produceState(initialValue = emptyList<MediaItem>()) {
        value = MediaLibrary.list(context)
        MediaLibrary.changes.collect {
            value = MediaLibrary.list(context)
        }
    }
    var filter by remember { mutableStateOf(MediaFilter.ALL) }
    val hqInstalled by produceState(
        initialValue = FluentHqPackStore.isInstalled(context)
    ) {
        MediaLibrary.changes.collect {
            value = FluentHqPackStore.isInstalled(context)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = {
                context.startActivity(
                    Intent(context, MediaImportActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Text(stringResource(R.string.media_import))
            }
            Button(onClick = {
                context.startActivity(
                    Intent(context, StickerEditorActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Text(stringResource(R.string.media_create))
            }
            Button(onClick = {
                context.startActivity(
                    Intent(context, MediaExportActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Text(stringResource(R.string.media_export))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = {
                context.startActivity(
                    Intent(context, GifEditorActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) {
                Text(stringResource(R.string.gif_editor_title))
            }
            TextButton(onClick = { manager.activateAction(OfflineAiAction) }) {
                Text(stringResource(R.string.local_ai_title))
            }
        }
        TextButton(onClick = {
            SettingsActivity.openToNavDest(context, "models")
        }) {
            Text(stringResource(R.string.media_offline_models))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            val filters = listOf(
                MediaFilter.ALL to R.string.media_all,
                MediaFilter.GIF to R.string.media_gifs,
                MediaFilter.STICKERS to R.string.media_stickers,
                MediaFilter.FAVORITES to R.string.media_favorites
            )
            for ((option, label) in filters) {
                TextButton(onClick = { filter = option }) {
                    Text(
                        stringResource(label),
                        maxLines = 1
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (hqInstalled) stringResource(R.string.media_hq_ready)
                else stringResource(R.string.media_hq_optional))
            if (hqInstalled) {
                TextButton(onClick = {
                    manager.getLifecycleScope().launch {
                        FluentHqPackStore.uninstall(context)
                    }
                }) { Text(stringResource(R.string.media_hq_remove)) }
            }
        }

        val shown = items.filter {
            when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.GIF -> it.kind == MediaKind.GIF
                MediaFilter.STICKERS -> it.kind != MediaKind.GIF
                MediaFilter.FAVORITES -> it.favorite
            }
        }
        if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.media_empty))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(106.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(shown, key = { it.id }) { item ->
                    MediaTile(
                        item = item,
                        onSend = {
                            val token = manager.captureEditorSession()
                            manager.getLifecycleScope().launch {
                                val result = MediaTransport.send(manager, item, token)
                                if (result == MediaTransport.Result.UNSUPPORTED ||
                                    result == MediaTransport.Result.REJECTED) {
                                    Toast.makeText(context, R.string.media_unsupported, Toast.LENGTH_SHORT).show()
                                } else if (result == MediaTransport.Result.MISSING) {
                                    Toast.makeText(context, R.string.media_missing, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onFavorite = {
                            manager.getLifecycleScope().launch {
                                MediaLibrary.setFavorite(context, item.id, !item.favorite)
                            }
                        },
                        onDelete = {
                            manager.getLifecycleScope().launch {
                                MediaLibrary.delete(context, item.id)
                            }
                        },
                        onEdit = {
                            context.startActivity(
                                Intent(context, GifEditorActivity::class.java)
                                    .putExtra("media_id", item.id)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaTile(
    item: MediaItem,
    onSend: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, item.id) {
        value = withContext(Dispatchers.IO) {
            MediaLibrary.thumbnail(context, item)
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
            if (bitmap != null) {
                Image(
                    bitmap = requireNotNull(bitmap).asImageBitmap(),
                    contentDescription = item.displayName,
                    modifier = Modifier.size(80.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                    Text(item.kind.name)
                }
            }
        }
        Text(item.displayName, maxLines = 1)
        if (item.kind == MediaKind.GIF) {
            TextButton(onClick = onEdit) {
                Text(stringResource(R.string.gif_editor_edit))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onFavorite) {
                Text(if (item.favorite) "★" else "☆")
            }
            IconButton(onClick = onDelete) {
                Text("×")
            }
        }
    }
}
