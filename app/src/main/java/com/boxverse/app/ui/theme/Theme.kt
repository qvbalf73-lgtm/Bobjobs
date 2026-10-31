package com.boxverse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boxverse.app.data.prefs.ThemeMode
import com.boxverse.app.domain.Rarity

/* ================= 设计规范：色彩 =================
 * 主色：柔和薰衣草紫；辅色：梦幻粉；背景：奶油白；金色：稀有款专用。
 */
object Palette {
    val Lavender = Color(0xFF8F7BEF)
    val LavenderSoft = Color(0xFFB9A8FF)
    val LavenderMist = Color(0xFFEDE7FF)
    val Pink = Color(0xFFF59AC4)
    val PinkSoft = Color(0xFFFFD3E6)
    val PinkMist = Color(0xFFFFEEF5)
    val Cream = Color(0xFFFFF9F3)
    val CreamDeep = Color(0xFFFFF1E4)
    val Gold = Color(0xFFE2AE45)
    val GoldSoft = Color(0xFFFFE6A6)
    val Ink = Color(0xFF2A2340)
    val InkSoft = Color(0xFF6E6688)
    val Mint = Color(0xFF5CC8A0)
    val Coral = Color(0xFFFF7A8A)
    val Night = Color(0xFF15121F)
    val NightSurface = Color(0xFF1F1A2E)
    val NightSurfaceHigh = Color(0xFF2A2440)
}

private val LightColors = lightColorScheme(
    primary = Palette.Lavender,
    onPrimary = Color.White,
    primaryContainer = Palette.LavenderMist,
    onPrimaryContainer = Color(0xFF2E2266),
    secondary = Palette.Pink,
    onSecondary = Color.White,
    secondaryContainer = Palette.PinkMist,
    onSecondaryContainer = Color(0xFF5A1F3B),
    tertiary = Palette.Gold,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF3D6),
    onTertiaryContainer = Color(0xFF5A4300),
    background = Palette.Cream,
    onBackground = Palette.Ink,
    surface = Color.White,
    onSurface = Palette.Ink,
    surfaceVariant = Color(0xFFF4F0FB),
    onSurfaceVariant = Palette.InkSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFCF8),
    surfaceContainer = Color(0xFFFAF6F9),
    surfaceContainerHigh = Color(0xFFF5F0F8),
    surfaceContainerHighest = Color(0xFFEFE9F5),
    outline = Color(0xFFD5CCE8),
    outlineVariant = Color(0xFFEAE4F3),
    error = Color(0xFFE5486A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBBAEFF),
    onPrimary = Color(0xFF261A5E),
    primaryContainer = Color(0xFF3B3170),
    onPrimaryContainer = Color(0xFFE8E2FF),
    secondary = Color(0xFFFFB0D2),
    onSecondary = Color(0xFF4F1532),
    secondaryContainer = Color(0xFF5B2A44),
    onSecondaryContainer = Color(0xFFFFD9E8),
    tertiary = Color(0xFFF0C766),
    onTertiary = Color(0xFF3E2E00),
    tertiaryContainer = Color(0xFF4A3A10),
    onTertiaryContainer = Color(0xFFFFE6A6),
    background = Palette.Night,
    onBackground = Color(0xFFECE6F7),
    surface = Palette.NightSurface,
    onSurface = Color(0xFFECE6F7),
    surfaceVariant = Color(0xFF2B2540),
    onSurfaceVariant = Color(0xFFB9B0D0),
    surfaceContainerLowest = Color(0xFF120F1A),
    surfaceContainerLow = Color(0xFF1B1728),
    surfaceContainer = Palette.NightSurface,
    surfaceContainerHigh = Palette.NightSurfaceHigh,
    surfaceContainerHighest = Color(0xFF332C4B),
    outline = Color(0xFF4D4566),
    outlineVariant = Color(0xFF3A3352),
    error = Color(0xFFFF8FA6),
)

/** 设计规范：额外语义色 */
@Immutable
data class BoxExtraColors(
    val isDark: Boolean,
    val gold: Color,
    val success: Color,
    val danger: Color,
    val heroGradient: List<Color>,
    val cardGlow: Color,
    val silhouette: Color,
    val shelf: Color,
)

private val LightExtra = BoxExtraColors(
    isDark = false,
    gold = Palette.Gold,
    success = Palette.Mint,
    danger = Palette.Coral,
    heroGradient = listOf(Color(0xFFC6B6FF), Color(0xFFE7C6FF), Color(0xFFFFC9DF)),
    cardGlow = Color(0x338F7BEF),
    silhouette = Color(0xFFD9D3E6),
    shelf = Color(0xFFEADFD3),
)

private val DarkExtra = BoxExtraColors(
    isDark = true,
    gold = Color(0xFFF0C766),
    success = Color(0xFF7FE0B8),
    danger = Color(0xFFFF8FA0),
    heroGradient = listOf(Color(0xFF3E3377), Color(0xFF55337A), Color(0xFF6E2F5F)),
    cardGlow = Color(0x55BBAEFF),
    silhouette = Color(0xFF3D3655),
    shelf = Color(0xFF3A3150),
)

val LocalBoxColors = staticCompositionLocalOf { LightExtra }

/** 设计规范：字体层级 */
private val BoxTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

/** 设计规范：圆角 */
private val BoxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun BoxverseTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalBoxColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = BoxTypography,
            shapes = BoxShapes,
            content = content,
        )
    }
}

/* ================= 稀有度视觉 ================= */
object RarityStyle {
    fun colors(rarity: Rarity): List<Color> = when (rarity) {
        Rarity.R -> listOf(Color(0xFFCFC8EA), Color(0xFFE9E5F8), Color(0xFFCFC8EA))
        Rarity.SR -> listOf(Color(0xFF7FB0FF), Color(0xFFB79CFF), Color(0xFF7FB0FF))
        Rarity.SSR -> listOf(Color(0xFFF6C85F), Color(0xFFFF9F5A), Color(0xFFFFE7A0), Color(0xFFF6C85F))
        Rarity.UR -> listOf(
            Color(0xFFFF7EB6), Color(0xFFB57CFF), Color(0xFF6FD3FF), Color(0xFF7FF0C0), Color(0xFFFFE27A), Color(0xFFFF7EB6),
        )
    }

    fun main(rarity: Rarity): Color = when (rarity) {
        Rarity.R -> Color(0xFF9D94C4)
        Rarity.SR -> Color(0xFF6E8DFF)
        Rarity.SSR -> Color(0xFFE2A23A)
        Rarity.UR -> Color(0xFFE05CA8)
    }

    fun brush(rarity: Rarity): Brush = Brush.linearGradient(colors(rarity))
    fun sweep(rarity: Rarity): Brush = Brush.sweepGradient(colors(rarity))
    fun borderWidth(rarity: Rarity) = when (rarity) {
        Rarity.R -> 1.5.dp
        Rarity.SR -> 2.dp
        Rarity.SSR -> 2.5.dp
        Rarity.UR -> 3.dp
    }
}
