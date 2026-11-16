package org.futo.inputmethod.latin.uix.actions.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.futo.inputmethod.latin.uix.DialogRequestItem
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import java.util.Locale

/**
 * One source of truth for Emoji picker and Media Hub.
 * Gallery reads only local metadata; decoding runs on IO with an 8 MiB cache.
 */
internal enum class MediaShelf {
    ALL, GIF, STICKERS, IMAGES, FAVORITES, RECENT
}

internal object MediaBrowsePolicy {
    fun matches(item: MediaItem, shelf: MediaShelf, query: String, collection: String = ""): Boolean {
        if (collection.isNotEmpty() && item.collection != collection) return false
        val eligible = when (shelf) {
            MediaShelf.ALL -> true
            MediaShelf.GIF -> item.contentType == MediaContentType.GIF
            MediaShelf.STICKERS -> item.contentType == MediaContentType.STICKER
            MediaShelf.IMAGES -> item.contentType == MediaContentType.IMAGE
            MediaShelf.FAVORITES -> item.favorite
            MediaShelf.RECENT -> item.lastUsedAt > 0L
        }
        if (!eligible) return false
        val keyword = query.trim().lowercase(Locale.ROOT)
        if (keyword.isBlank()) return true
        return item.displayName.lowercase(Locale.ROOT).contains(keyword) ||
            item.tags.any { it.lowercase(Locale.ROOT).contains(keyword) } ||
            item.collection.lowercase(Locale.ROOT).contains(keyword) ||
            item.contentType.name.lowercase(Locale.ROOT).contains(keyword)
    }

    fun sorted(items: List<MediaItem>, shelf: MediaShelf): List<MediaItem> =
        when (shelf) {
            MediaShelf.RECENT -> items.sortedByDescending { it.lastUsedAt }
            MediaShelf.FAVORITES -> items.sortedByDescending { it.lastUsedAt.coerceAtLeast(it.createdAt) }
            else -> items.sortedWith(compareByDescending<MediaItem> { it.favorite }
                .thenByDescending { it.lastUsedAt }
                .thenByDescending { it.createdAt })
        }
}

/** Real-time catalog snapshot, shared across both surfaces. */
@Composable
internal fun rememberSharedMediaItems(): List<MediaItem> {
    val context = LocalContext.current
    val entries by produceState(initialValue = emptyList<MediaItem>(), context) {
        value = runCatching { MediaLibrary.list(context) }.getOrDefault(emptyList())
        MediaLibrary.changes.collect {
            value = runCatching { MediaLibrary.list(context) }.getOrDefault(emptyList())
        }
    }
    return entries
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SharedMediaGallery(
    manager: KeyboardManagerForAction,
    shelf: MediaShelf,
    search: String,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    collection: String = "",
    onCreate: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val all = rememberSharedMediaItems()
    val shown = remember(all, shelf, search, collection) {
        MediaBrowsePolicy.sorted(all.filter { MediaBrowsePolicy.matches(it, shelf, search, collection) }, shelf)
    }
    if (shown.isEmpty()) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(
                if (shelf == MediaShelf.RECENT) stringResource(R.string.media_no_recents)
                else if (shelf == MediaShelf.FAVORITES) stringResource(R.string.media_no_favorites)
                else if (search.isNotBlank()) stringResource(R.string.media_no_search_results)
                else stringResource(R.string.media_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
            if (onCreate != null) {
                TextButton(onClick = onCreate) {
                    Text(stringResource(R.string.media_create_menu))
                }
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (compact) 68.dp else 88.dp),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(shown, key = { it.id }) { item ->
            val bitmap by produceState<Bitmap?>(null, item.id) {
                value = withContext(Dispatchers.IO) {
                    runCatching { MediaLibrary.thumbnail(context, item) }.getOrNull()
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(1.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .combinedClickable(
                        onClick = {
                            val token = manager.captureEditorSession()
                            manager.getLifecycleScope().launch {
                                when (MediaTransport.send(manager, item, token)) {
                                    MediaTransport.Result.UNSUPPORTED,
                                    MediaTransport.Result.REJECTED ->
                                        Toast.makeText(context, R.string.media_unsupported,
                                            Toast.LENGTH_SHORT).show()
                                    MediaTransport.Result.MISSING ->
                                        Toast.makeText(context, R.string.media_missing,
                                            Toast.LENGTH_SHORT).show()
                                    MediaTransport.Result.EDITOR_CHANGED ->
                                        Toast.makeText(context, R.string.media_editor_changed,
                                            Toast.LENGTH_SHORT).show()
                                    else -> Unit
                                }
                            }
                        },
                        onLongClick = {
                            val options = mutableListOf(
                                DialogRequestItem(
                                    context.getString(if (item.favorite)
                                        R.string.media_unfavorite else R.string.media_favorite)
                                ) {
                                    manager.getLifecycleScope().launch {
                                        MediaLibrary.setFavorite(context, item.id, !item.favorite)
                                    }
                                },
                                DialogRequestItem(context.getString(R.string.media_edit_metadata)) {
                                    context.startActivity(
                                        Intent(context, MediaMetadataActivity::class.java)
                                            .putExtra(MediaMetadataActivity.EXTRA_ID, item.id)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            )
                            if (item.kind == MediaKind.GIF) {
                                options += DialogRequestItem(
                                    context.getString(R.string.gif_editor_edit)
                                ) {
                                    context.startActivity(
                                        Intent(context, GifEditorActivity::class.java)
                                            .putExtra("media_id", item.id)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                            options += DialogRequestItem(
                                context.getString(R.string.media_delete)
                            ) {
                                manager.getLifecycleScope().launch {
                                    MediaLibrary.delete(context, item.id)
                                }
                            }
                            options += DialogRequestItem(
                                context.getString(R.string.ai_variant_cancel)
                            ) { }
                            manager.requestDialog(
                                context.getString(R.string.media_item_actions, item.displayName),
                                options,
                                {}
                            )
                        }
                    ),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Box(Modifier.size(if (compact) 69.dp else 88.dp),
                    contentAlignment = Alignment.Center) {
                    if (bitmap != null) {
                        Image(
                            bitmap = requireNotNull(bitmap).asImageBitmap(),
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(3.dp)
                        )
                    } else {
                        Text(item.kind.name, style = MaterialTheme.typography.labelSmall)
                    }
                    if (item.favorite) {
                        Text(
                            "★",
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (item.contentType == MediaContentType.GIF) {
                        Text("GIF",
                            modifier = Modifier.align(Alignment.BottomStart).padding(2.dp),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
