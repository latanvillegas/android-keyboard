package org.futo.inputmethod.latin.uix.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * UI-language override. Keyboard layout/dictionary languages are separate.
 *
 * Android 13+ uses the platform's per-app language API (applies across the app).
 * On older versions only the settings Activity's resources are wrapped, leaving
 * the IME service locale and typing configuration untouched.
 */
object UiLocalePreferences {
    private const val PREFS = "futo_ui_locale"
    private const val KEY = "chosen_language"

    fun current(context: Context): String {
        if (Build.VERSION.SDK_INT >= 33) {
            val manager = context.getSystemService(LocaleManager::class.java)
            val tag = manager?.applicationLocales?.toLanguageTags().orEmpty()
            return when {
                tag.startsWith("es", ignoreCase = true) -> "es"
                tag.startsWith("en", ignoreCase = true) -> "en"
                else -> ""
            }
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "").orEmpty().takeIf { it == "es" || it == "en" }.orEmpty()
    }

    fun set(context: Context, language: String) {
        require(language == "" || language == "es" || language == "en")
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(language)
        } else {
            check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, language).commit()) { "Unable to persist UI language" }
        }
    }

    fun wrapSettingsContext(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val chosen = current(context)
        if (chosen.isEmpty()) return context
        val cfg = Configuration(context.resources.configuration)
        val locale = Locale.forLanguageTag(chosen)
        cfg.setLocale(locale)
        cfg.setLayoutDirection(locale)
        return context.createConfigurationContext(cfg)
    }

    fun findActivity(context: Context): Activity? {
        var current: Context? = context
        repeat(12) {
            if (current is Activity) return current
            current = (current as? ContextWrapper)?.baseContext ?: return null
        }
        return null
    }
}
