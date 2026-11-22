package com.blackeye.kribo.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** In-app language options. [SYSTEM] follows the device locale. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    SPANISH("es"),
    ENGLISH("en"),
    ;

    companion object {

        /** Pure mapping used by [current] and by unit tests. */
        fun fromLanguageTag(tag: String?): AppLanguage {
            val normalized = tag
                ?.trim()
                ?.lowercase()
                ?.substringBefore('-')
                ?.substringBefore('_')
            return when (normalized) {
                "es" -> SPANISH
                "en" -> ENGLISH
                else -> SYSTEM
            }
        }

        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            return fromLanguageTag(locales.toLanguageTags())
        }

        /** Persisted by AppCompat on API 26-32 and by the platform on API 33+. */
        fun apply(language: AppLanguage) {
            val locales = language.tag
                ?.let { LocaleListCompat.forLanguageTags(it) }
                ?: LocaleListCompat.getEmptyLocaleList()
            if (AppCompatDelegate.getApplicationLocales() != locales) {
                AppCompatDelegate.setApplicationLocales(locales)
            }
        }
    }
}
