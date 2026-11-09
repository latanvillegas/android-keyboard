package org.futo.inputmethod.latin.uix.actions.emoji

import android.content.Context
import java.io.InputStream

/**
 * Local index for genuine frame-by-frame Microsoft Fluent Emoji animations.
 *
 * Only explicitly packaged Unicode keys are available. No synthesized 2D
 * movement, network access, skin-tone substitution or runtime conversion.
 */
internal object FluentEmoji3DMotion {
    private const val ROOT = "emoji3d-motion"
    private val guard = Any()
    private var cachedKeys: Set<String>? = null

    private fun available(context: Context): Set<String> = synchronized(guard) {
        cachedKeys?.let { return@synchronized it }
        val keys = try {
            context.applicationContext.assets.open("$ROOT/index.txt")
                .bufferedReader().useLines { sequence ->
                    sequence.filter { it.matches(Regex("[0-9a-f]+(?:-[0-9a-f]+)*")) }
                        .toHashSet()
                }
        } catch (_: Exception) {
            emptySet()
        }
        cachedKeys = keys
        keys
    }

    fun hasAnimation(context: Context, assetKey: String): Boolean =
        available(context).contains(assetKey)

    /** Called only from an I/O dispatcher; the caller owns and closes the stream. */
    fun openGif(context: Context, assetKey: String): InputStream? {
        if (!hasAnimation(context, assetKey)) return null
        return context.applicationContext.assets.open("$ROOT/$assetKey.gif")
    }
}
