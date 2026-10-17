package com.monolith.notes.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Minimal, ultra-responsive theme chooser page with live visual previews.
 * Features 2-column grid of compact theme cards displaying real palette swatches,
 * category filters, zero-latency switching, and fluid 120 FPS scrolling.
 */
@Composable
fun ThemeChooserPage(
    currentTheme: AppTheme,
    onSelectTheme: (AppTheme) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var selectedCategory by remember { mutableStateOf<ThemeCategory?>(null) }

    // Intercept back gesture to return cleanly
    BackHandler {
        onBack()
    }

    val filteredThemes = remember(selectedCategory) {
        if (selectedCategory == null) {
            AppTheme.entries
        } else {
            AppTheme.entries.filter { it.category == selectedCategory }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        // Navigation Bar (< Settings | Themes | Done)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back to Settings button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back to Settings",
                    tint = iOSYellow,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "Settings",
                    color = iOSYellow,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            Text(
                text = "Themes",
                style = MonolithTypography.headlineMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                color = iOSTextPrimary
            )

            TextButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDone()
                },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Done",
                    color = iOSYellow,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Active Theme Status Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iOSCardBackground)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(currentTheme.accent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Active: ",
                style = MonolithTypography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
                color = iOSTextSecondary
            )
            Text(
                text = "${currentTheme.displayName} • ${currentTheme.subtitle}",
                style = MonolithTypography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                color = iOSTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // Minimal Category Filter Chips
        val categoryScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(categoryScrollState)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val allCount = AppTheme.entries.size
            CategoryFilterChip(
                label = "All ($allCount)",
                isSelected = selectedCategory == null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedCategory = null
                }
            )

            ThemeCategory.entries.forEach { category ->
                val count = AppTheme.entries.count { it.category == category }
                val label = when (category) {
                    ThemeCategory.OLED -> "OLED ($count)"
                    ThemeCategory.CLASSICS -> "Classics ($count)"
                    ThemeCategory.BRUTALIST -> "Industrial ($count)"
                    ThemeCategory.PAPER -> "Warm Paper ($count)"
                }
                CategoryFilterChip(
                    label = label,
                    isSelected = selectedCategory == category,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedCategory = if (selectedCategory == category) null else category
                    }
                )
            }
        }

        // 2-Column Grid of Theme Preview Tiles (Ultra-fast, recycling LazyVerticalGrid)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filteredThemes, key = { it.id }) { theme ->
                val isSelected = theme == currentTheme
                ThemePreviewCard(
                    theme = theme,
                    isSelected = isSelected,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelectTheme(theme)
                    }
                )
            }
        }
    }
}

/**
 * Filter Chip for Theme Categories
 */
@Composable
private fun CategoryFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) iOSYellow else iOSCardBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MonolithTypography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) Color.Black else iOSTextSecondary
        )
    }
}

/**
 * Visual Theme Preview Card:
 * Renders a compact, high-fidelity miniature note with authentic background, card surface,
 * accent dot, text colors, and 4-dot palette swatch.
 */
@Composable
private fun ThemePreviewCard(
    theme: AppTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        theme.accent
    } else {
        theme.divider.copy(alpha = 0.5f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(theme.cardBackground)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Miniature Note Mockup Canvas (Visual live preview)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
                    .clip(RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp))
                    .background(theme.background)
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Mini header: accent dot + simulated title bar + accent tag
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(theme.accent)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .height(5.dp)
                                .width(44.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(theme.textPrimary)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .height(5.dp)
                                .width(16.dp)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(theme.accentContainer)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Simulated body lines
                    Box(
                        modifier = Modifier
                            .height(3.5.dp)
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(theme.textSecondary.copy(alpha = 0.75f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .height(3.5.dp)
                            .fillMaxWidth(0.55f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(theme.textTertiary.copy(alpha = 0.55f))
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Bottom Palette Swatch Bar (4 core colors: Background, Card, Accent, Text)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Background dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(theme.background)
                                .border(0.5.dp, theme.divider, CircleShape)
                        )
                        // Card surface dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(theme.cardBackground)
                                .border(0.5.dp, theme.divider, CircleShape)
                        )
                        // Accent dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(theme.accent)
                        )
                        // Text primary dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(theme.textPrimary)
                        )
                    }
                }
            }

            // Theme Label & Selection Status Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = theme.displayName,
                        style = MonolithTypography.titleSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = iOSTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = theme.subtitle,
                        style = MonolithTypography.bodySmall.copy(fontSize = 10.sp),
                        color = iOSTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Selection Indicator Badge
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(theme.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        val iconTint = if (theme.accent.luminance() > 0.45f) Color.Black else Color.White
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = iconTint,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .border(1.dp, iOSTextTertiary.copy(alpha = 0.35f), CircleShape)
                    )
                }
            }
        }
    }
}
