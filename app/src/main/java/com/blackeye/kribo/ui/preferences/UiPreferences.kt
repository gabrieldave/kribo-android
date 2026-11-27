package com.blackeye.kribo.ui.preferences

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The four approved Kribo palettes. */
enum class AppTheme(
    val id: String,
    val isLight: Boolean,
    val previewBackground: Long,
    val previewAccent: Long,
) {
    OBSIDIAN("obsidian", false, 0xFF0D1117, 0xFF00D8FF),
    WARM_PAPER("warm_paper", true, 0xFFFAF8F5, 0xFF0099B8),
    AMOLED("amoled", false, 0xFF000000, 0xFF00D8FF),
    PURE_LIGHT("pure_light", true, 0xFFFFFFFF, 0xFF0088A8),
    EMERALD("emerald", false, 0xFF071A12, 0xFF34D399),
    OCEAN("ocean", false, 0xFF08111F, 0xFF38BDF8),
    VIOLET("violet", false, 0xFF120A1F, 0xFFC084FC),
    ROSE("rose", true, 0xFFFFF7FA, 0xFFDB2777),
    AMBER("amber", true, 0xFFFFF9EE, 0xFFD97706),
    ;

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: OBSIDIAN
    }
}

enum class HomeLayout(val id: String) {
    COMPACT("compact"),
    BENTO("bento"),
    ;

    companion object {
        fun fromId(id: String?): HomeLayout = entries.firstOrNull { it.id == id } ?: COMPACT
    }
}

enum class VoiceEngineMode(val id: String) {
    SYSTEM("system"),
    OFFLINE_MOONSHINE("offline_moonshine"),
    ;

    companion object {
        fun fromId(id: String?): VoiceEngineMode =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * Lightweight UI preferences (theme + home layout). Separate from Room, secure OCR storage and
 * billing; reads happen once and live changes are exposed as StateFlows.
 */
class UiPreferences(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(
        AppTheme.fromId(preferences.getString(KEY_THEME, null)),
    )
    val theme: StateFlow<AppTheme> = _theme.asStateFlow()

    private val _homeLayout = MutableStateFlow(
        HomeLayout.fromId(preferences.getString(KEY_HOME_LAYOUT, null)),
    )
    val homeLayout: StateFlow<HomeLayout> = _homeLayout.asStateFlow()

    private val _onboardingSeen = MutableStateFlow(
        preferences.getBoolean(KEY_ONBOARDING_SEEN, false),
    )
    val onboardingSeen: StateFlow<Boolean> = _onboardingSeen.asStateFlow()

    private val _voiceEngineMode = MutableStateFlow(
        VoiceEngineMode.fromId(preferences.getString(KEY_VOICE_ENGINE, null)),
    )
    val voiceEngineMode: StateFlow<VoiceEngineMode> = _voiceEngineMode.asStateFlow()

    fun markOnboardingSeen() {
        if (_onboardingSeen.value) return
        preferences.edit().putBoolean(KEY_ONBOARDING_SEEN, true).apply()
        _onboardingSeen.value = true
    }

    fun isWelcomeNoteSeeded(): Boolean = preferences.getBoolean(KEY_WELCOME_NOTE_SEEDED, false)

    fun markWelcomeNoteSeeded() {
        preferences.edit().putBoolean(KEY_WELCOME_NOTE_SEEDED, true).apply()
    }

    fun setTheme(theme: AppTheme) {
        if (_theme.value == theme) return
        preferences.edit().putString(KEY_THEME, theme.id).apply()
        _theme.value = theme
    }

    fun setHomeLayout(layout: HomeLayout) {
        if (_homeLayout.value == layout) return
        preferences.edit().putString(KEY_HOME_LAYOUT, layout.id).apply()
        _homeLayout.value = layout
    }

    fun setVoiceEngineMode(mode: VoiceEngineMode) {
        if (_voiceEngineMode.value == mode) return
        preferences.edit().putString(KEY_VOICE_ENGINE, mode.id).apply()
        _voiceEngineMode.value = mode
    }

    companion object {
        private const val PREFS_NAME = "kribo_ui"
        private const val KEY_THEME = "theme_v1"
        private const val KEY_HOME_LAYOUT = "home_layout_v1"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen_v1"
        private const val KEY_WELCOME_NOTE_SEEDED = "welcome_note_seeded_v1"
        private const val KEY_VOICE_ENGINE = "voice_engine_v1"
    }
}
