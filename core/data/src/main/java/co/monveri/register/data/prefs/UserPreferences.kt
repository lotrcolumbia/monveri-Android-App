package co.monveri.register.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-sensitive user preferences. Tokens stay in [SecurePrefs] (Keystore-backed); this is for
 * cosmetic / UX state that's fine in plaintext: theme override, last-selected register, opt-in
 * flags for upcoming features.
 *
 * Implemented on top of Jetpack DataStore so reads are reactive (`Flow`) and writes are atomic.
 */
private val Context.userPreferencesStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

enum class ThemeMode { System, Light, Dark }

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // DataStore guidance: wrap `.data` with `.catch` so disk IO errors surface as empty
    // preferences instead of terminating the flow. Non-IO exceptions still propagate.
    private val safeData: Flow<Preferences> = context.userPreferencesStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    val themeMode: Flow<ThemeMode> = safeData.map { prefs ->
        prefs[KEY_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.System
    }

    val analyticsOptIn: Flow<Boolean> = safeData.map { prefs ->
        prefs[KEY_ANALYTICS_OPT_IN] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.userPreferencesStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setAnalyticsOptIn(optIn: Boolean) {
        context.userPreferencesStore.edit { it[KEY_ANALYTICS_OPT_IN] = optIn }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_ANALYTICS_OPT_IN = booleanPreferencesKey("analytics_opt_in")
    }
}
