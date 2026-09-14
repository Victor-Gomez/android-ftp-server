package studio.victorgomez.androidftpserver.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    enum class ConnectionType {
        WIFI,
        ETHERNET,
        HOTSPOT,
        NONE
    }

    data class NetworkInfo(
        val type: ConnectionType,
        val name: String,
        val ipAddress: String?
    )

    fun getActiveNetworkInfo(context: Context): NetworkInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNetwork)

        val ip = getLocalIpAddress()

        if (caps != null) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val ssid = getWifiSsid(context)
                return NetworkInfo(ConnectionType.WIFI, ssid ?: "Wi-Fi", ip)
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return NetworkInfo(ConnectionType.ETHERNET, "Ethernet", ip)
            }
        }

        // Check if Hotspot / Tethering interface has an IP
        if (ip != null && (ip.startsWith("192.168.43.") || ip.startsWith("192.168.44.") || ip.startsWith("192.168.50."))) {
            return NetworkInfo(ConnectionType.HOTSPOT, "Hotspot (AP)", ip)
        }

        if (ip != null) {
            return NetworkInfo(ConnectionType.WIFI, "Local Network", ip)
        }

        return NetworkInfo(ConnectionType.NONE, "No Connection", null)
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            // Prioritize wlan0, eth0, ap0, etc.
            val sorted = interfaces.sortedByDescending {
                when {
                    it.name.startsWith("wlan") -> 3
                    it.name.startsWith("eth") -> 2
                    it.name.startsWith("ap") || it.name.startsWith("rndis") -> 1
                    else -> 0
                }
            }
            for (intf in sorted) {
                if (!intf.isUp || intf.isLoopback) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (host != null && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getWifiSsid(context: Context): String? {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info: WifiInfo? = wm.connectionInfo
            if (info != null) {
                val ssid = info.ssid
                if (ssid != null && ssid != "<unknown ssid>" && ssid != "\"\"") {
                    return ssid.trim('"')
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
