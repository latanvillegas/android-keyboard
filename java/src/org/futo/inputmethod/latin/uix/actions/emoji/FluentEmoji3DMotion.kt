package org.futo.inputmethod.latin.uix.actions.emoji

import android.content.Context
import java.io.InputStream

/**
 * Offline index for original Microsoft Fluent animations, not synthetic motion.
 *
 * Every bundled Unicode key has two real animated variants: lossless RGBA WebP
 * and a compatibility GIF. Source media is generated at build time only.
 */
internal object FluentEmoji3DMotion {
    private const val ROOT = "emoji3d-motion"
    private val guard = Any()
    private val validKey = Regex("[0-9a-f]+(?:-[0-9a-f]+)*")
    private var cachedKeys: Set<String>? = null

    private fun available(context: Context): Set<String> = synchronized(guard) {
        cachedKeys?.let { return@synchronized it }
        val keys = try {
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

    fun hasAnimation(context: Context, assetKey: String): Boolean =
        available(context).contains(assetKey)

    /** Called from Dispatchers.IO. The caller owns and closes the input stream. */
    fun openWebp(context: Context, assetKey: String): InputStream? {
        if (!hasAnimation(context, assetKey)) return null
        return context.applicationContext.assets.open("$ROOT/$assetKey.webp")
    }

    /** Compatibility option for receivers which do not accept animated WebP. */
    fun openGif(context: Context, assetKey: String): InputStream? {
        if (!hasAnimation(context, assetKey)) return null
        return context.applicationContext.assets.open("$ROOT/$assetKey.gif")
    }
}
