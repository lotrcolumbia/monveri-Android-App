package co.monveri.register.payments

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent cache for the last-paired Stripe reader. iOS doesn't persist this (the iOS app is
 * always foreground so the in-memory `ReaderManager.connectedReader` is enough); Android needs
 * disk persistence so we can silently auto-reconnect after process death.
 *
 * Stored under a dedicated DataStore file so a corrupt user-prefs blob can't take the reader
 * pairing down with it.
 */
@Singleton
class ReaderPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val dataStore = context.readerDataStore

    // Wrap `.data` with `.catch` so a corrupted DataStore file surfaces as empty prefs instead
    // of terminating the flow (and crashing the cold-start auto-reconnect). Mirrors the pattern
    // in `UserPreferences`. Non-IO exceptions still propagate.
    private val safeData: Flow<Preferences> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    /** Reactive view of the persisted serial — null when no reader has ever been paired. */
    val lastReaderSerial: Flow<String?> = safeData.map { it[KEY_SERIAL] }

    suspend fun rememberReader(serial: String) {
        dataStore.edit { prefs -> prefs[KEY_SERIAL] = serial }
    }

    suspend fun forgetReader() {
        dataStore.edit { prefs -> prefs.remove(KEY_SERIAL) }
    }

    /** One-shot accessor used during cold-start auto-reconnect. */
    suspend fun currentSerial(): String? = lastReaderSerial.first()

    private companion object {
        val KEY_SERIAL: Preferences.Key<String> = stringPreferencesKey("last_reader_serial")
    }
}

private val Context.readerDataStore by preferencesDataStore(name = "monveri_reader")
