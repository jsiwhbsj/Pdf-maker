package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.localization.AppLanguage
import com.example.ui.theme.AccentTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pdf_plus_prefs")

data class UserSettings(
    val isDarkMode: Boolean = true,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val accentTheme: AccentTheme = AccentTheme.VIOLET
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val ACCENT_THEME = stringPreferencesKey("accent_theme")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val dark = prefs[Keys.DARK_MODE] ?: true
        val langCode = prefs[Keys.LANGUAGE] ?: AppLanguage.ENGLISH.code
        val themeKey = prefs[Keys.ACCENT_THEME] ?: AccentTheme.VIOLET.key

        UserSettings(
            isDarkMode = dark,
            language = AppLanguage.entries.firstOrNull { it.code == langCode } ?: AppLanguage.ENGLISH,
            accentTheme = AccentTheme.entries.firstOrNull { it.key == themeKey } ?: AccentTheme.VIOLET
        )
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = enabled }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setAccentTheme(theme: AccentTheme) {
        context.dataStore.edit { it[Keys.ACCENT_THEME] = theme.key }
    }
}
