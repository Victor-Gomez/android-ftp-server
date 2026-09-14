package studio.victorgomez.androidftpserver.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import studio.victorgomez.androidftpserver.model.FtpUser
import studio.victorgomez.androidftpserver.model.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class PreferencesRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("wifi_ftp_server_prefs", Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<ServerConfig> = _configFlow.asStateFlow()

    fun loadConfig(): ServerConfig {
        val defaultDir = Environment.getExternalStorageDirectory().absolutePath
        val ftpPort = prefs.getInt(KEY_FTP_PORT, 2121)
        val passivePorts = prefs.getString(KEY_PASSIVE_PORTS, "50000-50050") ?: "50000-50050"
        val allowAnonymous = prefs.getBoolean(KEY_ALLOW_ANONYMOUS, true)
        val anonymousWrite = prefs.getBoolean(KEY_ANONYMOUS_WRITE, true)
        val homeDirectory = prefs.getString(KEY_HOME_DIRECTORY, defaultDir) ?: defaultDir
        val enableHttp = prefs.getBoolean(KEY_ENABLE_HTTP, true)
        val httpPort = prefs.getInt(KEY_HTTP_PORT, 8080)
        val enableFtps = prefs.getBoolean(KEY_ENABLE_FTPS, false)
        val autoStartOnWifi = prefs.getBoolean(KEY_AUTO_START_WIFI, false)
        val autoStopOnWifiDisconnect = prefs.getBoolean(KEY_AUTO_STOP_WIFI, true)
        val startOnBoot = prefs.getBoolean(KEY_START_ON_BOOT, false)
        val usersJson = prefs.getString(KEY_USERS, "[]") ?: "[]"
        val users = parseUsers(usersJson)

        return ServerConfig(
            ftpPort = ftpPort,
            passivePorts = passivePorts,
            allowAnonymous = allowAnonymous,
            anonymousWrite = anonymousWrite,
            homeDirectory = homeDirectory,
            enableHttp = enableHttp,
            httpPort = httpPort,
            enableFtps = enableFtps,
            autoStartOnWifi = autoStartOnWifi,
            autoStopOnWifiDisconnect = autoStopOnWifiDisconnect,
            startOnBoot = startOnBoot,
            users = users
        )
    }

    fun saveConfig(config: ServerConfig) {
        prefs.edit().apply {
            putInt(KEY_FTP_PORT, config.ftpPort)
            putString(KEY_PASSIVE_PORTS, config.passivePorts)
            putBoolean(KEY_ALLOW_ANONYMOUS, config.allowAnonymous)
            putBoolean(KEY_ANONYMOUS_WRITE, config.anonymousWrite)
            putString(KEY_HOME_DIRECTORY, config.homeDirectory)
            putBoolean(KEY_ENABLE_HTTP, config.enableHttp)
            putInt(KEY_HTTP_PORT, config.httpPort)
            putBoolean(KEY_ENABLE_FTPS, config.enableFtps)
            putBoolean(KEY_AUTO_START_WIFI, config.autoStartOnWifi)
            putBoolean(KEY_AUTO_STOP_WIFI, config.autoStopOnWifiDisconnect)
            putBoolean(KEY_START_ON_BOOT, config.startOnBoot)
            putString(KEY_USERS, serializeUsers(config.users))
            apply()
        }
        _configFlow.value = config
    }

    private fun parseUsers(jsonStr: String): List<FtpUser> {
        val list = mutableListOf<FtpUser>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    FtpUser(
                        username = obj.getString("username"),
                        password = obj.getString("password"),
                        isReadOnly = obj.optBoolean("isReadOnly", false)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun serializeUsers(users: List<FtpUser>): String {
        val arr = JSONArray()
        for (u in users) {
            val obj = JSONObject()
            obj.put("username", u.username)
            obj.put("password", u.password)
            obj.put("isReadOnly", u.isReadOnly)
            arr.put(obj)
        }
        return arr.toString()
    }

    companion object {
        private const val KEY_FTP_PORT = "key_ftp_port"
        private const val KEY_PASSIVE_PORTS = "key_passive_ports"
        private const val KEY_ALLOW_ANONYMOUS = "key_allow_anonymous"
        private const val KEY_ANONYMOUS_WRITE = "key_anonymous_write"
        private const val KEY_HOME_DIRECTORY = "key_home_directory"
        private const val KEY_ENABLE_HTTP = "key_enable_http"
        private const val KEY_HTTP_PORT = "key_http_port"
        private const val KEY_ENABLE_FTPS = "key_enable_ftps"
        private const val KEY_AUTO_START_WIFI = "key_auto_start_wifi"
        private const val KEY_AUTO_STOP_WIFI = "key_auto_stop_wifi"
        private const val KEY_START_ON_BOOT = "key_start_on_boot"
        private const val KEY_USERS = "key_users"
    }
}
