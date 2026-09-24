package co.monveri.register.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.AuthRepository
import co.monveri.register.data.repository.BarcodeMatch
import co.monveri.register.data.repository.CartLine
import co.monveri.register.data.repository.CartRepository
import co.monveri.register.data.repository.CatalogRepository
import co.monveri.register.data.repository.CatalogSnapshot
import co.monveri.register.data.repository.Category
import co.monveri.register.data.repository.Product
import co.monveri.register.data.repository.QuickButton
import co.monveri.register.data.repository.QuickButtonType
import co.monveri.register.data.repository.RegisterSessionRepository
import co.monveri.register.network.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * Drives the catalog list screen — search input, category filter, scan handoff, and the
 * grid of [Product]s rendered from a combination of:
 *
 *  - the in-memory snapshot from [CatalogRepository] (browse mode), or
 *  - the server-side search results (search mode, kicked off after 300ms debounce).
 *
 * The view's `state` flow merges both so the UI doesn't have to branch.
 */
@HiltViewModel
@OptIn(FlowPreview::class)
class CatalogListViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val cart: CartRepository,
    private val authRepository: AuthRepository,
    private val registerSessionRepository: RegisterSessionRepository,
) : ViewModel() {

    /** Backs the top-bar profile menu's "Signed in as {name}" header. */
    val employeeName: String?
        get() = authRepository.currentSession()?.employee?.name

    private val _hasOpenRegisterSession = MutableStateFlow(false)

    /** Gates the profile menu's "Close Register" entry — mirrors iOS's session-open check. */
    val hasOpenRegisterSession: StateFlow<Boolean> = _hasOpenRegisterSession.asStateFlow()

    /** "Log out" from the profile menu — clears the employee session but keeps store pairing. */
    fun logout() {
        authRepository.logout()
    }

    private val query = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val searchResults = MutableStateFlow<List<Product>>(emptyList())
    private val isSyncing = MutableStateFlow(false)
    private val isSearching = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val barcodeFlash = MutableStateFlow<String?>(null)

    private var searchJob: Job? = null

    private val selectedTab = MutableStateFlow(CatalogTab.QUICK)
    private val quickButtons = MutableStateFlow<List<QuickButton>>(emptyList())
    private val quickButtonsLoading = MutableStateFlow(false)
    private val quickButtonsError = MutableStateFlow<String?>(null)
    private val customItem = MutableStateFlow(CustomItemUiState())

    /**
     * `combine` produces a fresh [CatalogUiState] every time any input changes. Using [stateIn]
     * gives the screen a hot StateFlow it can `collectAsStateWithLifecycle()` directly.
     */
    val state: StateFlow<CatalogUiState> = combine(
        catalog.observeCatalog(),
        query,
        selectedCategoryId,
        searchResults,
        combine(isSyncing, isSearching, errorMessage, barcodeFlash) { syncing, searching, error, flash ->
            UiStatus(syncing, searching, error, flash)
        },
    ) { snapshot, q, categoryId, searchHits, status ->
        val products = if (q.isBlank()) {
            applyCategoryFilter(snapshot.products, categoryId)
        } else {
            applyCategoryFilter(searchHits, categoryId)
        }
        CatalogUiState(
            query = q,
            selectedCategoryId = categoryId,
            categories = snapshot.categories,
            products = products,
            isSyncing = status.syncing,
            isSearching = status.searching,
            errorMessage = status.error,
            barcodeFlashMessage = status.barcodeFlash,
            isInitialLoad = snapshot.products.isEmpty() && status.syncing,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CatalogUiState(),
    )

    /** Reactive cart line count for the floating "view cart" pill. */
    val cartItemCount: StateFlow<Int> = cart.observeCart()
        .map { it.itemCount }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = cart.current().itemCount,
        )

    /**
     * Re-resolves every Quick tile against the catalog snapshot whenever EITHER changes. This
     * has to be reactive, not a one-shot lookup: the buttons load before `catalog.sync()`
     * finishes, and a plain `catalog.current()` call from inside a Composable would freeze at
     * whatever the snapshot was on first composition — Compose has no state read to key a
     * recomposition off, so the tiles would stay stuck on "Unavailable" forever once the sync
     * that would have resolved them completes a moment later.
     */
    private val resolvedQuickButtons: Flow<List<ResolvedQuickButton>> =
        combine(quickButtons, catalog.observeCatalog()) { buttons, snapshot ->
            buttons.map { button -> ResolvedQuickButton(button, resolveQuickButton(button, snapshot)) }
        }

    val tabState: StateFlow<CatalogTabUiState> = combine(
        selectedTab,
        resolvedQuickButtons,
        quickButtonsLoading,
        quickButtonsError,
        customItem,
    ) { tab, buttons, loading, error, custom ->
        CatalogTabUiState(
            selectedTab = tab,
            quickButtons = buttons,
            quickButtonsLoading = loading,
            quickButtonsError = error,
            customItem = custom,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = CatalogTabUiState(),
    )

    /** Reactive cart badge for the top bar — item count + subtotal, mirrors iOS's toolbar chip. */
    val cartSubtotalCents: StateFlow<Long> = cart.observeCart()
        .map { it.totals.subtotalCents }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = cart.current().totals.subtotalCents,
        )

    /** Name of the customer attached to the cart, if any — drives the top-bar pill vs. banner. */
    val attachedCustomerName: StateFlow<String?> = cart.observeCart()
        .map { it.customer?.displayName }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = cart.current().customer?.displayName,
        )

    init {
        refresh()
        loadQuickButtons()
        viewModelScope.launch {
            val result = registerSessionRepository.current()
            _hasOpenRegisterSession.value = result is NetworkResult.Success && result.data != null
        }
    }

    fun onTabSelected(tab: CatalogTab) {
        selectedTab.value = tab
    }

    fun loadQuickButtons() {
        if (quickButtonsLoading.value) return
        quickButtonsLoading.value = true
        quickButtonsError.value = null
        viewModelScope.launch {
            when (val result = catalog.quickButtons()) {
                is NetworkResult.Success -> quickButtons.value = result.data
                is NetworkResult.Failure -> quickButtonsError.value = result.error.message
            }
            quickButtonsLoading.value = false
        }
    }


    fun onCustomItemNameChanged(value: String) {
        customItem.value = customItem.value.copy(name = value)
    }

    fun onCustomItemDigit(digit: Char) {
        val d = digit.digitToIntOrNull() ?: return
        val current = customItem.value
        val next = (current.cents * 10 + d).coerceAtMost(MAX_CUSTOM_ITEM_CENTS)
        customItem.value = current.copy(cents = next)
    }

    fun onCustomItemBackspace() {
        customItem.value = customItem.value.copy(cents = customItem.value.cents / 10)
    }

    fun onCustomItemClear() {
        customItem.value = customItem.value.copy(cents = 0L)
    }

    fun onCustomItemTaxableChanged(taxable: Boolean) {
        customItem.value = customItem.value.copy(taxable = taxable)
    }

    /** Synthesizes a cart line with no backing product record — mirrors iOS's Custom tab. */
    fun addCustomItem() {
        val current = customItem.value
        val name = current.name.trim()
        if (name.isEmpty() || current.cents <= 0) return

        cart.addItem(
            CartLine(
                // Negative id keeps this line out of any real-product lookup path; randomized so
                // two different custom items in the same cart don't merge into one line (the cart
                // merges by productId + variantId).
                productId = -Random.nextLong(MIN_SYNTHETIC_ID, MAX_SYNTHETIC_ID),
                variantId = null,
                name = name,
                variantLabel = null,
                sku = null,
                unitPriceCents = current.cents,
                quantity = 1,
                isTaxable = current.taxable,
            ),
        )
        barcodeFlash.value = "$name added"
        customItem.value = CustomItemUiState(taxable = current.taxable)
    }

    fun onQueryChanged(value: String) {
        query.value = value
        errorMessage.value = null
        searchJob?.cancel()
        if (value.isBlank()) {
            searchResults.value = emptyList()
            isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            isSearching.value = true
            when (val result = catalog.search(value)) {
                is NetworkResult.Success -> {
                    searchResults.value = result.data
                    errorMessage.value = null
                }
                is NetworkResult.Failure -> errorMessage.value = result.error.message
            }
            isSearching.value = false
        }
    }

    fun onCategorySelected(categoryId: String?) {
        selectedCategoryId.value = categoryId
    }

    /** Pull-to-refresh forces a server sync even when the snapshot is non-empty. */
    fun refresh() {
        if (isSyncing.value) return
        isSyncing.value = true
        errorMessage.value = null
        viewModelScope.launch {
            when (val result = catalog.sync()) {
                is NetworkResult.Success -> Unit
                is NetworkResult.Failure -> errorMessage.value = result.error.message
            }
            isSyncing.value = false
        }
    }

    /**
     * Called by the scanner sheet once a barcode is captured. Looks the code up against the
     * backend, adds the matched product/variant straight to the cart, and flashes a one-line
     * banner so the cashier knows it landed.
     */
    fun onBarcodeScanned(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (val result = catalog.lookupBarcode(trimmed)) {
                is NetworkResult.Success -> {
                    val match = result.data
                    if (match == null) {
                        barcodeFlash.value = "No product matches “$trimmed”"
                    } else {
                        cart.addItem(match.toCartLine())
                        barcodeFlash.value = "${match.product.name} × ${match.qtyCount}"
                    }
                }
                is NetworkResult.Failure -> barcodeFlash.value = result.error.message
            }
        }
    }

    fun dismissBarcodeFlash() {
        barcodeFlash.value = null
    }

    /** Quick-add the default variant straight to the cart from a long-press on a list card. */
    fun quickAdd(product: Product) {
        val line = CartLine(
            productId = product.id,
            variantId = null,
            name = product.name,
            variantLabel = null,
            sku = product.sku,
            upc = product.upc,
            unitOfSale = product.unitOfSale,
            unitPriceCents = product.priceCents,
            quantity = 1,
            isTaxable = product.isTaxable,
        )
        cart.addItem(line)
        barcodeFlash.value = "${product.name} added"
    }

    private fun applyCategoryFilter(items: List<Product>, categoryId: String?): List<Product> {
        if (categoryId.isNullOrBlank()) return items
        return items.filter { it.categoryId == categoryId }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS: Long = 300
        const val STOP_TIMEOUT_MILLIS: Long = 5_000

        /** ~$999,999.99 — same cap iOS's Custom tab keypad enforces (8 digits). */
        const val MAX_CUSTOM_ITEM_CENTS: Long = 99_999_999L
        const val MIN_SYNTHETIC_ID: Long = 1_000_000L
        const val MAX_SYNTHETIC_ID: Long = 9_999_999L
    }
}

/** Which of the four Catalog pages is currently showing. */
enum class CatalogTab { QUICK, PRODUCTS, CATEGORIES, CUSTOM }

/** Where a tapped Quick tile should navigate — or [Unavailable] if its target no longer exists. */
sealed interface QuickButtonTarget {
    data class OpenProduct(val productId: Long) : QuickButtonTarget
    data class OpenCategory(val categoryId: String) : QuickButtonTarget
    data object Unavailable : QuickButtonTarget
}

data class CustomItemUiState(
    val name: String = "",
    val cents: Long = 0L,
    val taxable: Boolean = true,
)

/** Everything the four tab pages need beyond the shared [CatalogUiState] (search/products/categories). */
data class CatalogTabUiState(
    val selectedTab: CatalogTab = CatalogTab.QUICK,
    val quickButtons: List<ResolvedQuickButton> = emptyList(),
    val quickButtonsLoading: Boolean = false,
    val quickButtonsError: String? = null,
    val customItem: CustomItemUiState = CustomItemUiState(),
)

/** A Quick tile paired with where tapping it should go, per the catalog snapshot at combine time. */
data class ResolvedQuickButton(val button: QuickButton, val target: QuickButtonTarget)

/**
 * Resolves a Quick tile against a catalog snapshot — a `product` button resolves by SKU, a
 * `category` button by id. Deleted/hidden targets the server already filters out, but the local
 * cache can still lag a moment after a sync, so this stays defensive.
 */
internal fun resolveQuickButton(button: QuickButton, snapshot: CatalogSnapshot): QuickButtonTarget =
    when (button.type) {
        QuickButtonType.PRODUCT -> {
            val product = button.sku?.let { sku -> snapshot.products.firstOrNull { it.sku == sku } }
            if (product != null) QuickButtonTarget.OpenProduct(product.id) else QuickButtonTarget.Unavailable
        }
        QuickButtonType.CATEGORY -> {
            val categoryId = button.categoryId
            if (categoryId != null && snapshot.categories.any { it.id == categoryId }) {
                QuickButtonTarget.OpenCategory(categoryId)
            } else {
                QuickButtonTarget.Unavailable
            }
        }
    }

private data class UiStatus(
    val syncing: Boolean,
    val searching: Boolean,
    val error: String?,
    val barcodeFlash: String?,
)

/** Immutable view of everything the catalog list screen needs to render in a single render pass. */
data class CatalogUiState(
    val query: String = "",
    val selectedCategoryId: String? = null,
    val categories: List<Category> = emptyList(),
    val products: List<Product> = emptyList(),
    val isSyncing: Boolean = false,
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
    val barcodeFlashMessage: String? = null,
    val isInitialLoad: Boolean = false,
)

private fun BarcodeMatch.toCartLine(): CartLine = CartLine(
    productId = product.id,
    variantId = variant?.id,
    name = product.name,
    variantLabel = variant?.displayLabel,
    sku = variant?.sku ?: product.sku,
    upc = variant?.upc ?: product.upc,
    unitOfSale = product.unitOfSale,
    unitPriceCents = variant?.priceCents ?: product.priceCents,
    quantity = qtyCount,
    isTaxable = product.isTaxable,
)
