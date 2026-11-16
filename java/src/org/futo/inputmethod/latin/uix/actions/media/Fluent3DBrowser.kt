package org.futo.inputmethod.latin.uix.actions.media

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.EmojiTracker.useEmoji
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.actions.emoji.EmojiItem
import org.futo.inputmethod.latin.uix.actions.emoji.FluentEmoji3D
import org.futo.inputmethod.latin.uix.actions.emoji.FluentEmoji3DImage
import org.futo.inputmethod.latin.uix.actions.emoji.SmartEmojiTransport
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/** One-shot navigation between the media manager and the existing emoji action. */
internal enum class EmojiMediaPage { EMOJIS, GIF, STICKERS, THREE_D, FAVORITES }
internal object EmojiMediaNavigation {
    private val pending = AtomicReference<EmojiMediaPage?>(null)
    fun open(page: EmojiMediaPage) { pending.set(page) }
    fun take(): EmojiMediaPage = pending.getAndSet(null) ?: EmojiMediaPage.EMOJIS
}

/** Favorites for optional Fluent pack: only Unicode identifiers are persisted. */
internal object FluentFavorites {
    private const val PREFS = "fluent_3d_favorites"
    private const val KEY = "emojis"
    fun list(context: Context): Set<String> = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun toggle(context: Context, emoji: String): Boolean {
        if (emoji.length > 32 || emoji.isBlank()) return false
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val items = list(context).toMutableSet()
        if (!items.add(emoji)) items.remove(emoji)
        prefs.edit().putStringSet(KEY, items).apply()
        return items.contains(emoji)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Fluent3DBrowser(
    manager: KeyboardManagerForAction,
    emojis: List<EmojiItem>,
    search: String,
    onlyFavorites: Boolean = false,
    compact: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installed by produceState(FluentHqPackStore.isInstalled(context)) {
        MediaLibrary.changes.collect { value = FluentHqPackStore.isInstalled(context) }
    }
    var favoritesRevision by remember { mutableIntStateOf(0) }
    val favoriteSet = remember(context, favoritesRevision) { FluentFavorites.list(context) }
    val catalog by produceState(
        initialValue = emptyList<EmojiItem>(),
        emojis, installed,
        org.futo.inputmethod.latin.uix.actions.emoji.FluentEmoji3D.resourceRevision.intValue
    ) {
        value = withContext(Dispatchers.IO) {
            if (!installed) emptyList()
            else emojis.filter { item ->
                FluentEmoji3D.assetKey(context, item.emoji, item.category, item.description) != null
            }.distinctBy { it.emoji }
        }
    }
    val shown = remember(catalog, favoriteSet, onlyFavorites, search) {
        val q = search.trim().lowercase(Locale.ROOT)
        catalog.filter { item ->
            (!onlyFavorites || item.emoji in favoriteSet) &&
                (q.isBlank() || item.description.lowercase(Locale.ROOT).contains(q) ||
                    item.emoji.contains(q))
        }.let { originals ->
            if (!onlyFavorites) originals
            else originals.sortedBy { it.description }
        }
    }

    if (!installed) {
        Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.media_fluent_external),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp))
            Button(onClick = {
                context.startActivity(Intent(context, MediaImportActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text(stringResource(R.string.media_import_3d)) }
        }
        return
    }

    if (shown.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (onlyFavorites) stringResource(R.string.media_fluent_no_favorites)
                else stringResource(R.string.media_no_search_results))
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (compact) 62.dp else 76.dp),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(shown, key = { it.emoji }) { item ->
            Surface(
                modifier = Modifier.padding(2.dp).combinedClickable(
                    onClick = {
                        val token = manager.captureEditorSession()
                        manager.getLifecycleScope().launch {
                            val result = SmartEmojiTransport.send(manager, item, true, token)
                            if (result != SmartEmojiTransport.Result.EDITOR_CHANGED) {
                                context.useEmoji(item.emoji)
                            }
                        }
                    },
                    onLongClick = {
                        FluentFavorites.toggle(context, item.emoji)
                        favoritesRevision++
                        Toast.makeText(context,
                            if (item.emoji in FluentFavorites.list(context))
                                R.string.media_favorite_saved else R.string.media_favorite_removed,
                            Toast.LENGTH_SHORT).show()
                    }
                ),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(9.dp)
            ) {
                Box(Modifier.size(if (compact) 61.dp else 75.dp),
                    contentAlignment = Alignment.Center) {
                    FluentEmoji3DImage(
                        emoji = item.emoji,
                        enabled = true,
                        category = item.category,
                        description = item.description,
                        modifier = Modifier.fillMaxSize().padding(3.dp),
                        fallback = { Text(item.emoji) }
                    )
                    if (item.emoji in favoriteSet) {
                        Text("★", modifier = Modifier.align(Alignment.TopEnd),
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FluentFavoritesStrip(
    manager: KeyboardManagerForAction,
    emojis: List<EmojiItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installed by produceState(FluentHqPackStore.isInstalled(context)) {
        MediaLibrary.changes.collect { value = FluentHqPackStore.isInstalled(context) }
    }
    val saved = remember(installed, emojis) {
        if (installed) FluentFavorites.list(context) else emptySet()
    }
    if (!installed || saved.isEmpty()) return
    val matching = remember(saved, emojis) {
        emojis.filter { it.emoji in saved }.take(32)
    }
    if (matching.isEmpty()) return
    Column(modifier) {
        Text(stringResource(R.string.media_fluent_favorites),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(start = 8.dp, top = 2.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth().height(65.dp)) {
            items(matching, key = { it.emoji }) { item ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    onClick = {
                        val token = manager.captureEditorSession()
                        manager.getLifecycleScope().launch {
                            SmartEmojiTransport.send(manager, item, true, token)
                        }
                    },
                    modifier = Modifier.size(60.dp)
                ) {
                    FluentEmoji3DImage(
                        emoji = item.emoji,
                        enabled = true,
                        category = item.category,
                        description = item.description,
                        modifier = Modifier.padding(4.dp),
                        fallback = { Text(item.emoji) }
                    )
                }
            }
        }
    }
}
