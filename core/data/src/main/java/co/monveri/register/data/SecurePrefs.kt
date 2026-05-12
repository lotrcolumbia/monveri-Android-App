package co.monveri.register.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over `EncryptedSharedPreferences` (AES-256/GCM, key in Android Keystore).
 *
 * Stores the pieces needed to (a) talk to a paired store and (b) remember the signed-in
 * employee across cold starts. Catalog/cart/ticket data lives in Room in Phase 2+ — keep this
 * surface small.
 *
 * Keys are intentionally explicit constants so a future audit can grep them in one place.
 */
@Singleton
class SecurePrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // --- Store binding -------------------------------------------------------

    var storeApiKey: String?
        get() = prefs.getString(KEY_STORE_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_STORE_API_KEY, value).apply()

    var storeBaseUrl: String?
        get() = prefs.getString(KEY_STORE_BASE_URL, null)
        set(value) = prefs.edit().putString(KEY_STORE_BASE_URL, value).apply()

    var storeName: String?
        get() = prefs.getString(KEY_STORE_NAME, null)
        set(value) = prefs.edit().putString(KEY_STORE_NAME, value).apply()

    var storeCode: String?
        get() = prefs.getString(KEY_STORE_CODE, null)
        set(value) = prefs.edit().putString(KEY_STORE_CODE, value).apply()

    // --- Employee session ----------------------------------------------------

    var employeeId: Int?
        get() = if (prefs.contains(KEY_EMPLOYEE_ID)) prefs.getInt(KEY_EMPLOYEE_ID, -1) else null
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_EMPLOYEE_ID) else editor.putInt(KEY_EMPLOYEE_ID, value)
            editor.apply()
        }

    var employeeName: String?
        get() = prefs.getString(KEY_EMPLOYEE_NAME, null)
        set(value) = prefs.edit().putString(KEY_EMPLOYEE_NAME, value).apply()

    var employeeUsername: String?
        get() = prefs.getString(KEY_EMPLOYEE_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_EMPLOYEE_USERNAME, value).apply()

    var employeeLevel: Int?
        get() = if (prefs.contains(KEY_EMPLOYEE_LEVEL)) prefs.getInt(KEY_EMPLOYEE_LEVEL, -1) else null
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_EMPLOYEE_LEVEL) else editor.putInt(KEY_EMPLOYEE_LEVEL, value)
            editor.apply()
        }

    var permissionsJson: String?
        get() = prefs.getString(KEY_PERMISSIONS_JSON, null)
        set(value) = prefs.edit().putString(KEY_PERMISSIONS_JSON, value).apply()

    /** Clears the employee fields only — pairing remains intact (matches plan acceptance test). */
    fun clearEmployeeSession() {
        prefs.edit()
            .remove(KEY_EMPLOYEE_ID)
            .remove(KEY_EMPLOYEE_NAME)
            .remove(KEY_EMPLOYEE_USERNAME)
            .remove(KEY_EMPLOYEE_LEVEL)
            .remove(KEY_PERMISSIONS_JSON)
            .apply()
    }

    /** Full wipe (un-pair). Useful for the QA test that confirms returning to Pairing screen. */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE = "monveri_secure_prefs"

        const val KEY_STORE_API_KEY = "store_api_key"
        const val KEY_STORE_BASE_URL = "store_base_url"
        const val KEY_STORE_NAME = "store_name"
        const val KEY_STORE_CODE = "store_code"

        const val KEY_EMPLOYEE_ID = "employee_id"
        const val KEY_EMPLOYEE_NAME = "employee_name"
        const val KEY_EMPLOYEE_USERNAME = "employee_username"
        const val KEY_EMPLOYEE_LEVEL = "employee_level"
        const val KEY_PERMISSIONS_JSON = "permissions_json"
    }
}
