import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

// NetworkQualityChecker.kt
class NetworkQualityChecker(private val context: Context) {
    companion object {
        private const val PING_URL = "/checkConnection"  // Endpoint on your server for ping test
        private const val CONNECTION_TIMEOUT_MS = 5000   // 5 seconds timeout for connection
        private const val READ_TIMEOUT_MS = 5000         // 5 seconds timeout for reading
        private const val MIN_SUCCESSFUL_PINGS = 3       // Minimum successful pings required
        private const val MAX_ALLOWED_LATENCY_MS = 500   // Maximum acceptable average latency
        private const val MAX_PING_ATTEMPTS = 5          // Number of ping attempts
        private const val MIN_SUCCESSFUL_RATE = 0.6      // Minimum success rate (60%)
    }

    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Checks if the server connection is of sufficient quality
     * @param serverUrl Base server URL
     * @param callback Callback with boolean indicating if connection quality is sufficient for online mode
     */
    fun checkConnectionQuality(serverUrl: String, callback: (Boolean) -> Unit) {
        if (!isNetworkAvailable()) {
            Log.d("NetworkQualityChecker", "No network available, using offline mode")
            callback(false)
            return
        }

        // Run ping tests in a coroutine
        networkScope.launch {
            val results = measureConnectionQuality(serverUrl)
            val isQualitySufficient = evaluateConnectionQuality(results)

            withContext(Dispatchers.Main) {
                callback(isQualitySufficient)
            }
        }
    }

    /**
     * Measures various aspects of the connection quality
     * @param serverUrl Base server URL
     * @return ConnectionQuality object with measured metrics
     */
    private suspend fun measureConnectionQuality(serverUrl: String): ConnectionQuality {
        val pingResults = mutableListOf<Long>()
        var successfulPings = 0
        val pingUrl = serverUrl + PING_URL

        for (i in 1..MAX_PING_ATTEMPTS) {
            val startTime = System.currentTimeMillis()
            var success = false

            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(CONNECTION_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                    .readTimeout(READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                    .build()

                val request = Request.Builder()
                    .url(pingUrl)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val endTime = System.currentTimeMillis()
                        val latency = endTime - startTime
                        pingResults.add(latency)
                        successfulPings++
                        success = true
                        Log.d("NetworkQualityChecker", "Ping $i successful, latency: $latency ms")
                    } else {
                        Log.d("NetworkQualityChecker", "Ping $i failed with code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("NetworkQualityChecker", "Ping $i error: ${e.message}")
            }

            // Add artificial delay between pings
            if (i < MAX_PING_ATTEMPTS) {
                delay(300) // 300ms between ping attempts
            }

            // Early termination if we've already failed too many
            if (i >= 3 && successfulPings == 0) {
                Log.d("NetworkQualityChecker", "Early termination - first 3 pings all failed")
                break
            }
        }

        val successRate = successfulPings.toFloat() / MAX_PING_ATTEMPTS
        val averageLatency = if (pingResults.isNotEmpty()) pingResults.average() else Double.MAX_VALUE

        return ConnectionQuality(
            successfulPings = successfulPings,
            totalPings = MAX_PING_ATTEMPTS,
            successRate = successRate,
            averageLatency = averageLatency,
            pingResults = pingResults
        )
    }

    /**
     * Evaluates if the connection quality is sufficient for online mode
     * @param quality The measured connection quality
     * @return true if connection is good enough for online mode, false otherwise
     */
    private fun evaluateConnectionQuality(quality: ConnectionQuality): Boolean {
        Log.d("NetworkQualityChecker", "Connection metrics: " +
                "Successful pings=${quality.successfulPings}/${quality.totalPings}, " +
                "Success rate=${quality.successRate * 100}%, " +
                "Average latency=${quality.averageLatency}ms")

        // Check if minimum successful pings threshold is met
        if (quality.successfulPings < MIN_SUCCESSFUL_PINGS) {
            Log.d("NetworkQualityChecker", "Not enough successful pings")
            return false
        }

        // Check if success rate is sufficient
        if (quality.successRate < MIN_SUCCESSFUL_RATE) {
            Log.d("NetworkQualityChecker", "Success rate too low")
            return false
        }

        // Check if average latency is acceptable
        if (quality.averageLatency > MAX_ALLOWED_LATENCY_MS) {
            Log.d("NetworkQualityChecker", "Latency too high")
            return false
        }

        return true
    }

    /**
     * Checks if the device has network connectivity
     * @return true if connected to a network, false otherwise
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

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
     * Data class to hold connection quality metrics
     */
    data class ConnectionQuality(
        val successfulPings: Int,
        val totalPings: Int,
        val successRate: Float,
        val averageLatency: Double,
        val pingResults: List<Long>
    )
}