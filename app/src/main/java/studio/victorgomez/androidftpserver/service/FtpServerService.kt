package studio.victorgomez.androidftpserver.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import studio.victorgomez.androidftpserver.R
import studio.victorgomez.androidftpserver.data.PreferencesRepository
import studio.victorgomez.androidftpserver.model.ServerConfig
import studio.victorgomez.androidftpserver.model.ServerState
import studio.victorgomez.androidftpserver.model.ServerStatus
import studio.victorgomez.androidftpserver.server.FtpServerManager
import studio.victorgomez.androidftpserver.server.HttpServerManager
import studio.victorgomez.androidftpserver.server.SsdpDiscoveryService
import studio.victorgomez.androidftpserver.ui.MainActivity
import studio.victorgomez.androidftpserver.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FtpServerService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefsRepo: PreferencesRepository

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    private lateinit var ftpManager: FtpServerManager
    private val httpManager = HttpServerManager()
    private val ssdpService = SsdpDiscoveryService()

    inner class LocalBinder : Binder() {
        fun getService(): FtpServerService = this@FtpServerService
    }

    override fun onCreate() {
        super.onCreate()
        prefsRepo = PreferencesRepository(this)
        ftpManager = FtpServerManager { count, transfers, clients ->
            val cur = _statusFlow.value
            val updated = cur.copy(
                connectedClientsCount = count,
                activeTransfersCount = transfers,
                connectedClients = clients
            )
            _statusFlow.value = updated
            updateNotification(updated)
        }
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_NOT_STICKY
    }

    fun startServer() {
        if (_statusFlow.value.state == ServerState.RUNNING) return

        val config = prefsRepo.loadConfig()
        val netInfo = NetworkUtils.getActiveNetworkInfo(this)
        val ip = netInfo.ipAddress

        _statusFlow.value = ServerStatus(
            state = ServerState.STARTING,
            ipAddress = ip,
            networkName = netInfo.name,
            ftpPort = config.ftpPort,
            httpPort = config.httpPort,
            isHttpEnabled = config.enableHttp
        )

        startForeground(NOTIFICATION_ID, buildNotification(_statusFlow.value))

        serviceScope.launch {
            try {
                acquireLocks()
                ftpManager.start(config)

                if (config.enableHttp) {
                    httpManager.start(config)
                    if (ip != null) {
                        ssdpService.start(ip, config.httpPort)
                    }
                }

                val runningStatus = ServerStatus(
                    state = ServerState.RUNNING,
                    ipAddress = ip,
                    networkName = netInfo.name,
                    ftpPort = config.ftpPort,
                    httpPort = config.httpPort,
                    isHttpEnabled = config.enableHttp
                )
                _statusFlow.value = runningStatus
                updateNotification(runningStatus)
            } catch (e: Exception) {
                e.printStackTrace()
                releaseLocks()
                _statusFlow.value = ServerStatus(
                    state = ServerState.ERROR,
                    errorMessage = e.message ?: "Failed to start server"
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    fun stopServer() {
        serviceScope.launch {
            try {
                ftpManager.stop()
                httpManager.stop()
                ssdpService.stop()
                releaseLocks()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _statusFlow.value = ServerStatus(state = ServerState.STOPPED)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun acquireLocks() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AndroidFtpServer:WakeLock").apply {
                acquire(12 * 60 * 60 * 1000L) // 12 hours max
            }
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "AndroidFtpServer:WifiLock").apply {
                acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: ServerStatus): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FtpServerService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ip = status.ipAddress ?: "0.0.0.0"
        val contentText = "ftp://$ip:${status.ftpPort} | ${status.connectedClientsCount} client(s)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.server_running))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_ftp_server)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, getString(R.string.stop_server), pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: ServerStatus) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(status))
    }

    override fun onDestroy() {
        stopServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "studio.victorgomez.androidftpserver.ACTION_START"
        const val ACTION_STOP = "studio.victorgomez.androidftpserver.ACTION_STOP"
        private const val CHANNEL_ID = "ftp_server_channel"
        private const val NOTIFICATION_ID = 1001

        private val _statusFlow = MutableStateFlow(ServerStatus())
        val statusFlow: StateFlow<ServerStatus> = _statusFlow.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, FtpServerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FtpServerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
