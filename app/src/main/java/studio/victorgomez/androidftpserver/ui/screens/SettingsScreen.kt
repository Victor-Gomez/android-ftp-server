package studio.victorgomez.androidftpserver.ui.screens

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import studio.victorgomez.androidftpserver.model.FtpUser
import studio.victorgomez.androidftpserver.model.ServerConfig
import studio.victorgomez.androidftpserver.ui.components.UserDialog
import studio.victorgomez.androidftpserver.ui.theme.MintAccent
import studio.victorgomez.androidftpserver.ui.theme.MutedText
import studio.victorgomez.androidftpserver.ui.theme.RedError
import studio.victorgomez.androidftpserver.ui.theme.TealPrimary

@Composable
fun SettingsScreen(
    config: ServerConfig,
    isServerRunning: Boolean,
    onSaveConfig: (ServerConfig) -> Unit,
    onSaveUser: (oldUsername: String?, newUser: FtpUser) -> Unit,
    onRemoveUser: (String) -> Unit
) {
    var ftpPortText by remember(config.ftpPort) { mutableStateOf(config.ftpPort.toString()) }
    var httpPortText by remember(config.httpPort) { mutableStateOf(config.httpPort.toString()) }
    var passivePortsText by remember(config.passivePorts) { mutableStateOf(config.passivePorts) }
    var editingUser by remember { mutableStateOf<FtpUser?>(null) }
    var showUserDialog by remember { mutableStateOf(false) }

    if (showUserDialog) {
        UserDialog(
            initialUser = editingUser,
            existingUsers = config.users,
            onDismiss = {
                showUserDialog = false
                editingUser = null
            },
            onSave = { oldUsername, user ->
                onSaveUser(oldUsername, user)
                showUserDialog = false
                editingUser = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 48.dp)
    ) {
        if (isServerRunning) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = RedError)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Server is currently running. Stop the server to apply network port changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // ── 1. General & Automation ──
        item {
            SectionHeader(title = "Automation & Network")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitchRow(
                        title = "Auto-start on Wi-Fi",
                        subtitle = "Start server automatically when connecting to Wi-Fi",
                        checked = config.autoStartOnWifi,
                        onCheckedChange = { onSaveConfig(config.copy(autoStartOnWifi = it)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingSwitchRow(
                        title = "Auto-stop on Wi-Fi disconnect",
                        subtitle = "Stop server when Wi-Fi is turned off or disconnected",
                        checked = config.autoStopOnWifiDisconnect,
                        onCheckedChange = { onSaveConfig(config.copy(autoStopOnWifiDisconnect = it)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingSwitchRow(
                        title = "Start on device boot",
                        subtitle = "Launch FTP server when phone restarts",
                        checked = config.startOnBoot,
                        onCheckedChange = { onSaveConfig(config.copy(startOnBoot = it)) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── 2. FTP Settings ──
        item {
            SectionHeader(title = "FTP Server Settings")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = ftpPortText,
                        onValueChange = {
                            ftpPortText = it
                            val p = it.toIntOrNull()
                            if (p != null && p in 1024..65535) {
                                onSaveConfig(config.copy(ftpPort = p))
                            }
                        },
                        label = { Text("FTP Port (1024 - 65535)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = passivePortsText,
                        onValueChange = {
                            passivePortsText = it
                            onSaveConfig(config.copy(passivePorts = it.trim()))
                        },
                        label = { Text("Passive Data Ports Range") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    SettingSwitchRow(
                        title = "Enable FTPS (TLS / SSL)",
                        subtitle = "Encrypt FTP control and data connections with TLS",
                        checked = config.enableFtps,
                        onCheckedChange = { onSaveConfig(config.copy(enableFtps = it)) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

                    SettingSwitchRow(
                        title = "Allow Anonymous Access",
                        subtitle = "Clients can connect without username and password",
                        checked = config.allowAnonymous,
                        onCheckedChange = { onSaveConfig(config.copy(allowAnonymous = it)) }
                    )

                    if (config.allowAnonymous) {
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingSwitchRow(
                            title = "Allow Anonymous Write",
                            subtitle = "Enable file uploads and deletions for anonymous users",
                            checked = config.anonymousWrite,
                            onCheckedChange = { onSaveConfig(config.copy(anonymousWrite = it)) }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── 3. Web File Manager ──
        item {
            SectionHeader(title = "Web Browser File Manager")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitchRow(
                        title = "Enable Web Browser Access",
                        subtitle = "Allows PC/phone browsers to view, upload and download files directly",
                        checked = config.enableHttp,
                        onCheckedChange = { onSaveConfig(config.copy(enableHttp = it)) }
                    )

                    if (config.enableHttp) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        SettingSwitchRow(
                            title = "Enable HTTPS (SSL / TLS)",
                            subtitle = "Encrypt web transfers with SSL/TLS (Self-signed certificate)",
                            checked = config.enableHttps,
                            onCheckedChange = { onSaveConfig(config.copy(enableHttps = it)) }
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = httpPortText,
                            onValueChange = {
                                httpPortText = it
                                val p = it.toIntOrNull()
                                if (p != null && p in 1024..65535) {
                                    onSaveConfig(config.copy(httpPort = p))
                                }
                            },
                            label = { Text("Web Server Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── 4. Storage Directory ──
        item {
            SectionHeader(title = "Storage Directory")
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Root Directory:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = config.homeDirectory,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val defaultPath = Environment.getExternalStorageDirectory().absolutePath
                            onSaveConfig(config.copy(homeDirectory = defaultPath))
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset to Full Internal Storage")
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── 5. User Accounts ──
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(title = "User Accounts")
                TextButton(onClick = {
                    editingUser = null
                    showUserDialog = true
                }) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add User")
                }
            }

            if (config.users.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No custom users configured. Anyone on your Wi-Fi can access via anonymous mode (if enabled).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                    }
                }
            }
        }

        items(config.users) { user ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        editingUser = user
                        showUserDialog = true
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = user.username,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (user.isReadOnly) "Read-Only" else "Read & Write",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (user.isReadOnly) Color(0xFFFFA000) else MintAccent
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            editingUser = user
                            showUserDialog = true
                        }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit User",
                                tint = TealPrimary
                            )
                        }
                        IconButton(onClick = { onRemoveUser(user.username) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete User", tint = RedError)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TealPrimary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MutedText)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
