package co.monveri.register.network

import co.monveri.register.model.EmployeeLoginResponse
import co.monveri.register.model.KeyValidation
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Retrofit interface targeting `/api/register/`. Phase 1 only exposes the two auth endpoints;
 * later phases bolt on catalog, customers, payments etc.
 */
interface MonveriApi {

    /**
     * Pairing handshake. Caller passes the full URL (since base URL isn't persisted until after
     * this succeeds) plus the candidate API key as the `X-Store-Key` header. The interceptor's
     * persisted key is bypassed for this call via the explicit header argument.
     */
    @GET
    suspend fun validateKey(
        @Url url: String,
        @Header(AuthHeaders.STORE_KEY) storeKey: String,
    ): KeyValidation

    /**
     * Employee PIN login. Runs against the paired store via the interceptor-supplied headers.
     */
    @POST("auth/employee-login.php")
    suspend fun employeeLogin(
        @Body body: EmployeeLoginRequest,
    ): EmployeeLoginResponse
}

@kotlinx.serialization.Serializable
data class EmployeeLoginRequest(val pin: String)
