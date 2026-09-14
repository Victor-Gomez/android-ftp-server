package studio.victorgomez.androidftpserver

import android.app.Application
import android.content.IntentFilter
import android.net.ConnectivityManager
import studio.victorgomez.androidftpserver.receiver.WifiReceiver

class FtpApplication : Application() {
    private val wifiReceiver = WifiReceiver()

    override fun onCreate() {
        super.onCreate()
        try {
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            registerReceiver(wifiReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
