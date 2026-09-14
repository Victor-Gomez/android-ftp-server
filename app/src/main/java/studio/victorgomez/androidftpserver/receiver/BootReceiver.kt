package studio.victorgomez.androidftpserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import studio.victorgomez.androidftpserver.data.PreferencesRepository
import studio.victorgomez.androidftpserver.service.FtpServerService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val repo = PreferencesRepository(context)
            val config = repo.loadConfig()
            if (config.startOnBoot) {
                FtpServerService.start(context)
            }
        }
    }
}
