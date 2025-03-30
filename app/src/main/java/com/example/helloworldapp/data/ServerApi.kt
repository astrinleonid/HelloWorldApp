package com.example.helloworldapp.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
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
     * Send a GET request to the server
     *
     * @param route The API route (e.g. "/checkConnection")
     * @param params Map of query parameters
     * @param callback Callback to handle the response
     */
    fun get(route: String, params: Map<String, String> = emptyMap(), callback: (ApiResult<String>) -> Unit) {
        if (!isOnline()) {
            callback(ApiResult.Error("Device is offline", -1))
            return
        }

        // Build URL with query parameters
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

        val url = urlBuilder.toString()
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
     * Send a POST request to the server
     *
     * @param route The API route (e.g. "/upload")
     * @param params Map of form parameters
     * @param files Map of files to upload (fieldName to file path)
     * @param callback Callback to handle the response
     */
    fun post(
        route: String,
        params: Map<String, String> = emptyMap(),
        files: Map<String, FileInfo> = emptyMap(),
        callback: (ApiResult<String>) -> Unit
    ) {
        if (!isOnline()) {
            callback(ApiResult.Error("Device is offline", -1))
            return
        }

        val url = "${AppConfig.serverIP}$route"
        Log.d("ServerApi", "POST request to $url")

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

        // Execute request in background
        executeRequest(request, callback)
    }

    /**
     * Send POST request with raw JSON body
     *
     * @param route The API route
     * @param jsonBody The JSON body as an object (will be serialized to JSON)
     * @param callback Callback to handle the response
     */
    fun postJson(route: String, jsonBody: Any, callback: (ApiResult<String>) -> Unit) {
        if (!isOnline()) {
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
     * Check if the device has internet connectivity
     */
    fun isOnline(): Boolean {
        val context = try {
            // Get application context
            val applicationClass = Class.forName("com.example.helloworldapp.TeproApplication")
            val instanceField = applicationClass.getDeclaredField("instance")
            val applicationInstance = instanceField.get(null) as Context
            applicationInstance
        } catch (e: Exception) {
            Log.e("ServerApi", "Error getting application context, using local context", e)
            return true // Default to true to allow the attempt
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
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