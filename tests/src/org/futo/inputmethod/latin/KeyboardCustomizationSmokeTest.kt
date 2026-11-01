package org.futo.inputmethod.latin

import android.graphics.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.futo.inputmethod.latin.uix.resizing.DragDelta
import org.futo.inputmethod.latin.uix.resizing.FloatingKeyboardResizeHelper
import org.futo.inputmethod.latin.uix.resizing.KeyboardResizeHelper
import org.futo.inputmethod.latin.uix.resizing.SplitKeyboardResizeHelper
import org.futo.inputmethod.v2keyboard.FloatingKeyboardSize
import org.futo.inputmethod.v2keyboard.KeyboardMode
import org.futo.inputmethod.v2keyboard.OneHandedDirection
import org.futo.inputmethod.v2keyboard.RegularKeyboardSize
import org.futo.inputmethod.v2keyboard.SavedKeyboardSizingSettings
import org.futo.inputmethod.v2keyboard.SplitKeyboardSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Tests production sizing helpers on an emulator, not the visual mode selector. */
@RunWith(AndroidJUnit4::class)
class KeyboardCustomizationSmokeTest {
    private val density = Density(3f)
    private val insets = Rect(0, 0, 0, 0)
    private val viewport = IntSize(1080, 2400)

    private fun settings(mode: KeyboardMode): SavedKeyboardSizingSettings {
        val padding = DpRect(0.dp, 0.dp, 0.dp, 0.dp)
        return SavedKeyboardSizingSettings(
            currentMode = mode,
            heightMultiplier = 1.0f,
            heightAdditionDp = 0.0f,
            paddingDp = padding,
            splitWidthFraction = 0.6f,
            splitPaddingDp = padding,
            splitHeightAdditionDp = 0.0f,
            prefersSplit = true,
            oneHandedRectDp = DpRect(0.dp, 0.dp, 360.dp, 0.dp),
            oneHandedDirection = OneHandedDirection.Right,
            oneHandedHeightAdditionDp = 0.0f,
            floatingBottomOriginDp = Pair(0.0f, 0.0f),
            floatingWidthDp = 240.0f,
            floatingHeightDp = 180.0f
        )
    }

    @Test
    fun floatingModeClampsExtremeSizeToViewport() {
        val computed = FloatingKeyboardSize(
            width = 720, height = 540, padding = insets,
            bottomOrigin = Pair(0, 0)
        )
        val helper = FloatingKeyboardResizeHelper(
            viewport, computed, density, settings(KeyboardMode.Floating),
            DragDelta(right = 50000.0f, bottom = 50000.0f)
        )
        helper.applyDeltaSizeWithLimits()
        helper.applyOriginOffsetWithLimits()
        assertEquals(360.0f, helper.editedSettings.floatingWidthDp, 0.01f)
        assertEquals(800.0f, helper.editedSettings.floatingHeightDp, 0.01f)
        assertTrue(helper.editedSettings.floatingBottomOriginDp.first >= 0.0f)
    }

    @Test
    fun splitModeAllowsFullViewportFraction() {
        val computed = SplitKeyboardSize(
            width = viewport.width, height = 500, padding = insets,
            splitLayoutWidth = 600
        )
        val helper = SplitKeyboardResizeHelper(
            viewport, computed, density, settings(KeyboardMode.Split),
            DragDelta()
        )
        helper.editSplitLayoutWidth(50000.0f)
        assertEquals(1.0f, helper.editedSettings.splitWidthFraction, 0.001f)
    }

    @Test
    fun landscapeModeCanExpandToViewportHeight() {
        val wideViewport = IntSize(2400, 1080)
        val computed = RegularKeyboardSize(
            width = 2400, height = 450, padding = insets,
            singleRowHeight = 110
        )
        val helper = KeyboardResizeHelper(
            wideViewport, computed, density, settings(KeyboardMode.Regular),
            DragDelta(top = -30000.0f)
        )
        helper.editBottomPaddingAndHeightAddition()
        assertTrue(helper.editedSettings.heightAdditionDp > 0.0f)
        assertEquals(360.0f, helper.maximumKeyboardHeight.value, 0.01f)
    }

    @Test
    fun everyModeSurvivesSerialization() {
        for (mode in KeyboardMode.entries) {
            val original = settings(mode)
            val restored = SavedKeyboardSizingSettings.fromJsonString(
                original.toJsonString()
            )
            assertEquals(original, restored)
        }
    }
}
