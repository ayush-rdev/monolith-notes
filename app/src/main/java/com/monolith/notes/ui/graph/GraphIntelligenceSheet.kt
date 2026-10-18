package com.monolith.notes.ui.graph

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.notes.model.Note
import com.monolith.notes.data.ai.GeminiApiClient
import com.monolith.notes.data.ai.VaultTopologySynthesis
import com.monolith.notes.data.ai.VaultBridgeProposal
import com.monolith.notes.data.ai.VaultBottleneck
import com.monolith.notes.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Intelligent Vault Analytics & Inspector Sheet.
 * Accessible via the 'i' button in Knowledge Graph. Displays vault health metrics,
 * ranked most-connected notes (top hubs with 1-tap focus), cluster breakdown,
 * and live mathematical Gemini AI synthesis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphIntelligenceSheet(
    analytics: GraphAnalytics,
    nodes: List<GraphNode>,
    notes: List<Note> = emptyList(),
    apiKey: String = "",
    aiModel: String = "gemini-2.0-flash",
    onConfigureApiKey: () -> Unit = {},
    onSelectHub: (GraphNode) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = iOSBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(iOSTextTertiary)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = iOSYellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vault Intelligence",
                        style = MonolithTypography.headlineMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                        color = iOSTextPrimary
                    )
                }

                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Done",
                        color = iOSYellow,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Overview KPI Cards (2x2 Grid)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    title = "Total Notes",
                    value = "${analytics.totalNotes}",
                    subtitle = "${analytics.orphanCount} isolated",
                    icon = Icons.Default.Description,
                    iconTint = Color(0xFF0A84FF),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Wikilinks",
                    value = "${analytics.totalLinks}",
                    subtitle = "${analytics.hubCount} hub nodes",
                    icon = Icons.Default.Link,
                    iconTint = iOSYellow,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    title = "Graph Density",
                    value = "${analytics.densityPercentage}%",
                    subtitle = if (analytics.densityPercentage > 15) "Strongly interconnected" else "Growing network",
                    icon = Icons.Default.Grain,
                    iconTint = iOSEmerald,
                    modifier = Modifier.weight(1f)
                )
                val connectedRatio = if (analytics.totalNotes > 0) {
                    ((analytics.totalNotes - analytics.orphanCount) * 100) / analytics.totalNotes
                } else 0
                KpiCard(
                    title = "Connectedness",
                    value = "$connectedRatio%",
                    subtitle = "${analytics.totalNotes - analytics.orphanCount} connected notes",
                    icon = Icons.Default.Share,
                    iconTint = Color(0xFFBF5AF2),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Most Connected Notes (Top Hubs / Most Showed Up)
            Text(
                text = "MOST CONNECTED NOTES",
                style = MonolithTypography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                color = iOSTextSecondary,
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
            )

            val sortedHubs = analytics.topHubs.ifEmpty {
                nodes.filter { it.degree > 0 }.sortedByDescending { it.degree }.take(5)
            }

            if (sortedHubs.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(iOSCardBackground)
                ) {
                    Column {
                        sortedHubs.forEachIndexed { index, hubNode ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectHub(hubNode)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Rank Badge
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(if (index == 0) iOSYellow.copy(alpha = 0.2f) else iOSTextTertiary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (index == 0) iOSYellow else iOSTextSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hubNode.note.displayTitle,
                                        style = MonolithTypography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                        color = iOSTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${hubNode.degree} total links (${hubNode.inDegree} in • ${hubNode.outDegree} out) • ${hubNode.clusterName}",
                                        style = MonolithTypography.bodySmall.copy(fontSize = 12.sp),
                                        color = iOSTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Focus",
                                    tint = iOSTextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (index < sortedHubs.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 50.dp),
                                    thickness = 0.5.dp,
                                    color = iOSDivider
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(iOSCardBackground)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No linked notes found yet. Use [[wikilinks]] in your notes to connect thoughts organically.",
                        style = MonolithTypography.bodyMedium.copy(fontSize = 13.sp),
                        color = iOSTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Mathematical Vault Topology & Synthesis (Gemini API)
            var isSynthesizing by remember { mutableStateOf(false) }
            var synthesisResult by remember { mutableStateOf<VaultTopologySynthesis?>(null) }
            var synthesisError by remember { mutableStateOf<String?>(null) }

            val isKeyConfigured = apiKey.isNotBlank()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF14131A))
                    .border(1.dp, Color(0xFF2E2545), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Header Row
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
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2E2248)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFA78BFA),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Mathematical Vault Synthesis",
                                    style = MonolithTypography.titleMedium.copy(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = iOSTextPrimary
                                )
                                Text(
                                    text = "Topological gap analysis & cross-cluster bridges",
                                    style = MonolithTypography.bodySmall.copy(fontSize = 11.sp),
                                    color = iOSTextSecondary
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF281F3D))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isKeyConfigured) "GEMINI 2.0" else "API KEY REQ",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA78BFA)
                            )
                        }
                    }

                    if (!isKeyConfigured) {
                        Text(
                            text = "Connect Google's free-tier Gemini API in Settings to calculate topological bridges between clusters, detect single-point bottlenecks, and audit vault entropy.",
                            style = MonolithTypography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = iOSTextSecondary
                        )

                        Button(
                            onClick = {
                                onDismiss()
                                onConfigureApiKey()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF7C3AED),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Key, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Configure Gemini API Key in Settings", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        // Key is configured
                        if (isSynthesizing) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1C1926))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFFA78BFA),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Synthesizing topology & PageRank matrices with $aiModel...",
                                    style = MonolithTypography.bodySmall.copy(fontSize = 11.5.sp),
                                    color = iOSTextPrimary
                                )
                            }
                        } else if (synthesisResult == null) {
                            Text(
                                text = "Analyze ${nodes.size} nodes and ${analytics.totalLinks} directed links with deterministic mathematical synthesis.",
                                style = MonolithTypography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                color = iOSTextSecondary
                            )

                            Button(
                                onClick = {
                                    isSynthesizing = true
                                    synthesisError = null
                                    coroutineScope.launch {
                                        val res = GeminiApiClient.synthesizeVaultTopology(
                                            apiKey = apiKey,
                                            model = aiModel,
                                            notes = notes.ifEmpty { nodes.map { it.note } },
                                            analytics = analytics,
                                            nodes = nodes
                                        )
                                        isSynthesizing = false
                                        if (res.isSuccess) {
                                            synthesisResult = res.getOrNull()
                                        } else {
                                            synthesisError = res.exceptionOrNull()?.localizedMessage ?: "Synthesis failed."
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF7C3AED),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Run Mathematical Synthesis", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (synthesisError != null) {
                            Text(
                                text = "Error: $synthesisError",
                                style = MonolithTypography.bodySmall.copy(fontSize = 11.5.sp),
                                color = Color(0xFFFCA5A5)
                            )
                        }

                        val syn = synthesisResult
                        if (syn != null) {
                            // 1. Structural Assessment
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E1C29))
                                    .padding(12.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "STRUCTURAL CRITIQUE",
                                        style = MonolithTypography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = Color(0xFFA78BFA)
                                    )
                                    Text(
                                        text = syn.structuralAssessment,
                                        style = MonolithTypography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                        color = iOSTextPrimary
                                    )
                                }
                            }

                            // 2. Cross-Cluster Bridges
                            if (syn.bridgeProposals.isNotEmpty()) {
                                Text(
                                    text = "CROSS-CLUSTER BRIDGES (${syn.bridgeProposals.size})",
                                    style = MonolithTypography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = Color(0xFF38BDF8)
                                )

                                syn.bridgeProposals.forEach { bridge ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF171922))
                                            .border(0.5.dp, Color(0xFF263345), RoundedCornerShape(10.dp))
                                            .padding(11.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${bridge.sourceTitle} ⟷ ${bridge.targetTitle}",
                                                    style = MonolithTypography.bodySmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    ),
                                                    color = Color(0xFF7DD3FC),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF172B38))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "${(bridge.affinityScore * 100).toInt()}% affinity",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF38BDF8)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = bridge.rationale,
                                                style = MonolithTypography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                                color = iOSTextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Bottlenecks
                            if (syn.bottlenecks.isNotEmpty()) {
                                Text(
                                    text = "STRUCTURAL BOTTLENECKS",
                                    style = MonolithTypography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = Color(0xFFF87171)
                                )

                                syn.bottlenecks.forEach { b ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFF221417))
                                            .border(0.5.dp, Color(0xFF452228), RoundedCornerShape(10.dp))
                                            .padding(11.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = b.noteTitle,
                                                    style = MonolithTypography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                                                    color = Color(0xFFFCA5A5)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color(0xFF3D191F))
                                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                                ) {
                                                    Text(text = b.severity, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                                                }
                                            }
                                            Text(
                                                text = b.vulnerability,
                                                style = MonolithTypography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                                color = Color(0xFFFECACA)
                                            )
                                        }
                                    }
                                }
                            }

                            // Re-run button
                            OutlinedButton(
                                onClick = {
                                    isSynthesizing = true
                                    synthesisError = null
                                    coroutineScope.launch {
                                        val res = GeminiApiClient.synthesizeVaultTopology(
                                            apiKey = apiKey,
                                            model = aiModel,
                                            notes = notes.ifEmpty { nodes.map { it.note } },
                                            analytics = analytics,
                                            nodes = nodes
                                        )
                                        isSynthesizing = false
                                        if (res.isSuccess) {
                                            synthesisResult = res.getOrNull()
                                        } else {
                                            synthesisError = res.exceptionOrNull()?.localizedMessage ?: "Synthesis failed."
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0xFF352B4D)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFA78BFA))
                            ) {
                                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Re-run Synthesis", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(iOSCardBackground)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MonolithTypography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    color = iOSTextSecondary
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                style = MonolithTypography.headlineMedium.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                color = iOSTextPrimary
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MonolithTypography.bodySmall.copy(fontSize = 11.sp),
                color = iOSTextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
