package com.hairclinic.app.data

data class TokenOut(
    val access_token: String,
    val token_type: String = "bearer",
    val username: String = "",
    val role: String = "admin",
    val role_label: String = "管理员",
)

data class LoginIn(val username: String, val password: String)

data class MeOut(
    val id: Int,
    val username: String,
    val role: String = "admin",
    val role_label: String = "管理员",
)

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
    val role: String = "admin",
    val role_label: String = "管理员",
)

data class Staff(
    val id: Int,
    val username: String,
    val role: String = "manager",
    val role_label: String = "店长",
    val created_at: String? = null,
)

data class StaffCreate(
    val username: String,
    val password: String,
    val role: String = "manager",
)

data class StaffUpdate(
    val username: String? = null,
    val password: String? = null,
    val role: String? = null,
)

data class Customer(
    val id: Int? = null,
    val name: String,
    val phone: String,
    val gender: String = "",
    val birthday: String? = null,
    val wechat: String = "",
    val address: String = "",
    val intention: String = "",
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

data class ProjectMedicine(
    val id: Int? = null,
    val item_id: Int,
    val item_name: String = "",
    val quantity: Int = 1,
    val unit: String? = "个",
) {
    fun doseText(): String = "$item_name ${quantity}${stockUnitLabel(unit)}"
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
    val medicines: List<ProjectMedicine> = emptyList(),
) {
    fun medicineText(): String = medicines.joinToString(" · ") { it.doseText() }
}

fun stockUnitLabel(raw: String?): String {
    val value = raw?.trim().orEmpty()
    return if (value.isBlank() || value == "单位") "个" else value
}

data class StockItem(
    val id: Int,
    val name: String,
    val spec: String? = "",
    val unit: String? = "个",
    val stock_qty: Int = 0,
    val cost_price: Double = 0.0,
    val created_at: String? = null,
) {
    fun unitLabel(): String = stockUnitLabel(unit)

    fun specText(): String = spec?.trim().orEmpty().ifBlank { "—" }

    fun stockText(): String = "库存 $stock_qty ${unitLabel()}"

    fun costText(): String {
        val price = if (cost_price % 1.0 == 0.0) cost_price.toLong().toString() else "%.2f".format(cost_price)
        return "进货价 ¥$price"
    }
}

data class StockItemWrite(
    val name: String,
)

data class StockInRequest(
    val item_id: Int? = null,
    val name: String = "",
    val spec: String = "",
    val quantity: Int,
    val unit: String = "个",
    val unit_cost: Double,
    val moved_at: String? = null,
)

data class StockOutRequest(
    val item_id: Int,
    val quantity: Int,
    val remark: String = "",
)

data class StockMovement(
    val id: Int,
    val item_id: Int,
    val item_name: String,
    val kind: String,
    val quantity: Int,
    val unit: String? = "个",
    val unit_cost: Double = 0.0,
    val remark: String? = "",
    val inbound_no: String? = "",
    val moved_at: String? = null,
    val created_at: String,
) {
    fun kindLabel(): String = if (kind == "IN") "入库" else "出货"

    fun timeText(): String = formatVisitTime(moved_at ?: created_at).take(10)

    fun inboundNoText(): String {
        if (!inbound_no.isNullOrBlank()) return inbound_no
        return created_at.replace('T', ' ').replace('Z', ' ').trim().take(19)
    }

    fun unitLabel(): String = stockUnitLabel(unit)

    fun qtyText(): String = "$quantity${unitLabel()}"
}

data class OrderItemIn(val project_id: Int, val quantity: Int = 1)

data class OrderCreate(
    val customer_id: Int,
    val items: List<OrderItemIn>,
    val deal_price: Double,
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

data class RevenueDay(
    val date: String,
    val day: Int,
    val revenue: Double,
    val order_count: Int,
    val cost: Double,
    val inbound_count: Int,
    val profit: Double,
)

data class RevenueSummary(
    val year: Int,
    val month: Int,
    val revenue: Double,
    val order_count: Int,
    val cost: Double,
    val inbound_count: Int,
    val profit: Double,
    val days: List<RevenueDay> = emptyList(),
)

data class AppReleaseInfo(
    val version_code: Int = 0,
    val version_name: String = "",
    val download_url: String = "",
    val size_bytes: Long = 0,
    val filename: String = "",
    val updated_at: String = "",
)
