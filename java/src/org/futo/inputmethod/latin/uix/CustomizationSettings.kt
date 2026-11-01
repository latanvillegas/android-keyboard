package org.futo.inputmethod.latin.uix

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
