package com.hoshiyomix.injecttools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object NetworkUtils {

    enum class NetworkStatus {
        NO_INTERNET_NO_VPN,
        INTERNET_NO_VPN,
        NO_INTERNET_VPN,
        INTERNET_VPN,
        DISCONNECTED
    }

    /**
     * Check if device has active internet connection.
     * Use this for operations that REQUIRE internet (e.g., fetching from crt.sh).
     */
    fun hasInternetConnection(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && 
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun checkNetworkStatus(context: Context): NetworkStatus {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val activeNetwork = connectivityManager.activeNetwork ?: return NetworkStatus.DISCONNECTED
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return NetworkStatus.DISCONNECTED

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && 
                          capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        
        val hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

        return when {
            hasInternet && hasVpn -> NetworkStatus.INTERNET_VPN
            !hasInternet && hasVpn -> NetworkStatus.NO_INTERNET_VPN
            hasInternet && !hasVpn -> NetworkStatus.INTERNET_NO_VPN
            else -> NetworkStatus.NO_INTERNET_NO_VPN
        }
    }
}
