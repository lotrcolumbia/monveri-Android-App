package co.monveri.register.network

/**
 * Interface implemented by `:core:data` to surface the persisted store key + employee id without
 * dragging EncryptedSharedPreferences into `:core:network`. Keeps the modules acyclic.
 */
interface AuthHeaderProvider {
    fun storeKey(): String?
    fun employeeId(): Int?
}
