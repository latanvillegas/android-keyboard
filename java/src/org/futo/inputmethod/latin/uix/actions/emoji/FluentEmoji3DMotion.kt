package org.futo.inputmethod.latin.uix.actions.emoji

import android.content.Context
import org.futo.inputmethod.latin.uix.actions.media.FluentHqPackStore
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Offline index for original Microsoft Fluent animations, not synthetic motion.
 *
 * Every installed Unicode key has two original variants: lossless Animated
 * WebP and GIF. Both are loaded from the verified optional offline pack.
 */
internal object FluentEmoji3DMotion {
    private const val ROOT = "emoji3d-motion"
    private val guard = Any()
    private val validKey = Regex("[0-9a-f]+(?:-[0-9a-f]+)*")
    private var cachedKeys: Set<String>? = null

    private fun available(context: Context): Set<String> = synchronized(guard) {
        cachedKeys?.let { return@synchronized it }
        val keys = FluentHqPackStore.keys(context, ROOT) ?: try {
            context.applicationContext.assets.open("$ROOT/index.txt")
                .bufferedReader().useLines { lines ->
                    lines.filter { validKey.matches(it) }.toHashSet()
                }
        } catch (_: Exception) {
            emptySet()
        }
        cachedKeys = keys
        keys
    }

    internal fun invalidateResources() = synchronized(guard) {
        cachedKeys = null
    }

    fun hasAnimation(context: Context, assetKey: String): Boolean =
        available(context).contains(assetKey)

    /** Called from Dispatchers.IO. The caller owns and closes the input stream. */
    fun openWebp(context: Context, assetKey: String): InputStream? {
        if (!hasAnimation(context, assetKey)) return null
        FluentHqPackStore.openMotion(context, assetKey)?.let { return it }
        return try {
            context.applicationContext.assets.open("$ROOT/$assetKey.webp")
        } catch (_: FileNotFoundException) {
            null
        }
    }

    /** Compatibility option for receivers which do not accept animated WebP. */
    fun openGif(context: Context, assetKey: String): InputStream? {
        if (!hasAnimation(context, assetKey)) return null
        FluentHqPackStore.openGif(context, assetKey)?.let { return it }
        return try {
            context.applicationContext.assets.open("$ROOT/$assetKey.gif")
        } catch (_: FileNotFoundException) {
            null
        }
    }
}
