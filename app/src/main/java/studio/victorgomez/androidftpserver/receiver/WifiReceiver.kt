package studio.victorgomez.androidftpserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import studio.victorgomez.androidftpserver.data.PreferencesRepository
import studio.victorgomez.androidftpserver.model.ServerState
import studio.victorgomez.androidftpserver.service.FtpServerService
import studio.victorgomez.androidftpserver.util.NetworkUtils

class WifiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = PreferencesRepository(context)
        val config = repo.loadConfig()
        val netInfo = NetworkUtils.getActiveNetworkInfo(context)
        val isWifi = netInfo.type == NetworkUtils.ConnectionType.WIFI
        val isRunning = FtpServerService.statusFlow.value.state == ServerState.RUNNING

        if (isWifi && config.autoStartOnWifi && !isRunning) {
            FtpServerService.start(context)
        } else if (!isWifi && config.autoStopOnWifiDisconnect && isRunning) {
            FtpServerService.stop(context)
        }
    }
}
