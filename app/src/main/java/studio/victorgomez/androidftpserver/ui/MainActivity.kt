package studio.victorgomez.androidftpserver.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import studio.victorgomez.androidftpserver.model.ServerState
import studio.victorgomez.androidftpserver.ui.components.PermissionRationaleDialog
import studio.victorgomez.androidftpserver.ui.screens.HomeScreen
import studio.victorgomez.androidftpserver.ui.screens.SettingsScreen
import studio.victorgomez.androidftpserver.ui.theme.AndroidFtpServerTheme

enum class Screen {
    HOME,
    SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {

    private val viewModel: ServerViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val requestLegacyStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestPermissions()

        setContent {
            AndroidFtpServerTheme {
                val serverStatus by viewModel.serverStatus.collectAsState()
                val serverConfig by viewModel.serverConfig.collectAsState()
                val networkInfo by viewModel.networkInfo.collectAsState()

                var currentScreen by remember { mutableStateOf(Screen.HOME) }
                var showPermissionDialog by remember { mutableStateOf(false) }

                BackHandler(enabled = currentScreen != Screen.HOME) {
                    currentScreen = Screen.HOME
                }

                LaunchedEffect(Unit) {
                    if (!hasStoragePermission()) {
                        showPermissionDialog = true
                    }
                }

                if (showPermissionDialog) {
                    PermissionRationaleDialog(
                        onGrant = {
                            showPermissionDialog = false
                            requestStoragePermission()
                        },
                        onDismiss = { showPermissionDialog = false }
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = if (currentScreen == Screen.HOME) "Android FTP Server" else "Settings",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                if (currentScreen == Screen.SETTINGS) {
                                    IconButton(onClick = { currentScreen = Screen.HOME }) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            actions = {
                                if (currentScreen == Screen.HOME) {
                                    IconButton(onClick = { currentScreen = Screen.SETTINGS }) {
                                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AnimatedContent(
                            targetState = currentScreen,
                            label = "screen_transition"
                        ) { screen ->
                            when (screen) {
                                Screen.HOME -> HomeScreen(
                                    serverStatus = serverStatus,
                                    serverConfig = serverConfig,
                                    networkInfo = networkInfo,
                                    onStartServer = {
                                        if (!hasStoragePermission()) {
                                            showPermissionDialog = true
                                        } else {
                                            viewModel.startServer(this@MainActivity)
                                        }
                                    },
                                    onStopServer = {
                                        viewModel.stopServer(this@MainActivity)
                                    },
                                    onOpenSettings = {
                                        currentScreen = Screen.SETTINGS
                                    }
                                )
                                Screen.SETTINGS -> SettingsScreen(
                                    config = serverConfig,
                                    isServerRunning = serverStatus.state == ServerState.RUNNING,
                                    onSaveConfig = { viewModel.updateConfig(it) },
                                    onSaveUser = { oldUsername, newUser -> viewModel.saveUser(oldUsername, newUser) },
                                    onRemoveUser = { viewModel.removeUser(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNetwork()
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        } else {
            requestLegacyStoragePermission.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
