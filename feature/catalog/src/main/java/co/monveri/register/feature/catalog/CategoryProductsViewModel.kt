package co.monveri.register.feature.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.monveri.register.data.repository.Category
import co.monveri.register.data.repository.CatalogRepository
import co.monveri.register.data.repository.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Drives the category drill-in destination pushed from either the Categories tab or a
 * `category`-type Quick button. Mirrors iOS's `CategoryProductsView`: tapping ANY category —
 * leaf or parent — shows every product in that category **and all of its descendants**, not just
 * items directly assigned to it.
 */
@HiltViewModel
class CategoryProductsViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val categoryId: String = savedStateHandle.get<String>(CatalogRoutes.ARG_CATEGORY_ID)
        ?: error("Missing ${CatalogRoutes.ARG_CATEGORY_ID} argument")

    val categoryName: String
        get() = catalog.current().categories.firstOrNull { it.id == categoryId }?.name ?: ""

    val products: StateFlow<List<Product>> = catalog.observeCatalog()
        .map { snapshot ->
            val ids = descendantCategoryIds(categoryId, snapshot.categories)
            snapshot.products.filter { it.categoryId != null && it.categoryId in ids }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS: Long = 5_000
    }
}

/** BFS over `parentId` links to collect [rootId] plus every category nested beneath it. */
internal fun descendantCategoryIds(rootId: String, categories: List<Category>): Set<String> {
    val childrenByParent = categories.filter { it.parentId != null }.groupBy { it.parentId }
    val result = mutableSetOf(rootId)
    val queue = ArrayDeque(listOf(rootId))
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (child in childrenByParent[current].orEmpty()) {
            if (result.add(child.id)) {
                queue.addLast(child.id)
            }
        }
    }
    return result
}
