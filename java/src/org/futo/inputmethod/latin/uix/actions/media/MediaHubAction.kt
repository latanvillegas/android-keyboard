package org.futo.inputmethod.latin.uix.actions.media

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionHeaderSearch
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.actions.PersistentEmojiState
import org.futo.inputmethod.latin.uix.settings.SettingsActivity

/** Full management surface, sharing the same data and gallery as the emoji picker. */
val MediaHubAction = Action(
    icon = R.drawable.smile,
    name = R.string.media_hub_title,
    canShowKeyboard = true,
    simplePressImpl = null,
    windowImpl = { manager, _ ->
        object : ActionWindow() {
            private val searchState = mutableStateOf("")
            private val isSearching = mutableStateOf(false)

            @Composable
            override fun windowName(): String = stringResource(R.string.media_hub_title)

            @Composable
            override fun WindowTitleBar(rowScope: RowScope) {
                if (isSearching.value) {
                    with(rowScope) {
                        ActionHeaderSearch(searchState, Modifier.weight(1f))
                    }
                    TextButton(onClick = {
                        searchState.value = ""
                        isSearching.value = false
                    }) { Text("×") }
                } else {
                    super.WindowTitleBar(rowScope)
                    TextButton(onClick = { isSearching.value = true }) {
                        Text("⌕", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                MediaHubPanel(manager, searchState.value)
            }
        }
    }
)

private enum class HubTab(val label: Int, val shelf: MediaShelf?) {
    ALL(R.string.media_all, MediaShelf.ALL),
    GIF(R.string.media_gifs, MediaShelf.GIF),
    STICKERS(R.string.media_stickers, MediaShelf.STICKERS),
    IMAGES(R.string.media_images, MediaShelf.IMAGES),
    FAVORITES(R.string.media_favorites, MediaShelf.FAVORITES),
    RECENT(R.string.media_tab_recent, MediaShelf.RECENT),
    THREE_D(R.string.media_tab_3d, null)
}

@Composable
private fun MediaHubPanel(manager: KeyboardManagerForAction, search: String) {
    val context = LocalContext.current
    val media = rememberSharedMediaItems()
    var tab by remember { mutableStateOf(HubTab.ALL) }
    var createOpen by remember { mutableStateOf(false) }
    var collection by remember { mutableStateOf("") }
    var collectionMenu by remember { mutableStateOf(false) }
    val collections = remember(media) {
        media.map { it.collection }.filter { it.isNotBlank() }.distinct().sorted()
    }

    fun startActivity(clazz: Class<*>) {
        context.startActivity(
            Intent(context, clazz).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
    LaunchedEffect(tab) {
        if (tab == HubTab.THREE_D && PersistentEmojiState.emojis.value == null) {
            PersistentEmojiState.loadEmojis(context)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box {
                TextButton(onClick = { createOpen = true }) {
                    Text("+ " + stringResource(R.string.media_create_menu))
                }
                DropdownMenu(expanded = createOpen, onDismissRequest = { createOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.media_quick_import)) },
                        onClick = {
                            createOpen = false
                            startActivity(MediaImportActivity::class.java)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.media_quick_create_sticker)) },
                        onClick = {
                            createOpen = false
                            startActivity(StickerEditorActivity::class.java)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.media_quick_edit_gif)) },
                        onClick = {
                            createOpen = false
                            startActivity(GifEditorActivity::class.java)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.media_quick_export)) },
                        onClick = {
                            createOpen = false
                            startActivity(MediaExportActivity::class.java)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.media_quick_models)) },
                        onClick = {
                            createOpen = false
                            SettingsActivity.openToNavDest(context, "models")
                        }
                    )
                }
            }

            if (collections.isNotEmpty()) {
                Box {
                    TextButton(onClick = { collectionMenu = true }) {
                        Text(
                            if (collection.isEmpty()) stringResource(R.string.media_all_collections)
                            else stringResource(R.string.media_collection_label, collection),
                            maxLines = 1
                        )
                    }
                    DropdownMenu(
                        expanded = collectionMenu,
                        onDismissRequest = { collectionMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.media_all_collections)) },
                            onClick = { collection = ""; collectionMenu = false }
                        )
                        collections.forEach { label ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { collection = label; collectionMenu = false }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HubTab.entries.forEach { option ->
                TextButton(onClick = { tab = option; collection = "" }) {
                    Text(
                        stringResource(option.label),
                        color = if (tab == option) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        if (tab == HubTab.THREE_D) {
            val allEmojis = PersistentEmojiState.emojis.value.orEmpty()
            Fluent3DBrowser(
                manager = manager,
                emojis = allEmojis,
                search = search,
                modifier = Modifier.weight(1f),
                compact = false
            )
        } else {
            SharedMediaGallery(
                manager = manager,
                shelf = requireNotNull(tab.shelf),
                search = search,
                collection = collection,
                modifier = Modifier.weight(1f),
                compact = false,
                onCreate = { createOpen = true }
            )
        }
    }
}
