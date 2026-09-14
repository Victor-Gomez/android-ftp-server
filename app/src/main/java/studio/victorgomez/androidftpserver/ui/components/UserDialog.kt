package studio.victorgomez.androidftpserver.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import studio.victorgomez.androidftpserver.model.FtpUser
import studio.victorgomez.androidftpserver.ui.theme.*

@Composable
fun UserDialog(
    initialUser: FtpUser? = null,
    existingUsers: List<FtpUser> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (oldUsername: String?, newUser: FtpUser) -> Unit
) {
    var username by remember(initialUser) { mutableStateOf(initialUser?.username ?: "") }
    var password by remember(initialUser) { mutableStateOf(initialUser?.password ?: "") }
    var isReadOnly by remember(initialUser) { mutableStateOf(initialUser?.isReadOnly ?: false) }
    var errorMessage by remember(initialUser) { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (initialUser == null) "Add User" else "Edit User",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it.trim(); errorMessage = null },
                    label = { Text("Username") },
                    singleLine = true,
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Permission Level",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                SegmentedPermissionSelector(
                    isReadOnly = isReadOnly,
                    onSelect = { isReadOnly = it }
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (username.isBlank()) {
                                errorMessage = "Username cannot be empty"
                                return@Button
                            }
                            if (password.isBlank()) {
                                errorMessage = "Password cannot be empty"
                                return@Button
                            }
                            val isTaken = existingUsers.any {
                                it.username.equals(username, ignoreCase = true) &&
                                (initialUser == null || !it.username.equals(initialUser.username, ignoreCase = true))
                            }
                            if (isTaken) {
                                errorMessage = "Username '$username' already exists"
                                return@Button
                            }
                            onSave(initialUser?.username, FtpUser(username, password, isReadOnly))
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun SegmentedPermissionSelector(
    isReadOnly: Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    // Pure theme colors (Teal, Mint, Dark/Light surface variants)
    val containerBg = if (isDark) DarkSurfaceVariant else TealDim
    val containerBorder = if (isDark) TealDark else TealLight

    val activeCardBg = if (isDark) DarkSurface else Color.White
    val activeBorder = if (isDark) MintAccent else TealLight
    val activeText = if (isDark) MintAccent else TealDark

    val inactiveText = if (isDark) TealLight.copy(alpha = 0.75f) else TealDark.copy(alpha = 0.65f)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerBg,
        border = BorderStroke(1.5.dp, containerBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Option 1: Read-only
            val readOnlyBg by animateColorAsState(
                targetValue = if (isReadOnly) activeCardBg else Color.Transparent,
                label = "readOnlyBg"
            )
            val readOnlyTextColor by animateColorAsState(
                targetValue = if (isReadOnly) activeText else inactiveText,
                label = "readOnlyText"
            )
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = readOnlyBg,
                border = if (isReadOnly) BorderStroke(1.dp, activeBorder) else null,
                shadowElevation = if (isReadOnly) 3.dp else 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onSelect(true) }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Read-only",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isReadOnly) FontWeight.Bold else FontWeight.SemiBold,
                        color = readOnlyTextColor
                    )
                }
            }

            // Option 2: Read & Write
            val readWriteBg by animateColorAsState(
                targetValue = if (!isReadOnly) activeCardBg else Color.Transparent,
                label = "readWriteBg"
            )
            val readWriteTextColor by animateColorAsState(
                targetValue = if (!isReadOnly) activeText else inactiveText,
                label = "readWriteText"
            )
            Surface(
                shape = RoundedCornerShape(9.dp),
                color = readWriteBg,
                border = if (!isReadOnly) BorderStroke(1.dp, activeBorder) else null,
                shadowElevation = if (!isReadOnly) 3.dp else 0.dp,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable { onSelect(false) }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Read & Write",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (!isReadOnly) FontWeight.Bold else FontWeight.SemiBold,
                        color = readWriteTextColor
                    )
                }
            }
        }
    }
}

