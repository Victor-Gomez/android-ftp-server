package studio.victorgomez.androidftpserver.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import studio.victorgomez.androidftpserver.data.PreferencesRepository
import studio.victorgomez.androidftpserver.model.FtpUser
import studio.victorgomez.androidftpserver.model.ServerConfig
import studio.victorgomez.androidftpserver.model.ServerStatus
import studio.victorgomez.androidftpserver.service.FtpServerService
import studio.victorgomez.androidftpserver.util.NetworkUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ServerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = PreferencesRepository(application)

    val serverStatus: StateFlow<ServerStatus> = FtpServerService.statusFlow

    val serverConfig: StateFlow<ServerConfig> = prefsRepo.configFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = prefsRepo.loadConfig()
        )

    private val _networkInfo = MutableStateFlow(NetworkUtils.getActiveNetworkInfo(application))
    val networkInfo: StateFlow<NetworkUtils.NetworkInfo> = _networkInfo.asStateFlow()

    fun refreshNetwork() {
        _networkInfo.value = NetworkUtils.getActiveNetworkInfo(getApplication())
    }

    fun startServer(context: Context) {
        FtpServerService.start(context)
        refreshNetwork()
    }

    fun stopServer(context: Context) {
        FtpServerService.stop(context)
        refreshNetwork()
    }

    fun updateConfig(newConfig: ServerConfig) {
        viewModelScope.launch {
            prefsRepo.saveConfig(newConfig)
        }
    }

    fun saveUser(oldUsername: String?, newUser: FtpUser) {
        val current = serverConfig.value
        val updatedUsers = current.users.filterNot {
            (oldUsername != null && it.username.equals(oldUsername, ignoreCase = true)) ||
            it.username.equals(newUser.username, ignoreCase = true)
        } + newUser
        updateConfig(current.copy(users = updatedUsers))
    }

    fun addUser(user: FtpUser) {
        saveUser(null, user)
    }

    fun removeUser(username: String) {
        val current = serverConfig.value
        val updatedUsers = current.users.filterNot { it.username.equals(username, ignoreCase = true) }
        updateConfig(current.copy(users = updatedUsers))
    }
}
