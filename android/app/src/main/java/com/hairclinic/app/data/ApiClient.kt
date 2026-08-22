package com.hairclinic.app.data

import android.content.Context
import com.hairclinic.app.BuildConfig
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

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun token(context: Context): String =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_TOKEN, "") ?: ""

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
