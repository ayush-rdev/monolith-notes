package com.monolith.notes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.monolith.notes.data.sync.MonolithCloudClient
import com.monolith.notes.data.sync.MonolithCloudConfig
import com.monolith.notes.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MonolithCloudDialog(
    initialConfig: MonolithCloudConfig,
    onSave: (MonolithCloudConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var isRegisterMode by remember { mutableStateOf(!initialConfig.isLoggedIn) }
    var email by remember { mutableStateOf(initialConfig.email) }
    var password by remember { mutableStateOf(initialConfig.encryptionKey) }
    var passwordVisible by remember { mutableStateOf(false) }

    var serverUrl by remember { mutableStateOf(initialConfig.serverUrl) }
    var showAdvanced by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

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
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = iOSYellow,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Monolith Cloud Vault",
                                style = MonolithTypography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = iOSTextPrimary
                            )
                        }
                        Text(
                            text = "Zero-knowledge encrypted sync",
                            style = MonolithTypography.bodySmall,
                            color = iOSTextSecondary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = iOSTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // If already logged in: Account Status View
                if (initialConfig.isLoggedIn) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E24))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(iOSEmerald)
                                )
                                Text(
                                    text = "Connected & Encrypted",
                                    fontWeight = FontWeight.SemiBold,
                                    color = iOSEmerald,
                                    fontSize = 14.sp
                                )
                            }
                            Text(
                                text = "Account: ${initialConfig.email}",
                                color = iOSTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Server: ${initialConfig.serverUrl}",
                                color = iOSTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            onSave(initialConfig.copy(token = "", email = "", encryptionKey = ""))
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3A1C20),
                            contentColor = Color(0xFFFF453A)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Disconnect & Log Out", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    // Not logged in: Tab Switcher (Create Vault / Sign In)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF16161A))
                            .padding(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isRegisterMode) iOSYellow else Color.Transparent)
                                .clickable {
                                    isRegisterMode = true
                                    statusMessage = null
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create Free Vault",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isRegisterMode) Color.Black else iOSTextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isRegisterMode) iOSYellow else Color.Transparent)
                                .clickable {
                                    isRegisterMode = false
                                    statusMessage = null
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign In",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!isRegisterMode) Color.Black else iOSTextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Email Input
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            statusMessage = null
                        },
                        label = { Text("Email Address") },
                        placeholder = { Text("you@example.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = iOSYellow) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = iOSYellow,
                            unfocusedBorderColor = iOSDivider,
                            focusedTextColor = iOSTextPrimary,
                            unfocusedTextColor = iOSTextPrimary,
                            focusedContainerColor = Color(0xFF16161A),
                            unfocusedContainerColor = Color(0xFF16161A)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            statusMessage = null
                        },
                        label = { Text("Password") },
                        placeholder = { Text(if (isRegisterMode) "Create password (min 6 chars)" else "Your account password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = iOSYellow) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    tint = iOSTextSecondary
                                )
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = iOSYellow,
                            unfocusedBorderColor = iOSDivider,
                            focusedTextColor = iOSTextPrimary,
                            unfocusedTextColor = iOSTextPrimary,
                            focusedContainerColor = Color(0xFF16161A),
                            unfocusedContainerColor = Color(0xFF16161A)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // E2EE Notice Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF141923))
                            .border(1.dp, Color(0xFF1D3557), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF4EA8DE), modifier = Modifier.size(18.dp))
                            Text(
                                text = "End-to-End Encrypted (AES-256-GCM). Your password encrypts notes locally before syncing.",
                                color = Color(0xFFA8DADC),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Advanced Server Settings Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Advanced Server URL",
                            color = iOSTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = iOSTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = showAdvanced) {
                        Column {
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = serverUrl,
                                onValueChange = { serverUrl = it },
                                label = { Text("Sync Server URL") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = iOSYellow,
                                    unfocusedBorderColor = iOSDivider,
                                    focusedTextColor = iOSTextPrimary,
                                    unfocusedTextColor = iOSTextPrimary,
                                    focusedContainerColor = Color(0xFF16161A),
                                    unfocusedContainerColor = Color(0xFF16161A)
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Status Message
                    if (statusMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val (isSuccess, msg) = statusMessage!!
                        Text(
                            text = msg,
                            color = if (isSuccess) iOSEmerald else Color(0xFFFF453A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Submit Button
                    Button(
                        onClick = {
                            if (email.isBlank() || !email.contains("@")) {
                                statusMessage = false to "Please enter a valid email address."
                                return@Button
                            }
                            if (password.length < 6) {
                                statusMessage = false to "Password must be at least 6 characters."
                                return@Button
                            }

                            isLoading = true
                            statusMessage = null

                            coroutineScope.launch {
                                val result = if (isRegisterMode) {
                                    MonolithCloudClient.register(serverUrl, email, password)
                                } else {
                                    MonolithCloudClient.login(serverUrl, email, password)
                                }

                                isLoading = false

                                if (result.isSuccess && result.token != null) {
                                    statusMessage = true to result.message
                                    val newConfig = MonolithCloudConfig(
                                        serverUrl = serverUrl.trim(),
                                        email = result.email ?: email.trim().lowercase(),
                                        token = result.token,
                                        encryptionKey = password,
                                        autoSyncOnLaunch = true
                                    )
                                    onSave(newConfig)
                                    onDismiss()
                                } else {
                                    statusMessage = false to result.message
                                }
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = iOSYellow,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(if (isRegisterMode) "Creating Vault..." else "Signing In...", fontWeight = FontWeight.Bold)
                        } else {
                            Text(
                                text = if (isRegisterMode) "Create Cloud Vault" else "Sign In to Vault",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
