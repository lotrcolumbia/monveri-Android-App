package co.monveri.register.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape of `GET /config/quick-buttons.php` — a flat, already-filtered list (the backend
 * drops buttons pointing at hidden/deleted products or categories, and resolves the
 * per-register-vs-global precedence server-side). `button_type` is `"product"` or `"category"`;
 * `color` is a literal Bootstrap class name (e.g. `btn-success`) set by the admin per row, mapped
 * to a color at render time — never inferred from `button_type`.
 */
@Serializable
data class QuickButtonDto(
    @SerialName("id") val id: Long,
    @SerialName("label") val label: String,
    @SerialName("sku") val sku: String? = null,
    @SerialName("button_type") val buttonType: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("color") val color: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
)
