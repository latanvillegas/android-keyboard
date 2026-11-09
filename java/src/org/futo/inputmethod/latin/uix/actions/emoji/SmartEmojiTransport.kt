package org.futo.inputmethod.latin.uix.actions.emoji

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction

/**
 * Single ordered output path for Unicode, ASCII emoticons and actual Fluent 3D images.
 *
 * A successful commitContent means that the editor accepted the content request, not
 * that the hosting chat application has transmitted or displayed it.
 */
internal object SmartEmojiTransport {
    private const val TAG = "SmartEmojiTransport"
    private val sendMutex = Mutex()

    enum class Result { IMAGE_ACCEPTED, UNICODE_INSERTED, EDITOR_CHANGED }

    suspend fun send(
        manager: KeyboardManagerForAction,
        item: EmojiItem,
        useThreeD: Boolean,
        editorToken: Any?
    ): Result = sendMutex.withLock {
        if (!manager.isEditorSessionCurrent(editorToken)) {
            return@withLock Result.EDITOR_CHANGED
        }

        if (useThreeD && manager.appSupportsImageInsertion(Emoji3DShareStore.MIME_TYPE, true)) {
            val uri = try {
                withContext(Dispatchers.IO) {
                    Emoji3DShareStore.prepare(
                        manager.getContext(), item.emoji, item.category, item.description
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Unable to prepare 3D emoji; using Unicode", error)
                null
            }

            // The user might have switched conversations while the PNG was prepared.
            if (!manager.isEditorSessionCurrent(editorToken)) {
                return@withLock Result.EDITOR_CHANGED
            }

            if (uri != null && manager.appSupportsImageInsertion(Emoji3DShareStore.MIME_TYPE, true)) {
                val accepted = try {
                    manager.typeUri(uri, listOf(Emoji3DShareStore.MIME_TYPE), true, false)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Editor rejected 3D emoji content; using Unicode", error)
                    false
                }
                if (accepted) return@withLock Result.IMAGE_ACCEPTED
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
