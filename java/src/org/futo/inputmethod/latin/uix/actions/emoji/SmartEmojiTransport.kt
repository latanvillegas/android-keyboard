package org.futo.inputmethod.latin.uix.actions.emoji

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction

/**
 * Sends real animated Fluent artwork where supported, followed by static
 * 3D and exact Unicode fallbacks. Each key tap is committed at most once.
 *
 * Returning IMAGE_ACCEPTED / ANIMATION_ACCEPTED means the editor accepted
 * the content request; the receiving chat may still require user confirmation.
 */
internal object SmartEmojiTransport {
    private const val TAG = "SmartEmojiTransport"
    private val sendMutex = Mutex()

    enum class Result {
        ANIMATION_ACCEPTED,
        IMAGE_ACCEPTED,
        UNICODE_INSERTED,
        EDITOR_CHANGED
    }

    /** MIME negotiation is deterministic and can be regression-tested without Android. */
    internal fun preferredMimeTypes(
        useThreeD: Boolean,
        accepts: (String) -> Boolean
    ): List<String> {
        if (!useThreeD) return emptyList()
        return listOf(
            Emoji3DShareStore.ANIMATED_MIME_TYPE,
            Emoji3DShareStore.MIME_TYPE
        ).filter(accepts)
    }

    suspend fun send(
        manager: KeyboardManagerForAction,
        item: EmojiItem,
        useThreeD: Boolean,
        editorToken: Any?
    ): Result = sendMutex.withLock {
        if (!manager.isEditorSessionCurrent(editorToken)) {
            return@withLock Result.EDITOR_CHANGED
        }

        if (useThreeD) {
            // Never send animation to a receiver that only advertises static PNG.
            for (mime in preferredMimeTypes(useThreeD) {
                manager.appSupportsImageInsertion(it, true)
            }) {
                val animated = mime == Emoji3DShareStore.ANIMATED_MIME_TYPE

                val uri = try {
                    withContext(Dispatchers.IO) {
                        if (animated) {
                            Emoji3DShareStore.prepareAnimation(
                                manager.getContext(), item.emoji, item.category, item.description
                            )
                        } else {
                            Emoji3DShareStore.prepare(
                                manager.getContext(), item.emoji, item.category, item.description
                            )
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Unable to prepare $mime; checking fallback", error)
                    null
                }

                // Never deliver an old tap to a newly focused editor.
                if (!manager.isEditorSessionCurrent(editorToken)) {
                    return@withLock Result.EDITOR_CHANGED
                }

                if (uri != null && manager.appSupportsImageInsertion(mime, true)) {
                    val accepted = try {
                        manager.typeUri(uri, listOf(mime), true, false)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Log.w(TAG, "Editor rejected $mime; checking fallback", error)
                        false
                    }
                    if (accepted) {
                        return@withLock if (animated) {
                            Result.ANIMATION_ACCEPTED
                        } else {
                            Result.IMAGE_ACCEPTED
                        }
                    }
                }
            }
        }

        if (!manager.isEditorSessionCurrent(editorToken)) {
            return@withLock Result.EDITOR_CHANGED
        }
        if (item.category == "ASCII") {
            manager.typeTextSurroundedByWhitespace(item.emoji)
        } else {
            manager.typeText(item.emoji)
        }
        Result.UNICODE_INSERTED
    }
}
