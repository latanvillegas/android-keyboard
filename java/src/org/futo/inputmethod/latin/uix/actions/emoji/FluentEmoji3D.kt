package org.futo.inputmethod.latin.uix.actions.emoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Offline lookup of original, prerendered Microsoft Fluent Emoji 3D artwork.
 *
 * Source: microsoft/fluentui-emoji (MIT), organized by Unicode in
 * shuding/fluentui-emoji-unicode. Images and the attribution notice are
 * packaged into the Release APK at build time. There are no network calls.
 *
 * Missing assets fall back to the system Unicode renderer; skin tones are
 * NEVER silently changed to the default tone.
 */
object FluentEmoji3D {
    private const val ROOT = "emoji3d"
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private val workers = Executors.newFixedThreadPool(2) { task ->
        Thread(task, "fluent-emoji3d").apply { isDaemon = true }
    }
    private val cache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount + 1023) / 1024
    }
    private val pending = HashMap<String, MutableList<(Bitmap?) -> Unit>>()
    private val failed = HashSet<String>()
    private var available: Set<String>? = null

    private fun availableKeys(context: Context): Set<String> = synchronized(lock) {
        available?.let { return@synchronized it }
        val loaded = try {
            context.assets.open("$ROOT/index.txt").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.toHashSet()
            }
        } catch (_: Exception) {
            emptySet()
        }
        available = loaded
        loaded
    }

    private fun emojiHex(value: String, stripVariationSelectors: Boolean): String {
        val codepoints = ArrayList<String>(8)
        var offset = 0
        while (offset < value.length) {
            val cp = Character.codePointAt(value, offset)
            offset += Character.charCount(cp)
            if (stripVariationSelectors && (cp == 0xfe0e || cp == 0xfe0f)) continue
            codepoints.add(cp.toString(16).padStart(4, '0'))
        }
        return codepoints.joinToString("-")
    }

    /** ASCII and kaomoji use semantically matching *3D artwork*, not fake text shadows. */
    private fun mappedEmoticon(description: String?, value: String): String? {
        val desc = description.orEmpty().lowercase(Locale.ROOT)
        return when {
            "cat" in desc || "meow" in desc -> "1f63a"
            "heart" in desc && "face" !in desc -> "2764"
            "love" in desc || "flower" in desc || "hug" in desc -> "1f970"
            "cry" in desc || "teary" in desc -> "1f62d"
            "angry" in desc || "grumpy" in desc || "frustrat" in desc ||
                "annoy" in desc || "evil" in desc || "tableflip" in desc -> "1f620"
            "laugh" in desc || "excited" in desc || "very happy" in desc -> "1f606"
            "wink" in desc -> "1f609"
            "tongue" in desc -> "1f61b"
            "surpris" in desc || "shock" in desc || "wide eye" in desc -> "1f62e"
            "skeptic" in desc || "confus" in desc || "thinking" in desc -> "1f928"
            "sleep" in desc -> "1f634"
            "cool" in desc || "sunglasses" in desc -> "1f60e"
            "disappoint" in desc || "sad" in desc -> "1f61e"
            "flex" in desc -> "1f4aa"
            "sweat" in desc || "nervous" in desc -> "1f613"
            "dizzy" in desc -> "1f635"
            "shy" in desc || "blush" in desc -> "1f60a"
            "happy" in desc || "smile" in desc || "cute" in desc ||
                "cheer" in desc || "joy" in desc || "content" in desc -> "1f642"
            value == "<3" -> "2764"
            value == ":)" || value == ":-)" -> "1f642"
            value == ":(" || value == ":-(" -> "1f641"
            value == ":D" || value == "XD" -> "1f606"
            value == ";)" -> "1f609"
            value == ":P" -> "1f61b"
            else -> null
        }
    }

    private fun resolve(context: Context, value: String, category: String?, description: String?): String? {
        val known = availableKeys(context)
        if (known.isEmpty() || value.isBlank()) return null
        if (category == "ASCII" || (value.length <= 3 && value.all { it.code < 128 })) {
            val mapped = mappedEmoticon(description, value.trim())
            return mapped?.takeIf { known.contains(it) }
        }
        val normalized = emojiHex(value, stripVariationSelectors = true)
        if (known.contains(normalized)) return normalized
        val original = emojiHex(value, stripVariationSelectors = false)
        return original.takeIf { known.contains(it) }
    }

    private fun decode(context: Context, key: String): Bitmap? = try {
        context.assets.open("$ROOT/$key.webp").use { input ->
            BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            })
        }
    } catch (_: Exception) {
        null
    }

    /** Resolves the exact bundled artwork also used by the on-screen selector. */
    fun assetKey(
        context: Context,
        value: String,
        category: String? = null,
        description: String? = null
    ): String? = resolve(context.applicationContext, value, category, description)

    /** Intended for Dispatchers.IO; never call this on the UI thread. */
    fun loadBitmap(
        context: Context,
        value: String,
        category: String? = null,
        description: String? = null
    ): Bitmap? {
        val key = resolve(context, value, category, description) ?: return null
        synchronized(lock) {
            cache.get(key)?.let { return it }
            if (failed.contains(key)) return null
        }
        val decoded = decode(context.applicationContext, key)
        synchronized(lock) {
            if (decoded != null) cache.put(key, decoded) else failed.add(key)
        }
        return decoded
    }

    /** Coalesces concurrent scrolling requests and delivers callbacks on the main thread. */
    fun request(
        context: Context,
        value: String,
        category: String? = null,
        description: String? = null,
        onReady: (Bitmap?) -> Unit
    ) {
        val application = context.applicationContext
        val key = resolve(application, value, category, description)
        if (key == null) {
            onReady(null)
            return
        }
        synchronized(lock) {
            cache.get(key)?.let {
                onReady(it)
                return
            }
            if (failed.contains(key)) {
                onReady(null)
                return
            }
            val listeners = pending[key]
            if (listeners != null) {
                listeners.add(onReady)
                return
            }
            pending[key] = mutableListOf(onReady)
        }
        workers.execute {
            val bitmap = decode(application, key)
            val listeners = synchronized(lock) {
                if (bitmap != null) cache.put(key, bitmap) else failed.add(key)
                pending.remove(key).orEmpty()
            }
            main.post {
                listeners.forEach { listener -> listener(bitmap) }
            }
        }
    }
}

/** Shared raster rendering for skin-tone popups and emoji-only predictions. */
@Composable
fun FluentEmoji3DImage(
    emoji: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    category: String? = null,
    description: String? = null,
    fallback: @Composable () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val bitmap = produceState<Bitmap?>(null, emoji, enabled, category, description) {
        value = if (enabled) withContext(Dispatchers.IO) {
            FluentEmoji3D.loadBitmap(context, emoji, category, description)
        } else {
            null
        }
    }.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = emoji,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        fallback()
    }
}
