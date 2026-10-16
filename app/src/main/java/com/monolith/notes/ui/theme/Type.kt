package com.monolith.notes.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.monolith.notes.R

// Genuine Apple San Francisco Typography Suite
val SFProDisplay = FontFamily(
    Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold)
)

val SFProText = FontFamily(
    Font(R.font.sf_pro_text_regular, FontWeight.Normal),
    Font(R.font.sf_pro_text_medium, FontWeight.Medium)
)

val SFProRounded = FontFamily(
    Font(R.font.sf_pro_rounded_bold, FontWeight.Bold)
)

val SFMono = FontFamily(
    Font(R.font.sf_mono_regular, FontWeight.Normal)
)

val MonolithTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = 0.37.sp,
        color = Color(0xFFFFFFFF)
    ),
    headlineLarge = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.36.sp,
        color = Color(0xFFFFFFFF)
    ),
    headlineMedium = TextStyle(
        fontFamily = SFProDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.35.sp,
        color = Color(0xFFFFFFFF)
    ),
    titleLarge = TextStyle(
        fontFamily = SFProText,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.41).sp,
        color = Color(0xFFFFFFFF)
    ),
    bodyLarge = TextStyle(
        fontFamily = SFProText,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.41).sp,
        color = Color(0xFFFFFFFF)
    ),
    bodyMedium = TextStyle(
        fontFamily = SFProText,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp,
        color = Color(0xFF8E8E93)
    ),
    labelMedium = TextStyle(
        fontFamily = SFProText,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp,
        color = Color(0xFF8E8E93)
    ),
    labelSmall = TextStyle(
        fontFamily = SFProText,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.07.sp,
        color = Color(0xFF636366)
    )
)
