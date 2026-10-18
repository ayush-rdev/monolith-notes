package com.monolith.notes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.notes.data.ai.GeminiApiClient
import com.monolith.notes.data.ai.NotePointAnalysis
import com.monolith.notes.data.ai.NoteWikilinkSuggestion
import com.monolith.notes.model.Note
import com.monolith.notes.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteAnalysisSheet(
    note: Note,
    allNotes: List<Note>,
    apiKey: String,
    aiModel: String = "gemini-2.0-flash",
    onInsertWikilink: (String) -> Unit,
    onAppendAction: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<NotePointAnalysis?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val isKeyConfigured = apiKey.isNotBlank()

    // Auto-run once when sheet opens if key is available
    LaunchedEffect(note.id) {
        if (isKeyConfigured && analysisResult == null && !isAnalyzing && note.content.isNotBlank()) {
            isAnalyzing = true
            errorMessage = null
            val vaultTitles = allNotes.map { it.title }.filter { it.isNotBlank() }
            val res = GeminiApiClient.analyzeNotePointByPoint(
                apiKey = apiKey,
                model = aiModel,
                note = note,
                vaultTitles = vaultTitles
            )
            isAnalyzing = false
            if (res.isSuccess) {
                analysisResult = res.getOrNull()
            } else {
                errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Analysis failed."
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0F0F13),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF33333E))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0xFF231B38)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Note Insights",
                            style = MonolithTypography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = iOSTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Connections, takeaways & suggestions",
                            style = MonolithTypography.bodySmall.copy(fontSize = 11.sp),
                            color = iOSTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Done",
                        color = Color(0xFFA78BFA),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // State 1: Key not configured
            if (!isKeyConfigured) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF181820))
                        .border(1.dp, Color(0xFF2D2D3A), RoundedCornerShape(14.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Gemini API Key Required",
                                style = MonolithTypography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = iOSTextPrimary
                            )
                        }
                        Text(
                            text = "Connect Google's free-tier Gemini API in Settings to discover connections across your vault, key takeaways, and action items.",
                            style = MonolithTypography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                            color = iOSTextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenSettings()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7C3AED),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Configure API Key in Settings", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                return@Column
            }

            // State 2: Progress indicator while analyzing
            if (isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF161520))
                        .border(0.5.dp, Color(0xFF3B2F58), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFFA78BFA),
                            strokeWidth = 2.dp
                        )
                        Column {
                            Text(
                                text = "Analyzing note...",
                                style = MonolithTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                                color = iOSTextPrimary
                            )
                            Text(
                                text = "Finding connections across ${allNotes.size} vault notes",
                                style = MonolithTypography.bodySmall.copy(fontSize = 11.sp),
                                color = iOSTextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // State 3: Error
            if (errorMessage != null && !isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF281316))
                        .border(0.5.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Analysis Failed", style = MonolithTypography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFFCA5A5))
                        }
                        Text(errorMessage!!, style = MonolithTypography.bodySmall.copy(fontSize = 11.5.sp), color = Color(0xFFFCA5A5))
                        Button(
                            onClick = {
                                isAnalyzing = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val vaultTitles = allNotes.map { it.title }.filter { it.isNotBlank() }
                                    val res = GeminiApiClient.analyzeNotePointByPoint(
                                        apiKey = apiKey,
                                        model = aiModel,
                                        note = note,
                                        vaultTitles = vaultTitles
                                    )
                                    isAnalyzing = false
                                    if (res.isSuccess) {
                                        analysisResult = res.getOrNull()
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Analysis failed."
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF451A1D))
                        ) {
                            Text("Retry Analysis", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // State 4: Results
            val result = analysisResult
            if (result != null) {
                // Section: Verified Vault Wikilinks
                if (result.suggestedWikilinks.isNotEmpty()) {
                    SectionTitle(
                        title = "RELATED VAULT NOTES",
                        count = result.suggestedWikilinks.size,
                        badgeColor = Color(0xFFA78BFA)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        result.suggestedWikilinks.forEach { link ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Color(0xFF171720))
                                    .border(0.5.dp, Color(0xFF2C2C38), RoundedCornerShape(11.dp))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "[[${link.vaultTitle}]]",
                                            style = MonolithTypography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.5.sp,
                                                fontFamily = FontFamily.Monospace
                                            ),
                                            color = Color(0xFFA78BFA)
                                        )

                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onInsertWikilink("[[${link.vaultTitle}]]")
                                                },
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF261D3A),
                                            border = BorderStroke(0.5.dp, Color(0xFF4C3875))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AddLink,
                                                    contentDescription = null,
                                                    tint = Color(0xFFA78BFA),
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = "LINK",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFA78BFA)
                                                )
                                            }
                                        }
                                    }

                                    if (link.relevance.isNotBlank()) {
                                        Text(
                                            text = link.relevance,
                                            style = MonolithTypography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                                            color = iOSTextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Section: Key Takeaways
                if (result.premises.isNotEmpty()) {
                    SectionTitle(
                        title = "KEY TAKEAWAYS",
                        count = result.premises.size,
                        badgeColor = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        result.premises.forEachIndexed { idx, p ->
                            AnalyticBulletCard(
                                indexLabel = "${idx + 1}",
                                labelColor = Color(0xFF38BDF8),
                                text = p
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Section: Logical Flow
                if (result.inferenceSteps.isNotEmpty()) {
                    SectionTitle(
                        title = "LOGICAL FLOW",
                        count = result.inferenceSteps.size,
                        badgeColor = Color(0xFF4ADE80)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        result.inferenceSteps.forEachIndexed { idx, step ->
                            AnalyticBulletCard(
                                indexLabel = "${idx + 1}",
                                labelColor = Color(0xFF4ADE80),
                                text = step
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Section: Questions to Consider
                if (result.logicalTensions.isNotEmpty()) {
                    SectionTitle(
                        title = "QUESTIONS TO CONSIDER",
                        count = result.logicalTensions.size,
                        badgeColor = Color(0xFFF87171)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        result.logicalTensions.forEach { tension ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF221417))
                                    .border(0.5.dp, Color(0xFF4A2328), RoundedCornerShape(10.dp))
                                    .padding(11.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.HelpOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(15.dp).padding(top = 2.dp)
                                    )
                                    Text(
                                        text = tension,
                                        style = MonolithTypography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                                        color = Color(0xFFFECACA)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Section: Action Items
                if (result.actionVectors.isNotEmpty()) {
                    SectionTitle(
                        title = "ACTION ITEMS",
                        count = result.actionVectors.size,
                        badgeColor = Color(0xFFFBBF24)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        result.actionVectors.forEach { action ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF191820))
                                    .border(0.5.dp, Color(0xFF2E2B38), RoundedCornerShape(10.dp))
                                    .padding(11.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircleOutline,
                                            contentDescription = null,
                                            tint = Color(0xFFFBBF24),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = action,
                                            style = MonolithTypography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                            color = iOSTextPrimary
                                        )
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onAppendAction(action)
                                            },
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF282315),
                                        border = BorderStroke(0.5.dp, Color(0xFF4D4023))
                                    ) {
                                        Text(
                                            text = "+ TODO",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFBBF24),
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Re-analyze action button
                OutlinedButton(
                    onClick = {
                        isAnalyzing = true
                        errorMessage = null
                        coroutineScope.launch {
                            val vaultTitles = allNotes.map { it.title }.filter { it.isNotBlank() }
                            val res = GeminiApiClient.analyzeNotePointByPoint(
                                apiKey = apiKey,
                                model = aiModel,
                                note = note,
                                vaultTitles = vaultTitles
                            )
                            isAnalyzing = false
                            if (res.isSuccess) {
                                analysisResult = res.getOrNull()
                            } else {
                                errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Analysis failed."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(1.dp, Color(0xFF2C2C38)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA78BFA))
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refresh Insights", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    count: Int,
    badgeColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MonolithTypography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp,
                letterSpacing = 0.8.sp
            ),
            color = iOSTextTertiary
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(badgeColor.copy(alpha = 0.16f))
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Text(
                text = "$count",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = badgeColor
            )
        }
    }
}

@Composable
private fun AnalyticBulletCard(
    indexLabel: String,
    labelColor: Color,
    text: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF15151D))
            .border(0.5.dp, Color(0xFF252530), RoundedCornerShape(9.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(labelColor.copy(alpha = 0.14f))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = indexLabel,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = labelColor
                )
            }
            Text(
                text = text,
                style = MonolithTypography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = iOSTextPrimary
            )
        }
    }
}
