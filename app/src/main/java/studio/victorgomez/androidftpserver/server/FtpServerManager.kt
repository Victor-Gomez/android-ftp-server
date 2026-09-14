package studio.victorgomez.androidftpserver.server

import studio.victorgomez.androidftpserver.model.ServerConfig
import org.apache.ftpserver.ConnectionConfigFactory
import org.apache.ftpserver.DataConnectionConfigurationFactory
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authentication
import org.apache.ftpserver.ftplet.AuthenticationFailedException
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.ftplet.AuthorizationRequest
import org.apache.ftpserver.ftplet.DefaultFtplet
import org.apache.ftpserver.ftplet.FtpException
import org.apache.ftpserver.ftplet.FtpReply
import org.apache.ftpserver.ftplet.FtpRequest
import org.apache.ftpserver.ftplet.FtpSession
import org.apache.ftpserver.ftplet.FtpletResult
import org.apache.ftpserver.ftplet.User
import org.apache.ftpserver.ftplet.UserManager
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.AnonymousAuthentication
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginRequest
import org.apache.ftpserver.usermanager.impl.TransferRatePermission
import org.apache.ftpserver.usermanager.impl.TransferRateRequest
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class FtpServerUser : BaseUser() {
    override fun authorize(request: AuthorizationRequest?): AuthorizationRequest? {
        if (request is ConcurrentLoginRequest) {
            return request
        }
        if (request is TransferRateRequest) {
            return request
        }
        return super.authorize(request)
    }
}

class FtpServerManager(
    private val onSessionsChanged: (clientCount: Int, activeTransfers: Int, clients: List<String>) -> Unit
) {
    private var server: FtpServer? = null
    private val connectedClients = ConcurrentHashMap.newKeySet<String>()
    private var activeTransfers = 0

    @Synchronized
    fun start(config: ServerConfig) {
        stop()

        val serverFactory = FtpServerFactory()
        val listenerFactory = ListenerFactory()
        listenerFactory.port = config.ftpPort

        // Configure passive data ports
        try {
            val dataConnFactory = DataConnectionConfigurationFactory()
            dataConnFactory.passivePorts = config.passivePorts
            listenerFactory.dataConnectionConfiguration =
                dataConnFactory.createDataConnectionConfiguration()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        serverFactory.addListener("default", listenerFactory.createListener())

        // Connection configuration (max concurrent logins, anonymous access)
        val connConfigFactory = ConnectionConfigFactory().apply {
            maxLogins = 100
            maxAnonymousLogins = 100
            maxLoginFailures = 10
            loginFailureDelay = 500
            isAnonymousLoginEnabled = config.allowAnonymous
        }
        serverFactory.connectionConfig = connConfigFactory.createConnectionConfig()

        // In-memory User Manager
        serverFactory.userManager = createUserManager(config)

        // Ftplet for session & transfer tracking
        val ftpletMap = mutableMapOf<String, org.apache.ftpserver.ftplet.Ftplet>()
        ftpletMap["sessionTracker"] = object : DefaultFtplet() {
            override fun onConnect(session: FtpSession): FtpletResult {
                val remoteAddr = session.clientAddress?.toString() ?: "Unknown"
                connectedClients.add(remoteAddr)
                notifyChange()
                return FtpletResult.DEFAULT
            }

            override fun onDisconnect(session: FtpSession): FtpletResult {
                val remoteAddr = session.clientAddress?.toString() ?: "Unknown"
                connectedClients.remove(remoteAddr)
                notifyChange()
                return FtpletResult.DEFAULT
            }

            override fun onUploadStart(session: FtpSession, request: FtpRequest): FtpletResult {
                synchronized(this@FtpServerManager) { activeTransfers++ }
                notifyChange()
                return FtpletResult.DEFAULT
            }

            override fun onUploadEnd(session: FtpSession, request: FtpRequest): FtpletResult {
                synchronized(this@FtpServerManager) { if (activeTransfers > 0) activeTransfers-- }
                notifyChange()
                return FtpletResult.DEFAULT
            }

            override fun onDownloadStart(session: FtpSession, request: FtpRequest): FtpletResult {
                synchronized(this@FtpServerManager) { activeTransfers++ }
                notifyChange()
                return FtpletResult.DEFAULT
            }

            override fun onDownloadEnd(session: FtpSession, request: FtpRequest): FtpletResult {
                synchronized(this@FtpServerManager) { if (activeTransfers > 0) activeTransfers-- }
                notifyChange()
                return FtpletResult.DEFAULT
            }
        }
        serverFactory.ftplets = ftpletMap

        val newServer = serverFactory.createServer()
        newServer.start()
        server = newServer
    }

    @Synchronized
    fun stop() {
        try {
            server?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            server = null
            connectedClients.clear()
            activeTransfers = 0
            notifyChange()
        }
    }

    val isRunning: Boolean
        get() = server != null && !server!!.isStopped

    private fun notifyChange() {
        onSessionsChanged(connectedClients.size, activeTransfers, connectedClients.toList())
    }

    private fun createUserManager(config: ServerConfig): UserManager {
        val userMap = ConcurrentHashMap<String, User>()

        val homeDir = File(config.homeDirectory).apply { if (!exists()) mkdirs() }
        val homePath = homeDir.absolutePath

        // 1. Anonymous User
        if (config.allowAnonymous) {
            val anonUser = FtpServerUser().apply {
                name = "anonymous"
                homeDirectory = homePath
                setEnabled(true)
                maxIdleTime = 0
                val authorities = mutableListOf<Authority>()
                authorities.add(ConcurrentLoginPermission(0, 0))
                authorities.add(TransferRatePermission(0, 0))
                if (config.anonymousWrite) {
                    authorities.add(WritePermission())
                }
                this.authorities = authorities
            }
            userMap["anonymous"] = anonUser
        }

        // 2. Custom Users
        for (u in config.users) {
            val user = FtpServerUser().apply {
                name = u.username
                password = u.password
                homeDirectory = homePath
                setEnabled(true)
                maxIdleTime = 0
                val authorities = mutableListOf<Authority>()
                authorities.add(ConcurrentLoginPermission(0, 0))
                authorities.add(TransferRatePermission(0, 0))
                if (!u.isReadOnly) {
                    authorities.add(WritePermission())
                }
                this.authorities = authorities
            }
            userMap[u.username.lowercase()] = user
        }

        return object : UserManager {
            override fun getUserByName(username: String?): User? {
                if (username == null) return null
                return userMap[username.lowercase()]
            }

            override fun getAllUserNames(): Array<String> {
                return userMap.keys().toList().toTypedArray()
            }

            override fun delete(username: String?) {
                if (username != null) userMap.remove(username.lowercase())
            }

            override fun save(user: User?) {
                if (user != null) userMap[user.name.lowercase()] = user
            }

            override fun doesExist(username: String?): Boolean {
                if (username == null) return false
                return userMap.containsKey(username.lowercase())
            }

            override fun authenticate(authentication: Authentication?): User {
                if (authentication is AnonymousAuthentication) {
                    if (config.allowAnonymous) {
                        return userMap["anonymous"]
                            ?: throw AuthenticationFailedException("Anonymous not configured")
                    } else {
                        throw AuthenticationFailedException("Anonymous access not allowed")
                    }
                } else if (authentication is UsernamePasswordAuthentication) {
                    val uname = authentication.username?.lowercase() ?: ""
                    val u = userMap[uname]
                        ?: throw AuthenticationFailedException("User not found")
                    if (u.password != null && u.password == authentication.password) {
                        return u
                    }
                    throw AuthenticationFailedException("Invalid password")
                }
                throw AuthenticationFailedException("Unsupported authentication method")
            }

            override fun getAdminName(): String = "admin"
            override fun isAdmin(username: String?): Boolean = username.equals("admin", ignoreCase = true)
        }
    }
}
