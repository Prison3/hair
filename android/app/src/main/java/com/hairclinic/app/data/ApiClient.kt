package com.hairclinic.app.data

import android.content.Context
import com.hairclinic.app.BuildConfig
import com.hairclinic.app.R
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object Session {
    private const val PREF = "hair_clinic"
    private const val KEY_TOKEN = "token"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_USERNAME = "username"
    private const val KEY_ROLE = "role"
    private const val KEY_ORIGIN_TOKEN = "origin_token"
    private const val KEY_ORIGIN_USERNAME = "origin_username"
    private const val KEY_ORIGIN_ROLE = "origin_role"
    const val ROLE_ADMIN = "admin"
    const val ROLE_MANAGER = "manager"

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun token(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_TOKEN, "") ?: ""

    fun saveUsername(context: Context, username: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USERNAME, username)
            .apply()
    }

    fun username(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_USERNAME, "") ?: ""

    fun saveRole(context: Context, role: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun role(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_ROLE, "") ?: ""

    fun isAdmin(context: Context): Boolean {
        val value = role(context)
        return value.isBlank() || value == ROLE_ADMIN
    }

    fun saveAuth(context: Context, token: String, username: String, role: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putString(KEY_ROLE, role)
            .apply()
    }

    fun isImpersonating(context: Context): Boolean =
        originToken(context).isNotBlank()

    fun originUsername(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_ORIGIN_USERNAME, "") ?: ""

    private fun originToken(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_ORIGIN_TOKEN, "") ?: ""

    fun rememberOriginIfNeeded(context: Context) {
        if (isImpersonating(context)) return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ORIGIN_TOKEN, token(context))
            .putString(KEY_ORIGIN_USERNAME, username(context))
            .putString(KEY_ORIGIN_ROLE, role(context))
            .apply()
    }

    fun restoreOrigin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val origin = prefs.getString(KEY_ORIGIN_TOKEN, "") ?: ""
        if (origin.isBlank()) return false
        val name = prefs.getString(KEY_ORIGIN_USERNAME, "") ?: ""
        val originRole = prefs.getString(KEY_ORIGIN_ROLE, ROLE_ADMIN) ?: ROLE_ADMIN
        prefs.edit()
            .putString(KEY_TOKEN, origin)
            .putString(KEY_USERNAME, name)
            .putString(KEY_ROLE, originRole)
            .remove(KEY_ORIGIN_TOKEN)
            .remove(KEY_ORIGIN_USERNAME)
            .remove(KEY_ORIGIN_ROLE)
            .apply()
        return true
    }

    fun homeDestination(context: Context): Int =
        if (isAdmin(context)) R.id.projectsFragment else R.id.customersFragment

    fun allowedNavIds(context: Context): Set<Int> =
        if (isAdmin(context)) {
            setOf(
                R.id.projectsFragment,
                R.id.inventoryFragment,
                R.id.revenueFragment,
                R.id.staffFragment,
                R.id.meFragment,
            )
        } else {
            setOf(R.id.customersFragment, R.id.billingFragment, R.id.ordersFragment, R.id.meFragment)
        }

    fun isAllowedDestination(context: Context, destId: Int): Boolean {
        if (destId == R.id.loginFragment) return true
        if (isAdmin(context)) {
            return destId in allowedNavIds(context) || destId in setOf(
                R.id.projectEditFragment,
                R.id.productListFragment,
                R.id.productEditFragment,
                R.id.inboundListFragment,
                R.id.inboundFragment,
                R.id.stockFragment,
                R.id.staffEditFragment,
            )
        }
        return destId in allowedNavIds(context) || destId == R.id.customerEditFragment
    }

    fun saveBaseUrl(context: Context, url: String) {
        val normalized = normalizeBaseUrl(url)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, normalized)
            .apply()
        ApiClient.reset()
    }

    fun baseUrl(context: Context): String {
        val saved = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, null)
        return normalizeBaseUrl(saved ?: BuildConfig.BASE_URL)
    }

    fun displayBaseUrl(context: Context): String = stripScheme(baseUrl(context))

    fun stripScheme(url: String): String {
        var u = url.trim()
        if (u.startsWith("https://")) u = u.removePrefix("https://")
        else if (u.startsWith("http://")) u = u.removePrefix("http://")
        return u.trimEnd('/')
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val base = prefs.getString(KEY_BASE_URL, null)
        prefs.edit().clear().apply()
        if (base != null) {
            prefs.edit().putString(KEY_BASE_URL, base).apply()
        }
        ApiClient.reset()
    }

    fun normalizeBaseUrl(url: String): String {
        var u = url.trim()
        if (u.isEmpty()) u = BuildConfig.BASE_URL
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://$u"
        }
        if (!u.endsWith("/")) u += "/"
        return u
    }
}

object ApiClient {
    @Volatile private var api: ApiService? = null
    @Volatile private var boundUrl: String? = null

    fun reset() {
        api = null
        boundUrl = null
    }

    fun get(context: Context): ApiService {
        val appContext = context.applicationContext
        val baseUrl = Session.baseUrl(appContext)
        api?.let { if (boundUrl == baseUrl) return it }
        synchronized(this) {
            api?.let { if (boundUrl == baseUrl) return it }
            val auth = Interceptor { chain ->
                val token = Session.token(appContext)
                val req = if (token.isNotBlank()) {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else chain.request()
                chain.proceed(req)
            }
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(auth)
                .addInterceptor(logging)
                .build()
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(ApiService::class.java).also {
                api = it
                boundUrl = baseUrl
            }
        }
    }
}
