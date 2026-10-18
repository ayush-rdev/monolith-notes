package com.monolith.notes.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.monolith.notes.data.ai.GeminiApiClient
import com.monolith.notes.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun GeminiConfigDialog(
    initialApiKey: String,
    initialModel: String = "gemini-2.0-flash",
    onSave: (apiKey: String, model: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var apiKey by remember { mutableStateOf(initialApiKey) }
    var selectedModel by remember {
        mutableStateOf(if (initialModel == "gemini-1.5-flash" || initialModel.isBlank()) "gemini-2.0-flash" else initialModel)
    }
    var isKeyVisible by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<Boolean>?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF141418),
            border = BorderStroke(1.dp, Color(0xFF2C2C32))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF221A33)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Monolith Intelligence",
                                style = MonolithTypography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                ),
                                color = iOSTextPrimary
                            )
                            Text(
                                text = "Mathematical synthesis & note proofing",
                                style = MonolithTypography.bodySmall.copy(fontSize = 11.5.sp),
                                color = iOSTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = iOSTextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Value Proposition Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1A24))
                        .border(0.5.dp, Color(0xFF382F52), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Zero Fluff • 100% Client-to-Google Privacy",
                                style = MonolithTypography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = Color(0xFFA78BFA)
                            )
                        }
                        Text(
                            text = "Uses your personal Google AI Studio key directly from your device. Free tier provides 15 req/min with zero server intermediaries.",
                            style = MonolithTypography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                            color = iOSTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Model Selection
                Text(
                    text = "AI ENGINE MODEL",
                    style = MonolithTypography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        fontSize = 10.5.sp
                    ),
                    color = iOSTextTertiary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val models = listOf(
                        "gemini-2.0-flash" to "Gemini 2.0 Flash (Recommended)",
                        "gemini-2.0-flash-lite" to "Gemini 2.0 Flash Lite",
                        "gemini-2.5-flash" to "Gemini 2.5 Flash",
                        "gemini-1.5-flash-latest" to "Gemini 1.5 Flash (Legacy Alias)"
                    )
                    models.forEach { (id, label) ->
                        val isSelected = selectedModel == id
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedModel = id },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF2E2448) else Color(0xFF1E1E24),
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) Color(0xFFA78BFA) else Color(0xFF2C2C34)
                            )
                        ) {
                            Text(
                                text = label,
                                style = MonolithTypography.bodySmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                ),
                                color = if (isSelected) Color.White else iOSTextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // API Key Input
                Text(
                    text = "GOOGLE GEMINI API KEY",
                    style = MonolithTypography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        fontSize = 10.5.sp
                    ),
                    color = iOSTextTertiary
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        testResult = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "AIzaSy...",
                            color = iOSTextTertiary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    },
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF191920),
                        unfocusedContainerColor = Color(0xFF191920),
                        focusedBorderColor = Color(0xFFA78BFA),
                        unfocusedBorderColor = Color(0xFF2C2C34),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (apiKey.isBlank()) {
                                IconButton(
                                    onClick = {
                                        val clip = clipboardManager.getText()?.text.orEmpty().trim()
                                        if (clip.isNotBlank()) {
                                            apiKey = clip
                                            testResult = null
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "Paste",
                                        tint = Color(0xFFA78BFA),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle visibility",
                                        tint = iOSTextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(onClick = { apiKey = ""; testResult = null }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = iOSTextTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Get free API key link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://aistudio.google.com/app/apikey")
                            )
                            context.startActivity(intent)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Don't have a key? Get one for free →",
                        style = MonolithTypography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFFA78BFA)
                    )
                    Text(
                        text = "Google AI Studio",
                        style = MonolithTypography.labelSmall.copy(fontSize = 10.5.sp),
                        color = iOSTextTertiary
                    )
                }

                // Inline Test Status
                if (isTesting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF202028))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color(0xFFA78BFA),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Testing connection with Gemini...",
                            style = MonolithTypography.bodySmall.copy(fontSize = 11.5.sp),
                            color = iOSTextSecondary
                        )
                    }
                } else if (testResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val result = testResult!!
                    val isSuccess = result.isSuccess
                    val msg = if (isSuccess) "Connection verified! Model $selectedModel is ready." else result.exceptionOrNull()?.message ?: "Verification failed."

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSuccess) Color(0xFF14291E) else Color(0xFF331418))
                            .border(0.5.dp, if (isSuccess) Color(0xFF22C55E) else Color(0xFFEF4444), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (isSuccess) Color(0xFF22C55E) else Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = msg,
                            style = MonolithTypography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (isSuccess) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions: Test & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (apiKey.isBlank()) {
                                Toast.makeText(context, "Please enter an API Key first", Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
                            isTesting = true
                            testResult = null
                            coroutineScope.launch {
                                val res = GeminiApiClient.testConnection(apiKey, selectedModel)
                                isTesting = false
                                testResult = res
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF382F52)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFA78BFA)
                        )
                    ) {
                        Text(
                            text = "Test Key",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = {
                            onSave(apiKey.trim(), selectedModel)
                            Toast.makeText(context, if (apiKey.isNotBlank()) "Gemini Intelligence active" else "API Key cleared", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7C3AED),
                            contentColor = Color.White
                        )
                    ) {
                        Text(
                            text = "Save & Apply",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
