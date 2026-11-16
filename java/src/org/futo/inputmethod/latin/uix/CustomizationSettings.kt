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

/** Optional energy saver: skip continuously rendered theme shaders. */
val ReduceVisualEffectsSetting = SettingsKey(
    booleanPreferencesKey("reduce_visual_effects"), false
)

/** Height in dp of the editor inside the offline AI action panel, not the keyboard keys. */
val AiPromptEditorHeightSetting = SettingsKey(
    intPreferencesKey("custom_ai_prompt_editor_height_dp"), 72
)

/** 0: automatic, 1: fixed manual height, 2: hybrid with user min/max. */
val AiEditorGrowthModeSetting = SettingsKey(
    intPreferencesKey("ai_editor_growth_mode"), 0
)

/** Hybrid editor minimum and maximum heights; never affect keyboard keys. */
val AiEditorMinHeightSetting = SettingsKey(
    intPreferencesKey("ai_editor_min_height_dp"), 52
)

val AiEditorMaxHeightSetting = SettingsKey(
    intPreferencesKey("ai_editor_max_height_dp"), 220
)

/** IA button accessibility, independent from custom theme definitions. */
val AiQuickButtonVisibleSetting = SettingsKey(
    booleanPreferencesKey("ai_quick_button_visible"), true
)

/** 0 = inherit theme; 1 = purple; 2 = RGB; 3 = embossed theme relief. */
val AiQuickButtonAppearanceSetting = SettingsKey(
    intPreferencesKey("ai_quick_button_appearance"), 0
)
