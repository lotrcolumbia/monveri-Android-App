package co.monveri.register.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.repository.BarcodeMatch
import co.monveri.register.data.repository.CartLine
import co.monveri.register.data.repository.CartRepository
import co.monveri.register.data.repository.CatalogRepository
import co.monveri.register.data.repository.Category
import co.monveri.register.data.repository.Product
import co.monveri.register.network.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val searchResults = MutableStateFlow<List<Product>>(emptyList())
    private val isSyncing = MutableStateFlow(false)
    private val isSearching = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val barcodeFlash = MutableStateFlow<String?>(null)

    private var searchJob: Job? = null

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

    init {
        refresh()
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
    unitPriceCents = variant?.priceCents ?: product.priceCents,
    quantity = qtyCount,
    isTaxable = product.isTaxable,
)
