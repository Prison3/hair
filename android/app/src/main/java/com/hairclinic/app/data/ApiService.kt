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

    @GET("api/auth/staff")
    suspend fun listStaff(): List<Staff>

    @POST("api/auth/staff")
    suspend fun createStaff(@Body body: StaffCreate): Staff

    @PATCH("api/auth/staff/{id}")
    suspend fun updateStaff(@Path("id") id: Int, @Body body: StaffUpdate): Staff

    @DELETE("api/auth/staff/{id}")
    suspend fun deleteStaff(@Path("id") id: Int)

    @POST("api/auth/staff/{id}/login")
    suspend fun loginAsStaff(@Path("id") id: Int): TokenOut

    @GET("api/customers")
    suspend fun listCustomers(@Query("q") q: String? = null): List<Customer>

    @POST("api/customers")
    suspend fun createCustomer(@Body body: Customer): Customer

    @PUT("api/customers/{id}")
    suspend fun updateCustomer(@Path("id") id: Int, @Body body: Customer): Customer

    @DELETE("api/customers/{id}")
    suspend fun deleteCustomer(@Path("id") id: Int)

    @GET("api/customers/{id}/visits")
    suspend fun listVisits(@Path("id") id: Int): List<CustomerVisit>

    @POST("api/customers/{id}/visits")
    suspend fun createVisit(@Path("id") id: Int, @Body body: CustomerVisit): CustomerVisit

    @PUT("api/customers/{id}/visits/{visitId}")
    suspend fun updateVisit(
        @Path("id") id: Int,
        @Path("visitId") visitId: Int,
        @Body body: CustomerVisit,
    ): CustomerVisit

    @DELETE("api/customers/{id}/visits/{visitId}")
    suspend fun deleteVisit(@Path("id") id: Int, @Path("visitId") visitId: Int)

    @GET("api/projects")
    suspend fun listProjects(@Query("active_only") activeOnly: Boolean = false): List<Project>

    @GET("api/projects/{id}")
    suspend fun getProject(@Path("id") id: Int): Project

    @POST("api/projects")
    suspend fun createProject(@Body body: Project): Project

    @PUT("api/projects/{id}")
    suspend fun updateProject(@Path("id") id: Int, @Body body: Project): Project

    @DELETE("api/projects/{id}")
    suspend fun deleteProject(@Path("id") id: Int)

    @GET("api/inventory")
    suspend fun listInventory(@Query("q") q: String? = null): List<StockItem>

    @POST("api/inventory")
    suspend fun createStockItem(@Body body: StockItemWrite): StockItem

    @PUT("api/inventory/{id}")
    suspend fun updateStockItem(@Path("id") id: Int, @Body body: StockItemWrite): StockItem

    @DELETE("api/inventory/{id}")
    suspend fun deleteStockItem(@Path("id") id: Int)

    @GET("api/inventory/{id}")
    suspend fun getStockItem(@Path("id") id: Int): StockItem

    @GET("api/inventory/movements")
    suspend fun listStockMovements(
        @Query("item_id") itemId: Int? = null,
        @Query("kind") kind: String? = null,
        @Query("q") q: String? = null,
        @Query("limit") limit: Int? = null,
    ): List<StockMovement>

    @DELETE("api/inventory/movements/{id}")
    suspend fun deleteStockMovement(@Path("id") id: Int)

    @POST("api/inventory/in")
    suspend fun stockIn(@Body body: StockInRequest): StockMovement

    @POST("api/inventory/out")
    suspend fun stockOut(@Body body: StockOutRequest): StockMovement

    @GET("api/orders")
    suspend fun listOrders(@Query("status") status: String? = null): List<Order>

    @POST("api/orders")
    suspend fun createOrder(@Body body: OrderCreate): Order

    @PATCH("api/orders/{id}/status")
    suspend fun updateOrderStatus(@Path("id") id: Int, @Body body: OrderStatusUpdate): Order

    @GET("api/app/info")
    suspend fun appInfo(): AppReleaseInfo
}
