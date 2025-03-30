package com.example.helloworldapp.data

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log

/**
 * ContextProvider - A utility class to provide application context
 *
 * This class is initialized in the Application class and provides
 * access to the application context throughout the app.
 */
object ContextProvider {
    private var applicationContext: Context? = null

    /**
     * Initialize with application context
     */
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    /**
     * Get the application context
     */
    fun getContext(): Context {
        return applicationContext ?: throw IllegalStateException(
            "ContextProvider not initialized. Call ContextProvider.init() in your Application class"
        )
    }
}

/**
 * Example of Application class setup
 */
class TeproApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize ContextProvider with application context
        ContextProvider.init(this)
    }
}

/**
 * Updated isOnline method for ServerApi
 */
fun isOnline(): Boolean {
    val context = try {
        ContextProvider.getContext()
    } catch (e: Exception) {
        Log.e("ServerApi", "Error getting application context", e)
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