package studio.victorgomez.androidftpserver.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import studio.victorgomez.androidftpserver.model.ServerConfig
import studio.victorgomez.androidftpserver.model.ServerState
import studio.victorgomez.androidftpserver.model.ServerStatus
import studio.victorgomez.androidftpserver.ui.components.QrCodeDialog
import studio.victorgomez.androidftpserver.ui.theme.*
import studio.victorgomez.androidftpserver.util.NetworkUtils

@Composable
fun HomeScreen(
    serverStatus: ServerStatus,
    serverConfig: ServerConfig,
    networkInfo: NetworkUtils.NetworkInfo,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val isRunning = serverStatus.state == ServerState.RUNNING
    val isStarting = serverStatus.state == ServerState.STARTING
    var showQrDialog by remember { mutableStateOf(false) }

    val ip = serverStatus.ipAddress ?: networkInfo.ipAddress ?: "0.0.0.0"
    val ftpUrl = "ftp://$ip:${serverStatus.ftpPort}"
    val httpUrl = if (serverConfig.enableHttp) "http://$ip:${serverStatus.httpPort}" else null

    if (showQrDialog) {
        QrCodeDialog(
            ftpUrl = ftpUrl,
            httpUrl = httpUrl,
            onDismiss = { showQrDialog = false }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
    ) {
        // 1. Network Status Banner
        item {
            NetworkBanner(networkInfo = networkInfo)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 2. Hero Start/Stop Button
        item {
            ServerPowerButton(
                state = serverStatus.state,
                onClick = {
                    if (isRunning) onStopServer() else onStartServer()
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            ServerStatusBadge(state = serverStatus.state)
            Spacer(modifier = Modifier.height(28.dp))
        }

        // 3. URLs & Connection Info (visible when running)
        item {
            AnimatedVisibility(visible = isRunning) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // FTP Address Card
                    AddressCard(
                        title = "FTP Server Address",
                        subtitle = "Connect using FileZilla, Cyberduck, or Windows Explorer",
                        url = ftpUrl,
                        icon = Icons.Default.CloudSync,
                        accentColor = TealPrimary,
                        onCopy = { copyToClipboard(context, ftpUrl, "FTP Address Copied") },
                        onQr = { showQrDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Web Browser Card (if enabled)
                    if (httpUrl != null) {
                        AddressCard(
                            title = "Web Browser Transfer",
                            subtitle = "Open in Chrome, Firefox, Safari on any PC or phone",
                            url = httpUrl,
                            icon = Icons.Default.Language,
                            accentColor = MintAccent,
                            onCopy = { copyToClipboard(context, httpUrl, "Web Address Copied") },
                            onQr = { showQrDialog = true },
                            onOpen = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(httpUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Active Sessions Card
                    SessionsCard(
                        connectedCount = serverStatus.connectedClientsCount,
                        transfersCount = serverStatus.activeTransfersCount,
                        clients = serverStatus.connectedClients
                    )
                }
            }

            AnimatedVisibility(visible = !isRunning) {
                OfflineGuideCard(
                    config = serverConfig,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun NetworkBanner(networkInfo: NetworkUtils.NetworkInfo) {
    val isConnected = networkInfo.type != NetworkUtils.ConnectionType.NONE
    val bgColor = if (isConnected) TealDim else Color(0xFFFFEBEE)
    val textColor = if (isConnected) TealDark else RedError

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (networkInfo.type) {
                    NetworkUtils.ConnectionType.WIFI -> Icons.Default.Wifi
                    NetworkUtils.ConnectionType.ETHERNET -> Icons.Default.SettingsEthernet
                    NetworkUtils.ConnectionType.HOTSPOT -> Icons.Default.WifiTethering
                    NetworkUtils.ConnectionType.NONE -> Icons.Default.WifiOff
                },
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = networkInfo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = if (networkInfo.ipAddress != null) "IP: ${networkInfo.ipAddress}" else "No Wi-Fi or Hotspot connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ServerPowerButton(
    state: ServerState,
    onClick: () -> Unit
) {
    val isRunning = state == ServerState.RUNNING
    val isStarting = state == ServerState.STARTING
    val isStopping = state == ServerState.STOPPING

    val buttonColor by animateColorAsState(
        targetValue = when {
            isRunning -> RedError
            isStopping -> RedError.copy(alpha = 0.7f)
            isStarting -> Color(0xFFFFA000)
            else -> TealPrimary
        },
        animationSpec = tween(400)
    )

    val scale by animateFloatAsState(
        targetValue = if (isRunning) 1.05f else 1.0f,
        animationSpec = tween(300)
    )

    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing ring
        if (isRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(MintAccent.copy(alpha = 0.4f), Color.Transparent)
                        )
                    )
            )
        }

        Surface(
            shape = CircleShape,
            color = buttonColor,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .clickable(enabled = !isStarting && !isStopping, onClick = onClick)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isRunning || isStopping) Icons.Default.Stop else Icons.Default.PowerSettingsNew,
                    contentDescription = if (isRunning || isStopping) "Stop Server" else "Start Server",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when {
                        isStarting -> "STARTING…"
                        isStopping -> "STOPPING…"
                        isRunning -> "STOP"
                        else -> "START"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun ServerStatusBadge(state: ServerState) {
    val isRunning = state == ServerState.RUNNING
    val badgeBg = if (isRunning) GreenSuccess.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
    val badgeText = if (isRunning) GreenSuccess else MutedText

    Surface(
        shape = RoundedCornerShape(99.dp),
        color = badgeBg,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(badgeText)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (state) {
                    ServerState.RUNNING -> "SERVER ACTIVE"
                    ServerState.STARTING -> "STARTING…"
                    ServerState.STOPPING -> "STOPPING…"
                    ServerState.ERROR -> "SERVER ERROR"
                    ServerState.STOPPED -> "SERVER OFFLINE"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = badgeText,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun AddressCard(
    title: String,
    subtitle: String,
    url: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    onCopy: () -> Unit,
    onQr: () -> Unit,
    onOpen: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MutedText)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = url,
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (onOpen != null) {
                    FilledTonalButton(
                        onClick = onOpen,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                OutlinedButton(
                    onClick = onQr,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("QR Code", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onCopy,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SessionsCard(
    connectedCount: Int,
    transfersCount: Int,
    clients: List<String>
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Connected Clients",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$connectedCount active",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (connectedCount > 0) GreenSuccess else MutedText
                )
            }

            if (transfersCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚡ $transfersCount file transfer(s) in progress",
                    style = MaterialTheme.typography.bodySmall,
                    color = MintAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (clients.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                clients.forEach { client ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = MutedText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = client,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Waiting for connections from your PC, laptop, or phone...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }
    }
}

@Composable
private fun OfflineGuideCard(
    config: ServerConfig,
    onOpenSettings: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Quick Instructions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "1. Connect this phone and your PC/laptop to the same Wi-Fi network (or Hotspot).\n" +
                        "2. Tap the START button above.\n" +
                        "3. Open the displayed URL in your PC's browser (e.g. Chrome) or FTP client (e.g. FileZilla) to access files.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (config.allowAnonymous) "Anonymous access: ON" else "Anonymous access: OFF",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
                TextButton(onClick = onOpenSettings) {
                    Text("Configure Settings")
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String, toastMsg: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("URL", text))
    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
}
