package co.monveri.register.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.repository.CatalogRepository
import co.monveri.register.data.repository.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Back Office's Products list. Reuses [CatalogRepository] — the same cache the cashier-facing
 * Catalog tab reads — rather than a dedicated admin listing endpoint, because none exists yet
 * (iOS's own Back Office does the same; a comment in its source describes a `sync.php
 * include_inactive` parameter that was never actually implemented server-side). That means, for
 * now, an inactive or mobile-POS-hidden product won't show up here to be re-activated — a real
 * gap, shared with iOS, not something this slice invented.
 */
@HiltViewModel
class BackOfficeProductListViewModel @Inject constructor(
    private val catalog: CatalogRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<BackOfficeProductListUiState> = combine(
        catalog.observeCatalog(),
        query,
    ) { snapshot, q ->
        val products = if (q.isBlank()) {
            snapshot.products
        } else {
            snapshot.products.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.sku?.contains(q, ignoreCase = true) == true ||
                    it.upc?.contains(q, ignoreCase = true) == true
            }
        }
        BackOfficeProductListUiState(query = q, products = products, isEmpty = snapshot.products.isEmpty())
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = BackOfficeProductListUiState(),
    )

    init {
        // Products tab already synced this same cache; force a sync only if we're the first to
        // ask (e.g. an admin opens Back Office before ever visiting the catalog this session).
        if (catalog.current().products.isEmpty()) {
            viewModelScope.launch { catalog.sync() }
        }
    }

    fun onQueryChanged(value: String) {
        query.value = value
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}

data class BackOfficeProductListUiState(
    val query: String = "",
    val products: List<Product> = emptyList(),
    val isEmpty: Boolean = false,
)
