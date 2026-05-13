package co.monveri.register.feature.catalog

/**
 * Compose Navigation routes owned by the catalog feature. Centralised so :app's NavGraph and the
 * feature's own internal navigation stay in sync.
 */
object CatalogRoutes {
    const val LIST = "catalog/list"
    const val DETAIL = "catalog/detail/{productId}"

    fun detailFor(productId: Long): String = "catalog/detail/$productId"

    const val ARG_PRODUCT_ID: String = "productId"
}
