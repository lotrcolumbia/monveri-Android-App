package co.monveri.register.data

import co.monveri.register.network.AuthHeaderProvider
import co.monveri.register.network.BaseUrlProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges `SecurePrefs` to the interfaces declared in `:core:network`, keeping the network
 * module unaware of Android-specific storage.
 */
@Singleton
class SecurePrefsAuthHeaderProvider @Inject constructor(
    private val prefs: SecurePrefs,
) : AuthHeaderProvider {
    override fun storeKey(): String? = prefs.storeApiKey
    override fun employeeId(): Int? = prefs.employeeId
}

@Singleton
class SecurePrefsBaseUrlProvider @Inject constructor(
    private val prefs: SecurePrefs,
) : BaseUrlProvider {
    override fun baseUrl(): String =
        prefs.storeBaseUrl ?: BaseUrlProvider.UNPAIRED_PLACEHOLDER
}
