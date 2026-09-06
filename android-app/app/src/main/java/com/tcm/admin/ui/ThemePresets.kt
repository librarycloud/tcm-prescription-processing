package com.tcm.admin

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal data class ThemeAccent(
    val key: String,
    val name: String,
    val primary: Color,
    val darkPrimary: Color = blendColor(primary, Color.White, 0.45f),
    val lightContainer: Color = blendColor(primary, Color.White, 0.90f),
    val darkContainer: Color = blendColor(primary, Color.Black, 0.70f),
    val lightOnContainer: Color = blendColor(primary, Color.Black, 0.30f),
    val darkOnContainer: Color = blendColor(primary, Color.White, 0.85f),
)

internal fun blendColor(base: Color, overlay: Color, ratio: Float): Color {
    val r = (base.red * (1f - ratio) + overlay.red * ratio).coerceIn(0f, 1f)
    val g = (base.green * (1f - ratio) + overlay.green * ratio).coerceIn(0f, 1f)
    val b = (base.blue * (1f - ratio) + overlay.blue * ratio).coerceIn(0f, 1f)
    return Color(r, g, b, 1f)
}

internal fun isLightColor(color: Color): Boolean {
    return (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue) > 0.6f
}

internal fun tryParseHexColor(hex: String): Color? {
    val clean = hex.trim().removePrefix("#").removePrefix("0x").removePrefix("0X")
    if (clean.length == 6 && clean.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
        return runCatching {
            Color(android.graphics.Color.parseColor("#$clean"))
        }.getOrNull()
    }
    if (clean.length == 3 && clean.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
        val expanded = "${clean[0]}${clean[0]}${clean[1]}${clean[1]}${clean[2]}${clean[2]}"
        return runCatching {
            Color(android.graphics.Color.parseColor("#$expanded"))
        }.getOrNull()
    }
    return null
}

internal fun formatHexColor(color: Color): String {
    val r = (color.red * 255).toInt().coerceIn(0, 255)
    val g = (color.green * 255).toInt().coerceIn(0, 255)
    val b = (color.blue * 255).toInt().coerceIn(0, 255)
    return String.format("#%02X%02X%02X", r, g, b)
}

internal fun customThemeAccent(color: Color): ThemeAccent {
    return ThemeAccent(
        key = "custom",
        name = "自定义",
        primary = color,
        darkPrimary = blendColor(color, Color.White, 0.45f),
        lightContainer = blendColor(color, Color.White, 0.90f),
        darkContainer = blendColor(color, Color.Black, 0.70f),
        lightOnContainer = blendColor(color, Color.Black, 0.30f),
        darkOnContainer = blendColor(color, Color.White, 0.85f),
    )
}

internal val DefaultThemeAccents: List<ThemeAccent> = listOf(
    ThemeAccent(
        key = "blue",
        name = "科技蓝",
        primary = Color(0xFF2563EB),
        darkPrimary = Color(0xFF93C5FD),
        lightContainer = Color(0xFFEFF6FF),
        darkContainer = Color(0xFF1E3A5F),
        lightOnContainer = Color(0xFF1E40AF),
        darkOnContainer = Color(0xFFD6E8FF),
    ),
    ThemeAccent(
        key = "emerald",
        name = "岐黄绿",
        primary = Color(0xFF059669),
        darkPrimary = Color(0xFF6EE7B7),
        lightContainer = Color(0xFFECFDF5),
        darkContainer = Color(0xFF064E3B),
        lightOnContainer = Color(0xFF065F46),
        darkOnContainer = Color(0xFFA7F3D0),
    ),
    ThemeAccent(
        key = "cyan",
        name = "松柏青",
        primary = Color(0xFF0891B2),
        darkPrimary = Color(0xFF67E8F9),
        lightContainer = Color(0xFFECFEFF),
        darkContainer = Color(0xFF164E63),
        lightOnContainer = Color(0xFF155E75),
        darkOnContainer = Color(0xFFA5F3FC),
    ),
    ThemeAccent(
        key = "amber",
        name = "琥珀金",
        primary = Color(0xFFD97706),
        darkPrimary = Color(0xFFFCD34D),
        lightContainer = Color(0xFFFFFBEB),
        darkContainer = Color(0xFF78350F),
        lightOnContainer = Color(0xFF92400E),
        darkOnContainer = Color(0xFFFDE68A),
    ),
    ThemeAccent(
        key = "crimson",
        name = "朱砂红",
        primary = Color(0xFFDC2626),
        darkPrimary = Color(0xFFFCA5A5),
        lightContainer = Color(0xFFFEF2F2),
        darkContainer = Color(0xFF7F1D1D),
        lightOnContainer = Color(0xFF991B1B),
        darkOnContainer = Color(0xFFFECACA),
    ),
    ThemeAccent(
        key = "violet",
        name = "黛青紫",
        primary = Color(0xFF7C3AED),
        darkPrimary = Color(0xFFC4B5FD),
        lightContainer = Color(0xFFF5F3FF),
        darkContainer = Color(0xFF4C1D95),
        lightOnContainer = Color(0xFF5B21B6),
        darkOnContainer = Color(0xFFDDD6FE),
    ),
    ThemeAccent(
        key = "indigo",
        name = "幽兰墨",
        primary = Color(0xFF4F46E5),
        darkPrimary = Color(0xFFA5B4FC),
        lightContainer = Color(0xFFEEF2FF),
        darkContainer = Color(0xFF312E81),
        lightOnContainer = Color(0xFF3730A3),
        darkOnContainer = Color(0xFFC7D2FE),
    ),
    ThemeAccent(
        key = "forest",
        name = "月桂绿",
        primary = Color(0xFF15803D),
        darkPrimary = Color(0xFF86EFAC),
        lightContainer = Color(0xFFF0FDF4),
        darkContainer = Color(0xFF14532D),
        lightOnContainer = Color(0xFF166534),
        darkOnContainer = Color(0xFFBBF7D0),
    ),
)

internal val QuickCustomColors: List<Pair<String, Color>> = listOf(
    "玫瑰" to Color(0xFFE11D48),
    "珊瑚" to Color(0xFFEA580C),
    "姜黄" to Color(0xFFCA8A04),
    "青柠" to Color(0xFF65A30D),
    "水绿" to Color(0xFF0D9488),
    "天蓝" to Color(0xFF0284C7),
    "靛青" to Color(0xFF6366F1),
    "葡萄" to Color(0xFF9333EA),
    "紫红" to Color(0xFFC026D3),
    "桃粉" to Color(0xFFDB2777),
    "烟灰" to Color(0xFF475569),
    "檀褐" to Color(0xFF854D0E),
)

internal fun resolveThemeAccent(key: String, customHex: String): ThemeAccent {
    if (key == "custom") {
        val color = tryParseHexColor(customHex) ?: Color(0xFF2563EB)
        return customThemeAccent(color)
    }
    return DefaultThemeAccents.firstOrNull { it.key == key } ?: DefaultThemeAccents.first()
}

internal fun tcmLightColorScheme(accent: ThemeAccent = DefaultThemeAccents.first()): ColorScheme = lightColorScheme(
    primary = accent.primary,
    onPrimary = if (isLightColor(accent.primary)) Color(0xFF0F172A) else Color.White,
    primaryContainer = accent.lightContainer,
    onPrimaryContainer = accent.lightOnContainer,
    inversePrimary = accent.darkPrimary,
    secondary = Color(0xFFF59E0B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF78350F),
    tertiary = Color(0xFF10B981),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECFDF5),
    onTertiaryContainer = Color(0xFF065F46),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF8FAFC),
    surfaceTint = accent.primary,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    scrim = Color(0xFF000000),
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

internal fun tcmDarkColorScheme(accent: ThemeAccent = DefaultThemeAccents.first()): ColorScheme = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = Color(0xFF0B1B33),
    primaryContainer = accent.darkContainer,
    onPrimaryContainer = accent.darkOnContainer,
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF2A1A00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFE8A3),
    tertiary = Color(0xFF6EE7B7),
    onTertiary = Color(0xFF002117),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFB8F5D6),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)

internal fun tcmPureBlackColorScheme(accent: ThemeAccent = DefaultThemeAccents.first()): ColorScheme = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = Color(0xFF0B1B33),
    primaryContainer = accent.darkContainer,
    onPrimaryContainer = accent.darkOnContainer,
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF2A1A00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFE8A3),
    tertiary = Color(0xFF6EE7B7),
    onTertiary = Color(0xFF002117),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFB8F5D6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF18181B),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF52525B),
    outlineVariant = Color(0xFF27272A),
    scrim = Color(0xFF000000),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
)
