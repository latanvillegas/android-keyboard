package org.futo.inputmethod.latin.uix.settings.pages

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsExporter
import org.futo.inputmethod.latin.uix.settings.UiLocalePreferences
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.NavigationItemStyle
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingNavigationItem

val MiscMenu = UserSettingsMenu(
    title = R.string.misc_settings_title,
    navPath = "misc", registerNavPath = true,
    settings = listOf(
        UserSetting(name = R.string.custom_ui_language_title) {
            val context = LocalContext.current
            var selected by remember { mutableStateOf(UiLocalePreferences.current(context)) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.custom_ui_language_title))
                Text(stringResource(R.string.custom_ui_language_description))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "" to R.string.custom_ui_language_system,
                        "es" to R.string.custom_ui_language_spanish,
                        "en" to R.string.custom_ui_language_english
                    ).forEach { (tag, label) ->
                        if (selected == tag) {
                            Button(onClick = { }) { Text(stringResource(label)) }
                        } else {
                            OutlinedButton(onClick = {
                                UiLocalePreferences.set(context, tag)
                                selected = tag
                                UiLocalePreferences.findActivity(context)?.recreate()
                            }) { Text(stringResource(label)) }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT < 33) {
                    Text(stringResource(R.string.custom_ui_language_older_android_notice))
                }
            }
        },
        userSettingDecorationOnly {
            ScreenTitle(stringResource(R.string.settings_export_configuration_title))
        },

        userSettingNavigationItem(
            title = (R.string.settings_export_configuration),
            subtitle = (R.string.settings_export_configuration_subtitle),
            style = NavigationItemStyle.Misc,
            navigateTo = "exportingcfg"
        ).copy(searchTags = R.string.settings_import_export_tags),
        userSettingNavigationItem(
            title = (R.string.settings_import_configuration),
            subtitle = (R.string.settings_import_configuration_subtitle),
            style = NavigationItemStyle.Misc,
            navigate = { nav ->
                SettingsExporter.triggerImportSettings(nav.context)
            }
        ).copy(searchTags = R.string.settings_import_export_tags),
    )
)