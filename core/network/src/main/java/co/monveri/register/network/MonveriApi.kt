package co.monveri.register.network

import co.monveri.register.model.EmployeeLoginResponse
import co.monveri.register.model.KeyValidation
import co.monveri.register.network.dto.ApiEnvelope
import co.monveri.register.network.dto.BackOfficeProductDto
import co.monveri.register.network.dto.BarcodeMatchDto
import co.monveri.register.network.dto.CancelIntentRequest
import co.monveri.register.network.dto.CaptureIntentRequest
import co.monveri.register.network.dto.CatalogSyncDto
import co.monveri.register.network.dto.CategoryDto
import co.monveri.register.network.dto.ConnectionTokenDto
import co.monveri.register.network.dto.ConnectionTokenRequest
import co.monveri.register.network.dto.CreateExpenseResponseDto
import co.monveri.register.network.dto.CreateIntentRequest
import co.monveri.register.network.dto.CustomerSearchDto
import co.monveri.register.network.dto.ProductSearchDto
import co.monveri.register.network.dto.CloseSessionRequest
import co.monveri.register.network.dto.CurrentSessionEnvelopeDto
import co.monveri.register.network.dto.EmailReceiptRequest
import co.monveri.register.network.dto.EmailReceiptResponseDto
import co.monveri.register.network.dto.ExpenseCategoriesDto
import co.monveri.register.network.dto.ExpenseDetailDto
import co.monveri.register.network.dto.ExpenseListDto
import co.monveri.register.network.dto.IntentStatusResponseDto
import co.monveri.register.network.dto.ManageProductImagesRequest
import co.monveri.register.network.dto.ManageProductImagesResponseDto
import co.monveri.register.network.dto.OpenSessionRequest
import co.monveri.register.network.dto.PaymentIntentResponseDto
import co.monveri.register.network.dto.ProductImageDto
import co.monveri.register.network.dto.QuickButtonDto
import co.monveri.register.network.dto.ReceiptBrandingDto
import co.monveri.register.network.dto.SaveProductRequest
import co.monveri.register.network.dto.SaveProductResponseDto
import co.monveri.register.network.dto.SessionReportDto
import co.monveri.register.network.dto.TicketDetailResponseDto
import co.monveri.register.network.dto.TicketSubmitRequest
import co.monveri.register.network.dto.TicketSubmitResponseDto
import co.monveri.register.network.dto.UploadProductImageRequest
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
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

    /**
     * Admin-configured Quick tab buttons for this register (per-register override, falling back
     * to the global set — resolved server-side).
     */
    @GET("config/quick-buttons.php")
    suspend fun quickButtons(): ApiEnvelope<List<QuickButtonDto>>

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

    /** Opens a new till session for this (employee, device) pair. `data` is the new session id. */
    @POST("session/open.php")
    suspend fun openRegisterSession(@Body body: OpenSessionRequest): ApiEnvelope<Long>

    /** The open session for this (employee, device) pair, or `session: null` if there isn't one. */
    @GET("session/current.php")
    suspend fun currentRegisterSession(): ApiEnvelope<CurrentSessionEnvelopeDto>

    /**
     * Same report [closeRegisterSession] would produce, without actually closing the session —
     * lets the cashier see expected cash before they count the drawer. `null` when there's no
     * open session to preview.
     */
    @GET("session/preview_close.php")
    suspend fun previewCloseRegisterSession(): ApiEnvelope<SessionReportDto?>

    /** Closes the session and returns the final variance report. */
    @POST("session/close.php")
    suspend fun closeRegisterSession(@Body body: CloseSessionRequest): ApiEnvelope<SessionReportDto?>

    /**
     * Full admin-editable product record for the Back Office editor. Backend requires
     * `manage_products` permission (server checks `X-Employee-Id` — level-1 admins always pass).
     */
    @GET("products/get.php")
    suspend fun getBackOfficeProduct(@Query("id") id: Long): ApiEnvelope<BackOfficeProductDto>

    /** Create-or-update a product. See [SaveProductRequest]'s doc — this is a full-row overwrite. */
    @POST("products/save.php")
    suspend fun saveBackOfficeProduct(@Body body: SaveProductRequest): ApiEnvelope<SaveProductResponseDto>

    /**
     * Uploads one product photo (base64-in-JSON, not multipart — matches the backend's actual
     * v1 contract). JPEG/PNG only, 8 MB cap, enforced server-side. Takes effect immediately,
     * independent of the product's own Save button.
     */
    @POST("products/image-upload.php")
    suspend fun uploadProductImage(@Body body: UploadProductImageRequest): ApiEnvelope<ProductImageDto>

    /** Delete / set-primary / reorder product images in one batched call. Returns the full gallery. */
    @POST("products/image-manage.php")
    suspend fun manageProductImages(
        @Body body: ManageProductImagesRequest,
    ): ApiEnvelope<ManageProductImagesResponseDto>

    /**
     * Creates a card-present PaymentIntent server-side. Always call this (never a local/on-device
     * intent create) — see [co.monveri.register.network.dto.CreateIntentRequest]'s doc.
     */
    @POST("payments/intent-create.php")
    suspend fun createPaymentIntent(@Body body: CreateIntentRequest): ApiEnvelope<PaymentIntentResponseDto>

    /** Captures a manual-capture PaymentIntent. A no-op success for automatic-capture intents. */
    @POST("payments/intent-capture.php")
    suspend fun capturePaymentIntent(@Body body: CaptureIntentRequest): ApiEnvelope<IntentStatusResponseDto>

    /** Cancels an in-flight PaymentIntent so an abandoned auth doesn't linger on the card. */
    @POST("payments/intent-cancel.php")
    suspend fun cancelPaymentIntent(@Body body: CancelIntentRequest): ApiEnvelope<IntentStatusResponseDto>

    /** Records a finalized sale. Idempotent on `idempotency_key` — safe to retry after a timeout. */
    @POST("transactions/submit.php")
    suspend fun submitTicket(@Body body: TicketSubmitRequest): ApiEnvelope<TicketSubmitResponseDto>

    /** Canonical ticket + line items + split legs, for the post-sale receipt screen. */
    @GET("tickets/get.php")
    suspend fun getTicketReceipt(@Query("id") id: Long): ApiEnvelope<TicketDetailResponseDto>

    /** Store name/address/header/footer for the printed/emailed receipt. */
    @GET("tickets/receipt-branding.php")
    suspend fun getReceiptBranding(): ApiEnvelope<ReceiptBrandingDto>

    /** Emails a rendered receipt for an already-submitted ticket. */
    @POST("tickets/email-receipt.php")
    suspend fun emailReceipt(@Body body: EmailReceiptRequest): ApiEnvelope<EmailReceiptResponseDto>

    /** Active expense categories for the Back Office Expenses picker. No `manage_expenses` gate. */
    @GET("expenses/categories.php")
    suspend fun expenseCategories(): ApiEnvelope<ExpenseCategoriesDto>

    /**
     * Paginated expense list. `mine=1` (the backend default) scopes to the calling employee's own
     * submissions; admins could pass `mine=0` for the full store list, but this client always
     * scopes to the signed-in employee for now — a full "everyone's expenses" review view is out
     * of scope for this slice.
     */
    @GET("expenses/list.php")
    suspend fun listExpenses(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = DEFAULT_EXPENSE_PAGE_SIZE,
    ): ApiEnvelope<ExpenseListDto>

    /** Single expense plus its audit timeline — backs the Back Office Expenses detail screen. */
    @GET("expenses/get.php")
    suspend fun getExpense(@Query("id") id: Long): ApiEnvelope<ExpenseDetailDto>

    /**
     * Create a new expense. `expense` is the JSON-encoded [co.monveri.register.network.dto.CreateExpenseRequest]
     * as a multipart text part — no `image` part in this slice (manual entry only; camera capture
     * + OCR is a separate follow-up). Idempotent on `client_uuid`: a retried submit with the same
     * id returns the original row rather than inserting a duplicate.
     */
    @Multipart
    @POST("expenses/create.php")
    suspend fun createExpense(@Part("expense") expense: RequestBody): ApiEnvelope<CreateExpenseResponseDto>

    companion object {
        const val DEFAULT_EXPENSE_PAGE_SIZE: Int = 25
        const val DEFAULT_SEARCH_LIMIT: Int = 25
    }
}

@kotlinx.serialization.Serializable
data class EmployeeLoginRequest(val pin: String)
