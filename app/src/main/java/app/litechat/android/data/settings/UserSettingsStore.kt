package app.litechat.android.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("user_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val languageTag: String = "system",
    val temperature: Float = 0.7f,
    val topP: Float = 1f
)

class UserSettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val language = stringPreferencesKey("language")
        val temperature = floatPreferencesKey("temperature")
        val topP = floatPreferencesKey("top_p")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[Keys.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            dynamicColor = p[Keys.dynamicColor] ?: true,
            languageTag = p[Keys.language] ?: "system",
            temperature = p[Keys.temperature] ?: 0.7f,
            topP = p[Keys.topP] ?: 1f
        )
    }

    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.name }
    suspend fun setLanguage(value: String) = context.dataStore.edit { it[Keys.language] = value }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = value }
    suspend fun setParameters(temperature: Float, topP: Float) = context.dataStore.edit {
        it[Keys.temperature] = temperature
        it[Keys.topP] = topP
    }
}
