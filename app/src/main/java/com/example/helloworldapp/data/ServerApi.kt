package com.example.helloworldapp.data

import AppConfig
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Looper
import android.telephony.mbms.FileInfo
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * ServerApi - Utility class for server interactions
 *
 * All server communication should go through this class to maintain
 * a clean separation of concerns.
 */
object ServerApi {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Result class to wrap server responses
    sealed class ApiResult<out T> {
        data class Success<T>(val data: T) : ApiResult<T>()
        data class Error(val message: String, val code: Int = 0, val exception: Exception? = null) : ApiResult<Nothing>()
    }

    /**
     * Send a GET request to the server (asynchronous)
     */
    fun get(route: String, params: Map<String, String> = emptyMap(), context: Context? = null, callback: (ApiResult<String>) -> Unit) {
        if (!isOnline(context)) {
            callback(ApiResult.Error("Device is offline", -1))
            return
        }

        // Build URL with query parameters
        val url = buildUrl(route, params)
        Log.d("ServerApi", "GET request to $url")

        // Create request
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        // Execute request in background
        executeRequest(request, callback)
    }

    /**
     * Send a GET request to the server (synchronous)
     * Should only be called from a background thread
     */
    fun getSync(route: String, params: Map<String, String> = emptyMap(), context: Context? = null): ApiResult<String> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e("ServerApi", "getSync called on main thread! This will block the UI.")
        }

        if (!isOnline(context)) {
            return ApiResult.Error("Device is offline", -1)
        }

        // Build URL with query parameters
        val url = buildUrl(route, params)
        Log.d("ServerApi", "Sync GET request to $url")

        // Create request
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        // Execute request synchronously
        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    Log.d("ServerApi", "Sync request successful: ${response.code}")
                    ApiResult.Success(responseBody)
                } else {
                    Log.e("ServerApi", "Sync request failed: ${response.code}, ${response.message}")
                    ApiResult.Error(response.message, response.code)
                }
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "Exception in sync request", e)
            ApiResult.Error("Network error: ${e.message}", exception = e)
        }
    }



    /**
     * Send a POST request to the server (synchronous)
     * Should only be called from a background thread
     */
    fun postSync(
        route: String,
        params: Map<String, String> = emptyMap(),
        files: Map<String, FileInfo> = emptyMap(),
        context: Context? = null
    ): ApiResult<String> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e("ServerApi", "postSync called on main thread! This will block the UI.")
        }

        if (!isOnline(context)) {
            return ApiResult.Error("Device is offline", -1)
        }

        val url = "${AppConfig.serverIP}$route"
        Log.d("ServerApi", "Sync POST request to $url")

        // Build multipart request
        val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

        // Add form parameters
        params.forEach { (key, value) ->
            requestBodyBuilder.addFormDataPart(key, value)
        }

        // Add files
        files.forEach { (fieldName, fileInfo) ->
            val file = File(fileInfo.path)
            if (file.exists()) {
                val mediaType = fileInfo.mimeType.toMediaTypeOrNull()
                val requestBody = file.asRequestBody(mediaType)
                requestBodyBuilder.addFormDataPart(fieldName, fileInfo.name, requestBody)
            } else {
                Log.e("ServerApi", "File not found: ${fileInfo.path}")
            }
        }

        // Create request
        val request = Request.Builder()
            .url(url)
            .post(requestBodyBuilder.build())
            .build()

        // Execute request synchronously
        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    Log.d("ServerApi", "Sync POST request successful: ${response.code}")
                    ApiResult.Success(responseBody)
                } else {
                    Log.e("ServerApi", "Sync POST request failed: ${response.code}, ${response.message}")
                    ApiResult.Error(response.message, response.code)
                }
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "Exception in sync POST request", e)
            ApiResult.Error("Network error: ${e.message}", exception = e)
        }
    }

    /**
     * Send POST request with raw JSON body (asynchronous)
     */
    fun postJson(route: String, jsonBody: Any, context: Context? = null, callback: (ApiResult<String>) -> Unit) {
        if (!isOnline(context)) {
            callback(ApiResult.Error("Device is offline", -1))
            return
        }

        val url = "${AppConfig.serverIP}$route"
        Log.d("ServerApi", "POST JSON request to $url")

        val jsonString = gson.toJson(jsonBody)
        val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        executeRequest(request, callback)
    }

    /**
     * Send POST request with raw JSON body (synchronous)
     * Should only be called from a background thread
     */
    fun postJsonSync(route: String, jsonBody: Any, context: Context? = null): ApiResult<String> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e("ServerApi", "postJsonSync called on main thread! This will block the UI.")
        }

        if (!isOnline(context)) {
            return ApiResult.Error("Device is offline", -1)
        }

        val url = "${AppConfig.serverIP}$route"
        Log.d("ServerApi", "Sync POST JSON request to $url")

        val jsonString = gson.toJson(jsonBody)
        val requestBody = jsonString.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    Log.d("ServerApi", "Sync POST JSON request successful: ${response.code}")
                    ApiResult.Success(responseBody)
                } else {
                    Log.e("ServerApi", "Sync POST JSON request failed: ${response.code}, ${response.message}")
                    ApiResult.Error(response.message, response.code)
                }
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "Exception in sync POST JSON request", e)
            ApiResult.Error("Network error: ${e.message}", exception = e)
        }
    }

    /**
     * Download a file from the server (asynchronous)
     */
    fun downloadFile(route: String, params: Map<String, String> = emptyMap(), context: Context? = null, callback: (ByteArray?) -> Unit) {
        if (!isOnline(context)) {
            callback(null)
            return
        }

        val url = buildUrl(route, params)
        Log.d("ServerApi", "Downloading file from: $url")

        scope.launch {
            try {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("ServerApi", "Failed to download file: ${response.code}")
                        withContext(Dispatchers.Main) {
                            callback(null)
                        }
                        return@use
                    }

                    val bytes = response.body?.bytes()
                    if (bytes == null || bytes.isEmpty()) {
                        Log.e("ServerApi", "Downloaded file is empty")
                        withContext(Dispatchers.Main) {
                            callback(null)
                        }
                        return@use
                    }

                    Log.d("ServerApi", "Downloaded ${bytes.size} bytes")
                    withContext(Dispatchers.Main) {
                        callback(bytes)
                    }
                }
            } catch (e: Exception) {
                Log.e("ServerApi", "Error downloading file", e)
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }

    /**
     * Download a file from the server (synchronous)
     * Should only be called from a background thread
     */
    fun downloadFileSync(route: String, params: Map<String, String> = emptyMap(), context: Context? = null): ByteArray? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.e("ServerApi", "downloadFileSync called on main thread! This will block the UI.")
        }

        if (!isOnline(context)) {
            return null
        }

        val url = buildUrl(route, params)
        Log.d("ServerApi", "Downloading file synchronously from: $url")

        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ServerApi", "Failed to download file: ${response.code}")
                    return null
                }

                val bytes = response.body?.bytes()
                if (bytes == null || bytes.isEmpty()) {
                    Log.e("ServerApi", "Downloaded file is empty")
                    return null
                }

                Log.d("ServerApi", "Downloaded ${bytes.size} bytes")
                bytes
            }
        } catch (e: Exception) {
            Log.e("ServerApi", "Error downloading file", e)
            null
        }
    }

    /**
     * Execute a network request safely in the background
     */
    private fun executeRequest(request: Request, callback: (ApiResult<String>) -> Unit) {
        scope.launch {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        Log.d("ServerApi", "Request successful: ${response.code}")
                        withContext(Dispatchers.Main) {
                            callback(ApiResult.Success(responseBody))
                        }
                    } else {
                        Log.e("ServerApi", "Request failed: ${response.code}, ${response.message}")
                        withContext(Dispatchers.Main) {
                            callback(ApiResult.Error(response.message, response.code))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ServerApi", "Request exception", e)
                withContext(Dispatchers.Main) {
                    callback(ApiResult.Error("Network error: ${e.message}", exception = e))
                }
            }
        }
    }

    /**
     * Build a URL with query parameters
     */
    private fun buildUrl(route: String, params: Map<String, String>): String {
        val urlBuilder = StringBuilder()
        urlBuilder.append(AppConfig.serverIP).append(route)

        if (params.isNotEmpty()) {
            urlBuilder.append("?")
            params.entries.forEachIndexed { index, entry ->
                if (index > 0) urlBuilder.append("&")
                urlBuilder.append(URLEncoder.encode(entry.key, "UTF-8"))
                    .append("=")
                    .append(URLEncoder.encode(entry.value, "UTF-8"))
            }
        }

        return urlBuilder.toString()
    }

    /**
     * Check if the device has internet connectivity
     */
    fun isOnline(context: Context? = null): Boolean {
        // Get context from parameter or try alternative methods
        val appContext = context ?: try {
            // Try to get context using reflection
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplicationMethod = activityThreadClass.getDeclaredMethod("currentApplication")
            val currentApplication = currentApplicationMethod.invoke(null) as? Context
            currentApplication
        } catch (e: Exception) {
            try {
                // Alternative approach to get application context
                val application = Class.forName("android.app.AppGlobals")
                    .getDeclaredMethod("getInitialApplication")
                    .invoke(null) as? Context
                application
            } catch (e: Exception) {
                Log.e("ServerApi", "Error getting application context", e)
                null
            }
        }

        // If no context is available, default to assuming online
        if (appContext == null) {
            Log.w("ServerApi", "No context available to check network state, assuming online")
            return true
        }

        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val networkCapabilities = connectivityManager.activeNetwork ?: return false
            val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
            return when {
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            return networkInfo != null && networkInfo.isConnected
        }
    }

    /**
     * Represents a file to be uploaded
     */
    data class FileInfo(
        val path: String,
        val name: String,
        val mimeType: String = "application/octet-stream"
    )
}