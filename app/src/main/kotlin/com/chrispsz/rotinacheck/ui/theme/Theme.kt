package com.chrispsz.rotinacheck.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// AMOLED theme: TRUE black #000000 backgrounds — pixels fully off, zero
// battery draw on OLED panels. Same warm-amber accent family as SleepFlex
// (no blue/indigo: night screens must suppress melanopic stimulation).
// ---------------------------------------------------------------------------

private val Amber = Color(0xFFF2B36B)
private val OnBlack = Color(0xFFF2EDE4)
private val Muted = Color(0xFF9E968A)
private val Card = Color(0xFF141414)
private val CardHigh = Color(0xFF1E1B16)
private val Outline = Color(0xFF2E2A24)
private val Done = Color(0xFF8FCB9B)
private val Danger = Color(0xFFE57373)

private val AmoledColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF241703),
    primaryContainer = Color(0xFF3A2A0E),
    onPrimaryContainer = Color(0xFFFFDDB4),
    secondary = Done,
    onSecondary = Color(0xFF122616),
    secondaryContainer = Color(0xFF1D3322),
    onSecondaryContainer = Color(0xFFC8E8CE),
    tertiary = Danger,
    onTertiary = Color(0xFF2E1512),
    error = Danger,
    background = Color.Black,
    onBackground = OnBlack,
    surface = Color.Black,
    onSurface = OnBlack,
    surfaceVariant = Card,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Card,
    surfaceContainer = Card,
    surfaceContainerHigh = CardHigh,
    surfaceContainerHighest = CardHigh,
    outline = Outline,
    outlineVariant = Outline,
)

private val RotinaCheckTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
)

/**
 * The app is night-first and AMOLED-first: ALWAYS dark, regardless of the
 * system setting — a checklist consulted at 05:00 must not blind the user.
 */
@Composable
fun RotinaCheckTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledColors,
        typography = RotinaCheckTypography,
        content = content,
    )
}
