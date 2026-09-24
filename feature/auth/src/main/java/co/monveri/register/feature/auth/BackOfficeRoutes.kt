package co.monveri.register.feature.auth

/** Compose Navigation routes owned by the Back Office slice of the auth feature. */
object BackOfficeRoutes {
    const val HOME = "auth/back-office"
    const val PRODUCTS = "auth/back-office/products"
    const val PRODUCT_EDIT = "auth/back-office/products/{productId}"
    const val EXPENSES = "auth/back-office/expenses"
    const val EXPENSE_ADD = "auth/back-office/expenses/add"
    const val EXPENSE_DETAIL = "auth/back-office/expenses/{expenseId}"

    fun productEditFor(productId: Long): String = "auth/back-office/products/$productId"
    fun expenseDetailFor(expenseId: Long): String = "auth/back-office/expenses/$expenseId"

    const val ARG_PRODUCT_ID: String = "productId"
    const val ARG_EXPENSE_ID: String = "expenseId"
}
