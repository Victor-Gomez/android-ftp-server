package studio.victorgomez.androidftpserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import studio.victorgomez.androidftpserver.service.FtpServerService

class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            FtpServerService.ACTION_START -> FtpServerService.start(context)
            FtpServerService.ACTION_STOP -> FtpServerService.stop(context)
        }
    }
}
