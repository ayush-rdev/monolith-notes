package com.monolith.notes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.widget.Toast
import com.monolith.notes.data.GoogleDriveConfig
import com.monolith.notes.ui.theme.*

@Composable
fun GoogleDriveDialog(
    initialConfig: GoogleDriveConfig,
    onConnectGoogle: () -> Unit,
    onDisconnectGoogle: () -> Unit,
    onSaveConfig: (GoogleDriveConfig) -> Unit,
    onDismiss: () -> Unit,
    showDevHelpInitially: Boolean = false
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showDevHelp by remember { mutableStateOf(showDevHelpInitially) }
    var autoSync by remember { mutableStateOf(initialConfig.autoSyncOnLaunch) }
    var customPassphrase by remember { mutableStateOf(initialConfig.encryptionKey) }
    var passphraseVisible by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = iOSCardBackground,
            modifier = Modifier
                .fillMaxWidth(0.92f)
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
                            .background(Color(0xFF4285F4).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = Color(0xFF4285F4),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Drive Vault",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = iOSTextPrimary
                        )
                        Text(
                            text = "Free 15GB Cloud Storage • Zero Server Cost",
                            fontSize = 12.sp,
                            color = iOSTextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = iOSTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // End-to-End Encryption Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E2838))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF4285F4),
                            modifier = Modifier.size(18.dp).padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "Client-Side AES-256-GCM Encryption",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4285F4)
                            )
                            Text(
                                "All notes and attachments are encrypted before uploading to your private hidden Google Drive folder. Google cannot read your data.",
                                fontSize = 11.sp,
                                color = Color(0xFF8AB4F8),
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (initialConfig.isConnected) {
                    // Connected State
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF16161A))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = iOSEmerald, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connected to Google Drive", fontWeight = FontWeight.SemiBold, color = iOSTextPrimary, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(initialConfig.email, color = iOSTextSecondary, fontSize = 13.sp)
                            if (initialConfig.displayName.isNotBlank()) {
                                Text(initialConfig.displayName, color = iOSTextTertiary, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Auto sync toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF16161A))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Sync on Launch", fontSize = 14.sp, color = iOSTextPrimary)
                            Text("Synchronize notes whenever Monolith opens", fontSize = 11.sp, color = iOSTextSecondary)
                        }
                        Switch(
                            checked = autoSync,
                            onCheckedChange = {
                                autoSync = it
                                onSaveConfig(initialConfig.copy(autoSyncOnLaunch = it))
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = iOSYellow, checkedTrackColor = iOSYellow.copy(alpha = 0.5f))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Custom Passphrase (Optional for cross-device manual keys)
                    OutlinedTextField(
                        value = customPassphrase,
                        onValueChange = {
                            customPassphrase = it
                            onSaveConfig(initialConfig.copy(encryptionKey = it))
                        },
                        label = { Text("Custom E2EE Passphrase (Optional)", fontSize = 12.sp) },
                        placeholder = { Text("Default: Auto-derived from Google Account", fontSize = 11.sp, color = iOSTextTertiary) },
                        visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                Icon(
                                    if (passphraseVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = iOSTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = iOSYellow,
                            unfocusedBorderColor = iOSDivider,
                            focusedTextColor = iOSTextPrimary,
                            unfocusedTextColor = iOSTextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            onDisconnectGoogle()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3A1C20),
                            contentColor = Color(0xFFFF453A)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(46.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect Google Account", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    // Not Connected State: 1-Tap Sign In with Google
                    Button(
                        onClick = {
                            onConnectGoogle()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4285F4),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Sign In with Google", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Tap to choose your Google account. Your notes will sync directly with your own Google Drive quota.",
                        fontSize = 11.sp,
                        color = iOSTextSecondary,
                        lineHeight = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Developer Setup / SHA-1 Helper (Collapsible)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF141416))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDevHelp = !showDevHelp },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = iOSTextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Cloud OAuth Registration Details", fontSize = 12.sp, color = iOSTextSecondary, fontWeight = FontWeight.Medium)
                        }
                        Icon(
                            if (showDevHelp) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = iOSTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = showDevHelp) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                "To allow Google Sign-In, register an Android OAuth Client ID in Google Cloud Console:",
                                fontSize = 11.sp,
                                color = iOSTextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Package Name:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = iOSTextPrimary)
                            Text("com.monolith.notes", fontSize = 11.sp, color = iOSYellow)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("SHA-1 Certificate Fingerprint:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = iOSTextPrimary)
                            Text("C9:9A:48:F3:FE:CB:1A:F8:7F:73:18:7D:0E:78:29:0B:C3:4C:E4:89", fontSize = 10.sp, color = iOSYellow)

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val details = "Package Name: com.monolith.notes\nSHA-1: C9:9A:48:F3:FE:CB:1A:F8:7F:73:18:7D:0E:78:29:0B:C3:4C:E4:89"
                                    clipboardManager.setText(AnnotatedString(details))
                                    Toast.makeText(context, "Copied credentials to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24242A), contentColor = iOSTextPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Package & SHA-1", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
