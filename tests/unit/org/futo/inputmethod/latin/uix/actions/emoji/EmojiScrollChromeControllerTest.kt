package org.futo.inputmethod.latin.uix.actions.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiScrollChromeControllerTest {
    @Test
    fun hidesAfterAccumulatedUpwardScrollAndRestoresOnDownwardScroll() {
        val controller = EmojiScrollChromeController(collapseAfterPx = 32, expandAfterPx = 20)
        assertTrue(controller.expanded)
        assertNull(controller.onScrolled(12, userScrolling = true))
        assertNull(controller.onScrolled(19, userScrolling = true))
        assertEquals(false, controller.onScrolled(1, userScrolling = true))
        assertFalse(controller.expanded)
        assertNull(controller.onScrolled(-10, userScrolling = true))
        assertEquals(true, controller.onScrolled(-10, userScrolling = true))
        assertTrue(controller.expanded)
    }

    @Test
    fun directionChangesResetTheAccumulation() {
        val controller = EmojiScrollChromeController(32, 20)
        assertNull(controller.onScrolled(25, true))
        assertNull(controller.onScrolled(-5, true))
        assertNull(controller.onScrolled(25, true))
        assertTrue(controller.expanded)
        assertEquals(false, controller.onScrolled(7, true))
    }

    @Test
    fun programmaticJumpsNeverChangeChromeVisibility() {
        val controller = EmojiScrollChromeController(32, 20)
        assertNull(controller.onScrolled(1000, userScrolling = false))
        assertTrue(controller.expanded)
        assertEquals(false, controller.onScrolled(32, userScrolling = true))
        assertNull(controller.onScrolled(-1000, userScrolling = false))
        assertFalse(controller.expanded)
    }

    @Test
    fun expandingForSearchResetsStateAndKeepsChromeVisible() {
        val controller = EmojiScrollChromeController(32, 20)
        assertEquals(false, controller.onScrolled(40, true))
        assertEquals(true, controller.expand())
        assertNull(controller.expand())
        assertNull(controller.onScrolled(15, true))
        assertTrue(controller.expanded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidThresholds() {
        EmojiScrollChromeController(0, 20)
    }
}
