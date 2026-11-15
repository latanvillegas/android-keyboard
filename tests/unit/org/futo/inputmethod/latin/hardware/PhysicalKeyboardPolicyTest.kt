package org.futo.inputmethod.latin.hardware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalKeyboardPolicyTest {
    @Test fun invalidModeFallsBackToSystem() {
        assertEquals(VirtualKeyboardMode.SYSTEM, VirtualKeyboardMode.parse("invalid"))
    }
    @Test fun systemPreservesNativeDecision() {
        assertFalse(resolveKeyboardVisibility(VirtualKeyboardMode.SYSTEM, true, true, false))
        assertTrue(resolveKeyboardVisibility(VirtualKeyboardMode.SYSTEM, true, false, true))
    }
    @Test fun hideOnlyWhenConnected() {
        assertFalse(resolveKeyboardVisibility(VirtualKeyboardMode.HIDE_WHEN_CONNECTED, true, true, true))
        assertTrue(resolveKeyboardVisibility(VirtualKeyboardMode.HIDE_WHEN_CONNECTED, false, false, true))
    }
    @Test fun manualAndFallbackBehavior() {
        assertFalse(resolveKeyboardVisibility(VirtualKeyboardMode.MANUAL, true, false, true))
        assertTrue(resolveKeyboardVisibility(VirtualKeyboardMode.MANUAL, true, true, false))
        assertTrue(resolveKeyboardVisibility(VirtualKeyboardMode.MANUAL, false, false, true))
        assertTrue(resolveKeyboardVisibility(VirtualKeyboardMode.ALWAYS_SHOW, true, false, false))
    }
}
