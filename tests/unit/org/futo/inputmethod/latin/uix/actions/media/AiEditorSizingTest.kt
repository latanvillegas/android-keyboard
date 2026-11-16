package org.futo.inputmethod.latin.uix.actions.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEditorSizingTest {
    @Test fun automaticGrowsAndShrinksWithActualVisualLines() {
        val empty = AiEditorSizing.resolve(0, 1, 120, 52, 220, 900)
        val paragraph = AiEditorSizing.resolve(0, 5, 120, 52, 220, 900)
        val longText = AiEditorSizing.resolve(0, 40, 120, 52, 220, 900)
        assertEquals(52, empty)
        assertTrue(paragraph > empty)
        assertEquals(220, longText)
        assertEquals(empty, AiEditorSizing.resolve(0, 1, 120, 52, 220, 900))
    }

    @Test fun manualRemainsFixed() {
        assertEquals(110, AiEditorSizing.resolve(1, 1, 110, 52, 220, 900))
        assertEquals(110, AiEditorSizing.resolve(1, 80, 110, 52, 220, 900))
    }

    @Test fun hybridRespectsConfiguredMinAndMax() {
        assertEquals(75, AiEditorSizing.resolve(2, 1, 120, 75, 140, 900))
        assertEquals(140, AiEditorSizing.resolve(2, 40, 120, 75, 140, 900))
        assertEquals(96, AiEditorSizing.resolve(2, 3, 120, 75, 140, 900))
    }

    @Test fun noModeCanCoverSmallScreen() {
        for (mode in 0..2) {
            val result = AiEditorSizing.resolve(mode, 100, 500, 200, 340, 300)
            assertTrue(result <= 96)
        }
    }
}
