package studio.victorgomez.androidftpserver.model

import android.os.Environment

data class FtpUser(
    val username: String,
    val password: String,
    val isReadOnly: Boolean = false
)

data class ServerConfig(
    val ftpPort: Int = 2121,
    val passivePorts: String = "50000-50050",
    val allowAnonymous: Boolean = true,
    val anonymousWrite: Boolean = true,
    val homeDirectory: String = Environment.getExternalStorageDirectory().absolutePath,
    val enableHttp: Boolean = true,
    val httpPort: Int = 8080,
    val enableFtps: Boolean = false,
    val autoStartOnWifi: Boolean = false,
    val autoStopOnWifiDisconnect: Boolean = true,
    val startOnBoot: Boolean = false,
    val users: List<FtpUser> = emptyList()
)

enum class ServerState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

data class ServerStatus(
    val state: ServerState = ServerState.STOPPED,
    val errorMessage: String? = null,
    val ipAddress: String? = null,
    val networkName: String? = null,
    val ftpPort: Int = 2121,
    val httpPort: Int = 8080,
    val isHttpEnabled: Boolean = true,
    val connectedClientsCount: Int = 0,
    val activeTransfersCount: Int = 0,
    val connectedClients: List<String> = emptyList()
)
