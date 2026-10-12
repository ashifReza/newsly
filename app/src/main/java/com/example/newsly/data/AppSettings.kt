package com.example.newsly.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class Accent { EMBER, COBALT, JADE, VIOLET, SAFFRON }

enum class TextSize { STANDARD, LARGE }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.EMBER,
    val textSize: TextSize = TextSize.STANDARD,
    val onboardingDone: Boolean = false,
)

/** Appearance choices and first-launch state, kept in SharedPreferences. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setAccent(accent: Accent) = update { it.copy(accent = accent) }
    fun setTextSize(size: TextSize) = update { it.copy(textSize = size) }
    fun finishOnboarding() = update { it.copy(onboardingDone = true) }

    private fun update(change: (Settings) -> Settings) {
        val new = change(_settings.value)
        prefs.edit()
            .putString(KEY_MODE, new.themeMode.name)
            .putString(KEY_ACCENT, new.accent.name)
            .putString(KEY_TEXT_SIZE, new.textSize.name)
            .putBoolean(KEY_ONBOARDING_DONE, new.onboardingDone)
            .apply()
        _settings.value = new
    }

    private fun load() = Settings(
        themeMode = enumOr(prefs.getString(KEY_MODE, null), ThemeMode.SYSTEM),
        accent = enumOr(prefs.getString(KEY_ACCENT, null), Accent.EMBER),
        textSize = enumOr(prefs.getString(KEY_TEXT_SIZE, null), TextSize.STANDARD),
        onboardingDone = prefs.getBoolean(KEY_ONBOARDING_DONE, false),
    )

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        const val KEY_MODE = "theme_mode"
        const val KEY_ACCENT = "accent"
        const val KEY_TEXT_SIZE = "text_size"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
