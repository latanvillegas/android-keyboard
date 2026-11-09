package org.futo.inputmethod.latin.uix.actions.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction

/** Exactly one URI commit per tap, protected against changed input fields. */
internal object MediaTransport {
    private val mutex = Mutex()
    enum class Result { ACCEPTED, UNSUPPORTED, EDITOR_CHANGED, MISSING, REJECTED }

    suspend fun send(
        manager: KeyboardManagerForAction, item: MediaItem, editorToken: Any?
    ): Result = mutex.withLock {
        if (!manager.isEditorSessionCurrent(editorToken))
            return@withLock Result.EDITOR_CHANGED
        val mime = item.kind.mime
        if (!manager.appSupportsImageInsertion(mime, true))
            return@withLock Result.UNSUPPORTED
        val present = withContext(Dispatchers.IO) {
            MediaLibrary.get(manager.getContext(), item.id) != null
        }
        if (!present) return@withLock Result.MISSING
        if (!manager.isEditorSessionCurrent(editorToken))
            return@withLock Result.EDITOR_CHANGED
        val accepted = try {
            manager.typeUri(MediaShareProvider.uri(item.id), listOf(mime), true, false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        if (accepted) Result.ACCEPTED else Result.REJECTED
    }
}
