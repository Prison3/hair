package com.hairclinic.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.exifinterface.media.ExifInterface
import coil.ImageLoader
import com.hairclinic.app.BuildConfig
import com.hairclinic.app.R
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    fun normalizeRole(value: String?, fallback: String = ROLE_MANAGER): String {
        val role = value?.trim().orEmpty()
        return when (role) {
            ROLE_ADMIN, ROLE_MANAGER -> role
            else -> fallback
        }
    }

    fun isAdmin(context: Context): Boolean {
        val value = role(context).trim()
        if (value == ROLE_MANAGER) return false
        if (value == ROLE_ADMIN) return true
        // 旧会话可能没写入 role；管理员切到其他账号后绝不能再当成管理员
        return value.isBlank() && !isImpersonating(context)
    }

    fun roleLabel(context: Context): String =
        if (isAdmin(context)) "管理员" else "店长"

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
            .putString(KEY_ORIGIN_ROLE, normalizeRole(role(context), fallback = ROLE_ADMIN))
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
        if (isAdmin(context)) R.id.revenueFragment else R.id.customersFragment

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
            setOf(R.id.customersFragment, R.id.ordersFragment, R.id.meFragment)
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
        return destId in allowedNavIds(context) || destId in setOf(
            R.id.customerEditFragment,
            R.id.billingFragment,
        )
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

/** Token 失效时统一清会话并通知界面跳转登录。 */
object AuthExpired {
    private val handling = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var onExpired: (() -> Unit)? = null

    fun setHandler(handler: (() -> Unit)?) {
        onExpired = handler
    }

    fun notifyIfNeeded(context: Context) {
        if (!handling.compareAndSet(false, true)) return
        Session.clear(context.applicationContext)
        mainHandler.post {
            try {
                onExpired?.invoke()
            } finally {
                handling.set(false)
            }
        }
    }
}

object ApiClient {
    @Volatile private var api: ApiService? = null
    @Volatile private var boundUrl: String? = null
    @Volatile private var httpClient: OkHttpClient? = null
    @Volatile private var imageLoader: ImageLoader? = null

    fun reset() {
        api = null
        boundUrl = null
        httpClient = null
        imageLoader = null
    }

    fun photoUrl(context: Context, path: String): String {
        val base = Session.baseUrl(context)
        val tail = path.trim().removePrefix("/")
        return base + tail
    }

    fun httpClient(context: Context): OkHttpClient {
        val appContext = context.applicationContext
        val baseUrl = Session.baseUrl(appContext)
        httpClient?.let { if (boundUrl == baseUrl) return it }
        return buildClient(appContext).also { httpClient = it }
    }

    fun imageLoader(context: Context): ImageLoader {
        val appContext = context.applicationContext
        val baseUrl = Session.baseUrl(appContext)
        imageLoader?.let { if (boundUrl == baseUrl) return it }
        return ImageLoader.Builder(appContext)
            .okHttpClient(httpClient(appContext))
            .build()
            .also { imageLoader = it }
    }

    private fun buildClient(appContext: Context): OkHttpClient {
        val auth = Interceptor { chain ->
            val token = Session.token(appContext)
            val req = if (token.isNotBlank()) {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else chain.request()
            chain.proceed(req)
        }
        val unauthorized = Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code == 401 && !isAuthLoginRequest(request.url.encodedPath)) {
                AuthExpired.notifyIfNeeded(appContext)
            }
            response
        }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(auth)
            .addInterceptor(unauthorized)
            .addInterceptor(logging)
            .build()
    }

    private fun isAuthLoginRequest(path: String): Boolean {
        val p = path.trimEnd('/')
        return p.endsWith("/api/auth/login") || p.endsWith("/api/auth/token")
    }

    suspend fun uploadCustomerPhoto(
        context: Context,
        customerId: Int,
        kind: String,
        uri: android.net.Uri,
    ): CustomerPhoto {
        val appContext = context.applicationContext
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val ext = when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("heic") || mime.contains("heif") -> "heic"
            else -> "jpg"
        }
        val temp = File.createTempFile("upload_", ".$ext", appContext.cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalArgumentException("无法读取图片")
            val fileBody = temp.asRequestBody(mime.toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", "photo.$ext", fileBody)
            val kindBody = kind.toRequestBody("text/plain".toMediaTypeOrNull())
            val takenAtIso = readPhotoTakenAtUtcIso(temp)
            val takenBody = takenAtIso?.toRequestBody("text/plain".toMediaTypeOrNull())
            return get(appContext).uploadCustomerPhoto(customerId, kindBody, takenBody, filePart)
        } finally {
            temp.delete()
        }
    }

    /** 从 EXIF 读取拍摄时间，按上海时区转为 UTC ISO（yyyy-MM-dd'T'HH:mm:ss）。 */
    private fun readPhotoTakenAtUtcIso(file: File): String? {
        return try {
            val exif = ExifInterface(file.absolutePath)
            val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                ?: return null
            val local = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("Asia/Shanghai")
            }.parse(raw.trim()) ?: return null
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(local)
        } catch (_: Exception) {
            null
        }
    }

    fun get(context: Context): ApiService {
        val appContext = context.applicationContext
        val baseUrl = Session.baseUrl(appContext)
        api?.let { if (boundUrl == baseUrl) return it }
        synchronized(this) {
            api?.let { if (boundUrl == baseUrl) return it }
            val client = buildClient(appContext)
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(ApiService::class.java).also {
                api = it
                boundUrl = baseUrl
                httpClient = client
            }
        }
    }
}
