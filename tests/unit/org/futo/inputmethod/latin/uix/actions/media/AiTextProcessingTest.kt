package org.futo.inputmethod.latin.uix.actions.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTextProcessingTest {
    @Test fun singleShortRequestRemainsIntact() {
        assertEquals(listOf("Hola, mundo"), AiTextProcessing.split(" Hola, mundo ", 100))
    }

    @Test fun longTextSplitsWithoutDroppingWords() {
        val text = (1..90).joinToString(" ") { "palabra" }
        val parts = AiTextProcessing.split(text, 60)
        assertTrue(parts.size > 3)
        assertTrue(parts.all { it.length <= 60 })
        assertEquals(text, parts.joinToString(" "))
    }

    @Test fun wordsLongerThanWindowStillTerminate() {
        val input = "x".repeat(220)
        val parts = AiTextProcessing.split(input, 50)
        assertTrue(parts.isNotEmpty())
        assertEquals(input, parts.joinToString(""))
    }

    @Test fun emptyTextProducesNoChunks() {
        assertTrue(AiTextProcessing.split(" \n  ", 50).isEmpty())
    }
}
