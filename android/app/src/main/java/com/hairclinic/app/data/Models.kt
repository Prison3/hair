package com.hairclinic.app.data

data class TokenOut(val access_token: String, val token_type: String = "bearer")

data class LoginIn(val username: String, val password: String)

data class MeOut(val id: Int, val username: String)

data class AccountUpdateIn(
    val current_password: String,
    val username: String? = null,
    val new_password: String? = null,
)

data class AccountUpdateOut(
    val id: Int,
    val username: String,
    val access_token: String,
    val token_type: String = "bearer",
)

data class Customer(
    val id: Int? = null,
    val name: String,
    val phone: String,
    val gender: String = "",
    val birthday: String? = null,
    val notes: String = "",
    val created_at: String? = null,
    val last_visited_at: String? = null,
    val visit_count: Int = 0,
)

data class CustomerVisit(
    val id: Int? = null,
    val customer_id: Int? = null,
    val visited_at: String,
    val content: String = "",
    val created_at: String? = null,
) {
    fun timeText(): String = formatVisitTime(visited_at)
}

fun formatVisitTime(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return raw.replace('T', ' ').replace('Z', ' ').trim().take(16)
}

data class Project(
    val id: Int? = null,
    val name: String,
    val description: String = "",
    val price: Double,
    val graft_count: Int = 0,
    val unit: String? = "个",
    val active: Boolean = true,
    val created_at: String? = null,
    val stock_qty: Int = 0,
    val cost_price: Double = 0.0,
) {
    fun unitLabel(): String {
        val raw = unit?.trim().orEmpty()
        return when {
            raw.isBlank() || raw == "单位" -> "次"
            else -> raw
        }
    }

    fun specText(): String = "$graft_count ${unitLabel()}"

    fun isPhysical(): Boolean = unitLabel() in setOf("支", "个", "盒")

    fun stockText(): String = "库存 $stock_qty ${unitLabel()}"

    fun costText(): String {
        val price = if (cost_price % 1.0 == 0.0) cost_price.toLong().toString() else "%.2f".format(cost_price)
        return "进货价 ¥$price"
    }
}

data class StockMoveIn(
    val project_id: Int,
    val quantity: Int,
    val unit_cost: Double = 0.0,
    val moved_at: String? = null,
    val remark: String = "",
)

data class StockMovement(
    val id: Int,
    val project_id: Int,
    val project_name: String,
    val kind: String,
    val quantity: Int,
    val unit_cost: Double = 0.0,
    val remark: String = "",
    val moved_at: String? = null,
    val created_at: String,
) {
    fun kindLabel(): String = if (kind == "IN") "入库" else "出货"

    fun timeText(): String = formatVisitTime(moved_at ?: created_at).take(10)
}

data class OrderItemIn(val project_id: Int, val quantity: Int = 1)

data class OrderCreate(
    val customer_id: Int,
    val items: List<OrderItemIn>,
    val remark: String = "",
)

data class OrderItem(
    val id: Int,
    val project_id: Int,
    val project_name: String,
    val unit_price: Double,
    val quantity: Int,
)

data class Order(
    val id: Int,
    val order_no: String,
    val customer_id: Int,
    val customer_name: String? = null,
    val customer_phone: String? = null,
    val total_amount: Double,
    val status: String,
    val remark: String = "",
    val created_at: String,
    val items: List<OrderItem> = emptyList(),
)

data class OrderStatusUpdate(val status: String)

data class AppReleaseInfo(
    val version_code: Int = 0,
    val version_name: String = "",
    val download_url: String = "",
    val size_bytes: Long = 0,
    val filename: String = "",
    val updated_at: String = "",
)
