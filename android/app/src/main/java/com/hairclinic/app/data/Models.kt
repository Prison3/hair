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
)

data class Project(
    val id: Int? = null,
    val name: String,
    val description: String = "",
    val price: Double,
    val graft_count: Int = 0,
    val active: Boolean = true,
    val created_at: String? = null,
)

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
