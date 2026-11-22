package com.blackeye.kribo.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun mapsSupportedLanguageTags() {
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromLanguageTag("es"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromLanguageTag("es-MX"))
        assertEquals(AppLanguage.SPANISH, AppLanguage.fromLanguageTag("ES_es"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-GB"))
    }

    @Test
    fun unknownOrMissingTagsFallBackToAutomatic() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("fr-FR"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("es,en"))
    }
}
