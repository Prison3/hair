package com.hairclinic.app.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginIn): TokenOut

    @GET("api/auth/me")
    suspend fun me(): MeOut

    @PATCH("api/auth/me")
    suspend fun updateAccount(@Body body: AccountUpdateIn): AccountUpdateOut

    @GET("api/customers")
    suspend fun listCustomers(@Query("q") q: String? = null): List<Customer>

    @POST("api/customers")
    suspend fun createCustomer(@Body body: Customer): Customer

    @PUT("api/customers/{id}")
    suspend fun updateCustomer(@Path("id") id: Int, @Body body: Customer): Customer

    @DELETE("api/customers/{id}")
    suspend fun deleteCustomer(@Path("id") id: Int)

    @GET("api/projects")
    suspend fun listProjects(@Query("active_only") activeOnly: Boolean = false): List<Project>

    @POST("api/projects")
    suspend fun createProject(@Body body: Project): Project

    @PUT("api/projects/{id}")
    suspend fun updateProject(@Path("id") id: Int, @Body body: Project): Project

    @DELETE("api/projects/{id}")
    suspend fun deactivateProject(@Path("id") id: Int): Project

    @GET("api/orders")
    suspend fun listOrders(@Query("status") status: String? = null): List<Order>

    @POST("api/orders")
    suspend fun createOrder(@Body body: OrderCreate): Order

    @PATCH("api/orders/{id}/status")
    suspend fun updateOrderStatus(@Path("id") id: Int, @Body body: OrderStatusUpdate): Order

    @GET("api/app/info")
    suspend fun appInfo(): AppReleaseInfo
}
