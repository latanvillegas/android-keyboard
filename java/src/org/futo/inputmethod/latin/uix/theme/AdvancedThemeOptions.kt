package org.futo.inputmethod.latin.uix.theme

import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class KeyBackground(
    val foregroundColor: Int?,
    val outlineColor: Int?,
    val outlineWidth: Dp = 3.dp,
    val padding: Rect = Rect(0,0,0,0),
    val gap: RectF = RectF(1.0f,1.0f,1.0f,1.0f),
    val background: Drawable
)

data class KeyIcon(
    val drawable: Drawable
)

/** Per-action assets keyed by Android drawable entry name (e.g. mic_fill). */
data class ThemeActionIcon(
    val image: ImageBitmap,
    val scale: Float = 1.0f,
    val tint: Boolean = false
)

data class AdvancedThemeOptions(
    val backgroundShader: String? = null,
    val backgroundImage: ImageBitmap? = null,
    val backgroundImageVisibleArea: Rect? = null,
    val thumbnailImage: ImageBitmap? = null,
    val thumbnailScale: Float = 1.0f,
    val keyRoundness: Float = 1.0f,
    val actionBarOpacity: Float = 0.5f,
    val keyBorders: Boolean? = null,
    val keyBackgrounds: KeyedBitmaps<KeyBackground>? = null,
    val keyIcons: KeyedBitmaps<KeyIcon>? = null,
    val actionMicIcon: ImageBitmap? = null,
    val actionMicIconScale: Float = 1.0f,
    // Optional V2 extension: V1 themes continue to use their original icons.
    val actionIcons: Map<String, ThemeActionIcon> = emptyMap(),
    val actionIconColor: Color? = null,
    val actionButtonBackgroundColor: Color? = null,
    val actionButtonForegroundColor: Color? = null,
    // Optional 9-slice skins for the two circular action controls.
    val actionButtonBorder: KeyBackground? = null,
    val actionButtonPressedBorder: KeyBackground? = null,
    val expandButtonBorder: KeyBackground? = null,
    val expandButtonPressedBorder: KeyBackground? = null,
    val micButtonBorder: KeyBackground? = null,
    val micButtonPressedBorder: KeyBackground? = null,
    val expandedActionsBarColor: Color? = null,
    val suggestionFontScale: Float = 1.0f,
    val suggestionTextColor: Color? = null,
    val suggestionBarColor: Color? = null,
    val suggestionDividerColor: Color? = null,
    // Unified suggestion-strip 9-slice, responsive to portrait and landscape.
    val suggestionBarBorder: KeyBackground? = null,
    val suggestionBarInsetHorizontal: Float = 2.0f,
    val suggestionBarInsetVertical: Float = 2.0f,
    // Optional per-suggestion backgrounds. Each 9-slice drawable is decoded once per theme.
    val suggestionItemBorder: KeyBackground? = null,
    val suggestionItemPressedBorder: KeyBackground? = null,
    val suggestionItemBackgroundColor: Color? = null,
    val suggestionItemPressedBackgroundColor: Color? = null,
    val suggestionItemRadius: Float = 12.0f,
    val suggestionItemInsetHorizontal: Float = 3.0f,
    val suggestionItemInsetVertical: Float = 2.0f,
    val suggestionItemHideDividers: Boolean = true,
    val font: Typeface? = null,
    val themeName: String? = null,
    val themeAuthor: String? = null,

    val textSizeMultiplier: Float = 1.0f,
    val hintSizeMultiplier: Float = 1.0f,
    val textWeight: Float? = null,
    val hintWeight: Float? = null,

    val centerHints: Boolean = false,
)
