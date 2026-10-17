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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monolith.notes.ui.theme.*

/**
 * Intelligent Vault Analytics & Inspector Sheet.
 * Accessible via the 'i' button in Knowledge Graph. Displays vault health metrics,
 * ranked most-connected notes (top hubs with 1-tap focus), cluster breakdown,
 * and future AI synthesis card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphIntelligenceSheet(
    analytics: GraphAnalytics,
    nodes: List<GraphNode>,
    onSelectHub: (GraphNode) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

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

            // 3. AI Vault Analysis Preview (Prepared for BYO API Key)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(iOSCardBackground)
                    .border(1.dp, iOSYellow.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(iOSYellow.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = iOSYellow,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "AI Vault Synthesis",
                                style = MonolithTypography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                color = iOSTextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(iOSYellow.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "COMING SOON",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = iOSYellow
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Connect your personal LLM API key to automatically analyze knowledge clusters, uncover hidden conceptual connections between isolated notes, and synthesize vault insights.",
                            style = MonolithTypography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                            color = iOSTextSecondary
                        )
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
