package com.txahub.vn

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log

/**
 * Monitor network status và log thay đổi
 */
class NetworkMonitor(private val context: Context) {
    
    private val logWriter = LogWriter(context)
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isMonitoring = false
    
    /**
     * Bắt đầu monitor network
     */
    fun startMonitoring() {
        if (isMonitoring) return
        
        try {
            connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                logWriter.writeAppLog("ConnectivityManager is null", "NetworkMonitor", Log.WARN)
                return
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val networkInfo = getNetworkInfo()
                        logWriter.writeAppLog("Network Available: $networkInfo", "NetworkMonitor", Log.INFO)
                    }
                    
                    override fun onLost(network: Network) {
                        logWriter.writeAppLog("Network Lost", "NetworkMonitor", Log.WARN)
                    }
                    
                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        // Không log network changes nữa, chỉ monitor
                        // Có thể trigger speedtest tự động nếu cần
                    }
                }
                
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                
                connectivityManager?.registerNetworkCallback(request, networkCallback!!)
                isMonitoring = true
                logWriter.writeAppLog("Network monitoring started", "NetworkMonitor", Log.INFO)
            }
        } catch (e: Exception) {
            logWriter.writeAppLog("Failed to start network monitoring: ${e.message}", "NetworkMonitor", Log.ERROR)
        }
    }
    
    /**
     * Dừng monitor network
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        try {
            networkCallback?.let {
                connectivityManager?.unregisterNetworkCallback(it)
            }
            networkCallback = null
            isMonitoring = false
            logWriter.writeAppLog("Network monitoring stopped", "NetworkMonitor", Log.INFO)
        } catch (e: Exception) {
            logWriter.writeAppLog("Failed to stop network monitoring: ${e.message}", "NetworkMonitor", Log.ERROR)
        }
    }
    
    /**
     * Kiểm tra có internet không
     */
    fun isConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm?.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = cm?.activeNetworkInfo
                @Suppress("DEPRECATION")
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            logWriter.writeAppLog("Error checking network: ${e.message}", "NetworkMonitor", Log.ERROR)
            false
        }
    }
    
    /**
     * Lấy thông tin network hiện tại
     */
    private fun getNetworkInfo(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm?.activeNetwork ?: return "No Network"
                val capabilities = cm.getNetworkCapabilities(network) ?: return "No Capabilities"
                
                buildString {
                    when {
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> append("WiFi")
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> append("Cellular")
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> append("Ethernet")
                        else -> append("Unknown")
                    }
                    append(" (Internet: ${capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)})")
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = cm?.activeNetworkInfo
                @Suppress("DEPRECATION")
                networkInfo?.typeName ?: "Unknown"
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}

