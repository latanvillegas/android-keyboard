package org.futo.inputmethod.latin.uix

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey

/** Persist user customizations independently of the current theme. */
val SuggestionBarHeightSetting = SettingsKey(
    intPreferencesKey("custom_suggestion_bar_height_dp"), 40
)

val SuggestionFontSizeSetting = SettingsKey(
    intPreferencesKey("custom_suggestion_font_size_sp"), 18
)

val ActionIconScaleSetting = SettingsKey(
    floatPreferencesKey("custom_action_icon_scale"), 1.0f
)

/** Multiplies letter, number and hint glyph sizes without affecting key geometry. */
val KeyLabelScaleSetting = SettingsKey(
    floatPreferencesKey("custom_key_label_scale"), 1.0f
)

/** Emoji and ASCII emoticon grid cell size in dp. */
val EmojiCellSizeSetting = SettingsKey(
    intPreferencesKey("custom_emoji_cell_size_dp"), 42
)

/** Shaded relief for emoji picker glyphs; does not replace Unicode glyphs. */
val EmojiReliefSetting = SettingsKey(
    booleanPreferencesKey("custom_emoji_relief_3d"), true
)
