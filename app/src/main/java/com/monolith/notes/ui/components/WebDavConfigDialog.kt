package com.monolith.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monolith.notes.data.sync.WebDavClient
import com.monolith.notes.data.sync.WebDavConfig
import com.monolith.notes.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WebDavConfigDialog(
    initialConfig: WebDavConfig,
    onSave: (WebDavConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf(initialConfig.serverUrl) }
    var username by remember { mutableStateOf(initialConfig.username) }
    var password by remember { mutableStateOf(initialConfig.password) }
    var passwordVisible by remember { mutableStateOf(false) }

    var autoSync by remember { mutableStateOf(initialConfig.autoSyncOnLaunch) }
    var e2eeEnabled by remember { mutableStateOf(initialConfig.e2eeEnabled) }
    var passphrase by remember { mutableStateOf(initialConfig.encryptionPassphrase) }
    var passphraseVisible by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = iOSCardBackground,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(iOSYellow.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = iOSYellow,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "WebDAV Cloud Vault",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Nextcloud, ownCloud, or custom server",
                            fontSize = 12.sp,
                            color = iOSTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Server URL
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it; testResult = null },
                    label = { Text("Server WebDAV URL", color = iOSTextSecondary) },
                    placeholder = { Text("https://my-cloud.com/remote.php/dav/files/user/Vault/", color = iOSTextTertiary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = iOSYellow,
                        unfocusedBorderColor = iOSDivider,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Username
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; testResult = null },
                    label = { Text("Username", color = iOSTextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = iOSYellow,
                        unfocusedBorderColor = iOSDivider,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; testResult = null },
                    label = { Text("Password or App Token", color = iOSTextSecondary) },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility",
                                tint = iOSTextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = iOSYellow,
                        unfocusedBorderColor = iOSDivider,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Auto-Sync Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Sync on Launch",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Silently sync notes when opening app",
                            fontSize = 12.sp,
                            color = iOSTextSecondary
                        )
                    }
                    Switch(
                        checked = autoSync,
                        onCheckedChange = { autoSync = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = iOSYellow
                        )
                    )
                }

                HorizontalDivider(color = iOSDivider, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))

                // Zero-Knowledge E2EE Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Zero-Knowledge Encryption",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Client-side AES-256-GCM before upload",
                            fontSize = 12.sp,
                            color = iOSTextSecondary
                        )
                    }
                    Switch(
                        checked = e2eeEnabled,
                        onCheckedChange = { e2eeEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = iOSYellow
                        )
                    )
                }

                if (e2eeEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Encryption Passphrase", color = iOSTextSecondary) },
                        placeholder = { Text("Master passphrase for decryption", color = iOSTextTertiary) },
                        singleLine = true,
                        visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                Icon(
                                    imageVector = if (passphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle passphrase",
                                    tint = iOSTextSecondary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = iOSYellow,
                            unfocusedBorderColor = iOSDivider,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Remember this passphrase! If lost, encrypted notes cannot be recovered from the cloud.",
                        fontSize = 11.sp,
                        color = Color(0xFFFF9F0A),
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                }

                // Test Connection Status Message
                testResult?.let { (success, message) ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (success) Color(0x2234C759) else Color(0x22FF453A))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = message,
                            fontSize = 12.sp,
                            color = if (success) iOSEmerald else Color(0xFFFF453A),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Test Connection & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Test Button
                    OutlinedButton(
                        onClick = {
                            val candidate = WebDavConfig(
                                serverUrl = serverUrl.trim(),
                                username = username.trim(),
                                password = password.trim()
                            )
                            isTesting = true
                            testResult = null
                            coroutineScope.launch(Dispatchers.IO) {
                                val result = WebDavClient(candidate).testConnection()
                                withContext(Dispatchers.Main) {
                                    isTesting = false
                                    if (result.isSuccess) {
                                        testResult = Pair(true, "✓ ${result.getOrNull()}")
                                    } else {
                                        testResult = Pair(false, "✗ ${result.exceptionOrNull()?.message}")
                                    }
                                }
                            }
                        },
                        enabled = serverUrl.isNotBlank() && username.isNotBlank() && !isTesting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = iOSYellow
                            )
                        } else {
                            Text("Test", color = iOSYellow, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Save Button
                    Button(
                        onClick = {
                            val newConfig = WebDavConfig(
                                serverUrl = serverUrl.trim(),
                                username = username.trim(),
                                password = password.trim(),
                                e2eeEnabled = e2eeEnabled,
                                encryptionPassphrase = passphrase.trim(),
                                autoSyncOnLaunch = autoSync
                            )
                            onSave(newConfig)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = iOSYellow),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
