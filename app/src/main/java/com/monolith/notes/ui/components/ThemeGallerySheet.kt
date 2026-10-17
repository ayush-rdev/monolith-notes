package com.monolith.notes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.monolith.notes.data.AppSettings
import com.monolith.notes.ui.theme.AppTheme
import com.monolith.notes.ui.theme.MonolithTypography
import com.monolith.notes.ui.theme.ThemeCategory
import com.monolith.notes.ui.theme.iOSBackground
import com.monolith.notes.ui.theme.iOSCardBackground
import com.monolith.notes.ui.theme.iOSDivider
import com.monolith.notes.ui.theme.iOSElevatedCard
import com.monolith.notes.ui.theme.iOSTextPrimary
import com.monolith.notes.ui.theme.iOSTextSecondary
import com.monolith.notes.ui.theme.iOSTextTertiary
import com.monolith.notes.ui.theme.iOSYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeGallerySheet(
    settings: AppSettings,
    onUpdateTheme: (AppTheme) -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var selectedCategory by remember { mutableStateOf<ThemeCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredThemes = remember(selectedCategory, searchQuery) {
        AppTheme.entries.filter { theme ->
            val matchesCategory = selectedCategory == null || theme.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                theme.displayName.contains(searchQuery, ignoreCase = true) ||
                theme.subtitle.contains(searchQuery, ignoreCase = true) ||
                theme.category.displayName.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = iOSBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(iOSTextTertiary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Theme Gallery",
                        style = MonolithTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = iOSTextPrimary
                    )
                    Text(
                        text = "23 Curated Engineering & OLED Palettes",
                        style = MonolithTypography.labelMedium,
                        color = iOSTextSecondary
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

            // Search Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iOSCardBackground)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Themes",
                        tint = iOSTextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search themes (Nord, Dracula, OLED...)",
                                color = iOSTextTertiary,
                                fontSize = 14.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedTextColor = iOSTextPrimary,
                            unfocusedTextColor = iOSTextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = iOSTextTertiary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryPill(
                    label = "All (${AppTheme.entries.size})",
                    isSelected = selectedCategory == null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedCategory = null
                    }
                )
                ThemeCategory.entries.forEach { cat ->
                    val count = AppTheme.entries.count { it.category == cat }
                    CategoryPill(
                        label = "${cat.displayName} ($count)",
                        isSelected = selectedCategory == cat,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedCategory = cat
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Themes List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredThemes, key = { it.id }) { theme ->
                    val isSelected = settings.theme == theme
                    LiveNoteThemeCard(
                        theme = theme,
                        isSelected = isSelected,
                        onSelect = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUpdateTheme(theme)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) iOSYellow else iOSCardBackground
    val contentColor = if (isSelected) Color.Black else iOSTextPrimary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor
        )
    }
}

@Composable
private fun LiveNoteThemeCard(
    theme: AppTheme,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) theme.accent else theme.divider.copy(alpha = 0.5f),
        animationSpec = spring(),
        label = "border"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(theme.background)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onSelect)
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header Bar: Name, Subtitle, Category badge, Palette swatches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = theme.displayName,
                            style = MonolithTypography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = theme.textPrimary
                        )

                        // Light / Dark / OLED Badge
                        val badgeText = when {
                            !theme.isDark -> "LIGHT PAPER"
                            theme.category == ThemeCategory.OLED -> "OLED 0%"
                            theme.category == ThemeCategory.BRUTALIST -> "HARDWARE"
                            else -> "DEV CLASSIC"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(theme.accentContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MonolithTypography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = theme.accent
                            )
                        }
                    }
                    Text(
                        text = theme.subtitle,
                        style = MonolithTypography.bodySmall.copy(fontSize = 12.sp),
                        color = theme.textSecondary
                    )
                }

                // Palette Swatches
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SwatchDot(color = theme.background, borderColor = theme.textTertiary)
                    SwatchDot(color = theme.cardBackground, borderColor = theme.divider)
                    SwatchDot(color = theme.accent, borderColor = Color.Transparent)
                    SwatchDot(color = theme.textPrimary, borderColor = Color.Transparent)
                }
            }

            // Simulated Live Note Preview Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(theme.cardBackground)
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Note Title
                    Text(
                        text = "Real-time Vector Sync Engine",
                        style = MonolithTypography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        ),
                        color = theme.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Note Body Snippet
                    Text(
                        text = "3-way conflict-free sync with atomic disk journals and offline-first storage...",
                        style = MonolithTypography.bodySmall.copy(fontSize = 12.sp),
                        color = theme.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Live Interactive Checklist Row & Tag Chip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(theme.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (theme.isDark) Color.Black else Color.White,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            Text(
                                text = "Zero latency local disk cache",
                                style = MonolithTypography.bodySmall.copy(fontSize = 11.sp),
                                color = theme.textSecondary
                            )
                        }

                        // Tag Chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(theme.accentContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#architecture",
                                style = MonolithTypography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = theme.accent
                            )
                        }
                    }
                }
            }

            // Action / Selection Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${theme.category.displayName} • ${if (theme.isDark) "Dark Surface" else "Light Surface"}",
                    style = MonolithTypography.labelSmall.copy(fontSize = 11.sp),
                    color = theme.textTertiary
                )

                if (isSelected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = theme.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Active Theme",
                            style = MonolithTypography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            color = theme.accent
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(theme.elevatedCard)
                            .clickable(onClick = onSelect)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Apply",
                            style = MonolithTypography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            color = theme.accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwatchDot(
    color: Color,
    borderColor: Color
) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
            .border(0.5.dp, borderColor.copy(alpha = 0.5f), CircleShape)
    )
}
