package org.futo.inputmethod.latin.uix.actions.emoji

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.util.Collections

/** JVM regression tests for transport fallbacks that do not need a real Android editor. */
class SmartEmojiTransportTest {
    private val editorToken = Any()

    private class FakeEditor {
        var current = true
        var pngSupported = false
        val writes = Collections.synchronizedList(mutableListOf<String>())

        fun manager(): KeyboardManagerForAction {
            val handler = InvocationHandler { _, method, _ ->
                when (method.name) {
                    "isEditorSessionCurrent" -> current
                    "appSupportsImageInsertion" -> pngSupported
                    "typeText" -> { writes.add("unicode"); null }
                    "typeTextSurroundedByWhitespace" -> { writes.add("ascii"); null }
                    "typeUri" -> error("No image is expected in this test")
                    "toString" -> "FakeKeyboardManager"
                    else -> error("Unexpected manager call: " + method.name)
                }
            }
            return Proxy.newProxyInstance(
                KeyboardManagerForAction::class.java.classLoader,
                arrayOf(KeyboardManagerForAction::class.java),
                handler
            ) as KeyboardManagerForAction
        }
    }

    private fun item(category: String = "Smileys & Emotion") = EmojiItem(
        emoji = "😀",
        description = "grinning face",
        category = category,
        skinTones = false
    )

    @Test
    fun realAnimationIsPreferredBeforeStaticImage() {
        val both = setOf("image/gif", "image/png")
        assertEquals(
            listOf("image/gif", "image/png"),
            SmartEmojiTransport.preferredMimeTypes(true) { it in both }
        )
    }

    @Test
    fun pngOnlyEditorNeverReceivesAnimatedMimeType() {
        assertEquals(
            listOf("image/png"),
            SmartEmojiTransport.preferredMimeTypes(true) { it == "image/png" }
        )
    }

    @Test
    fun disabledThreeDDoesNotQueryMultimediaCapabilities() {
        var queries = 0
        assertTrue(SmartEmojiTransport.preferredMimeTypes(false) {
            queries++
            true
        }.isEmpty())
        assertEquals(0, queries)
    }

    @Test
    fun disabledThreeDAlwaysInsertsUnicode() = runBlocking {
        val editor = FakeEditor().apply { pngSupported = true }
        val result = SmartEmojiTransport.send(editor.manager(), item(), false, editorToken)
        assertEquals(SmartEmojiTransport.Result.UNICODE_INSERTED, result)
        assertEquals(listOf("unicode"), editor.writes)
    }

    @Test
    fun unsupportedEditorFallsBackToUnicodeOnce() = runBlocking {
        val editor = FakeEditor()
        val result = SmartEmojiTransport.send(editor.manager(), item(), true, editorToken)
        assertEquals(SmartEmojiTransport.Result.UNICODE_INSERTED, result)
        assertEquals(listOf("unicode"), editor.writes)
    }

    @Test
    fun obsoleteEditorSessionNeverReceivesContent() = runBlocking {
        val editor = FakeEditor().apply { current = false }
        val result = SmartEmojiTransport.send(editor.manager(), item(), true, editorToken)
        assertEquals(SmartEmojiTransport.Result.EDITOR_CHANGED, result)
        assertTrue(editor.writes.isEmpty())
    }

    @Test
    fun asciiEmoticonsRetainExistingWhitespacePath() = runBlocking {
        val editor = FakeEditor()
        val result = SmartEmojiTransport.send(editor.manager(), item("ASCII"), true, editorToken)
        assertEquals(SmartEmojiTransport.Result.UNICODE_INSERTED, result)
        assertEquals(listOf("ascii"), editor.writes)
    }

    @Test
    fun concurrentFallbacksNeverDuplicateOutput() = runBlocking {
        val editor = FakeEditor()
        val manager = editor.manager()
        val results = (1..24).map {
            async(Dispatchers.Default) {
                SmartEmojiTransport.send(manager, item(), true, editorToken)
            }
        }.awaitAll()

        assertEquals(24, results.size)
        assertTrue(results.all { it == SmartEmojiTransport.Result.UNICODE_INSERTED })
        assertEquals(24, editor.writes.size)
        assertTrue(editor.writes.all { it == "unicode" })
    }
}
