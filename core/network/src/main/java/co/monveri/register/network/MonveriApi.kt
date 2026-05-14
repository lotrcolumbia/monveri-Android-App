package co.monveri.register.network

import co.monveri.register.model.EmployeeLoginResponse
import co.monveri.register.model.KeyValidation
import co.monveri.register.network.dto.ApiEnvelope
import co.monveri.register.network.dto.BarcodeMatchDto
import co.monveri.register.network.dto.CatalogSyncDto
import co.monveri.register.network.dto.CategoryDto
import co.monveri.register.network.dto.ConnectionTokenDto
import co.monveri.register.network.dto.ConnectionTokenRequest
import co.monveri.register.network.dto.CustomerSearchDto
import co.monveri.register.network.dto.ProductSearchDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit interface targeting `/api/register/`. Phase 1 added the two auth endpoints; Phase 3
 * adds catalog (sync/search/barcode/categories) and customer search.
 *
 * Every endpoint runs against the paired store host via [HostSwitchInterceptor]; the
 * [AuthInterceptor] attaches `X-Store-Key` (and `X-Employee-Id` once a cashier is signed in).
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

    /**
     * Full catalog sync — products, variants, and barcode relationships. Phase 9 will pass
     * `since` for incremental sync; Phase 3 always fetches the full snapshot.
     */
    @GET("products/sync.php")
    suspend fun catalogSync(
        @Query("since") since: String? = null,
    ): ApiEnvelope<CatalogSyncDto>

    /** Live keyword search across product name / SKU / UPC. Backend clamps `limit` to [1, 100]. */
    @GET("products/search.php")
    suspend fun searchProducts(
        @Query("q") query: String,
        @Query("limit") limit: Int = DEFAULT_SEARCH_LIMIT,
    ): ApiEnvelope<ProductSearchDto>

    /** Resolve a scanned barcode/UPC/SKU to a product, variant, or multi-pack relationship. */
    @GET("products/barcode.php")
    suspend fun barcodeLookup(
        @Query("code") code: String,
    ): ApiEnvelope<BarcodeMatchDto>

    /** Flat list of categories (with parent ids) for the catalog filter dropdown. */
    @GET("config/categories.php")
    suspend fun categories(): ApiEnvelope<List<CategoryDto>>

    /** Customer + loyalty lookup. `q` matches name / phone / email / loyalty card. */
    @GET("customers/search.php")
    suspend fun searchCustomers(
        @Query("q") query: String,
        @Query("limit") limit: Int = DEFAULT_SEARCH_LIMIT,
    ): ApiEnvelope<CustomerSearchDto>

    /**
     * Mints a short-lived Stripe Terminal connection token. Body's `location_id` is optional —
     * when omitted the backend uses the store's `stripe_terminal_location_id` setting. The token
     * is consumed by the Stripe SDK during reader handshake; never stored client-side.
     */
    @POST("payments/connection-token.php")
    suspend fun stripeConnectionToken(
        @Body body: ConnectionTokenRequest = ConnectionTokenRequest(),
    ): ApiEnvelope<ConnectionTokenDto>

    companion object {
        const val DEFAULT_SEARCH_LIMIT: Int = 25
    }
}

@kotlinx.serialization.Serializable
data class EmployeeLoginRequest(val pin: String)
