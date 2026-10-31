package com.sshellspace.core.ui.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.composeunstyled.theme.ThemeToken
import com.sshellspace.core.domain.models.DEFAULT_COLOR_SCHEME_ID

/**
 * Built-in color schemes. Each one overrides the surface and brand tokens on top of the base light/dark palettes.
 */
enum class AppColorScheme(
    val id: String,
    val label: String,
    val dark: Map<ThemeToken<Color>, Color>,
    val light: Map<ThemeToken<Color>, Color>,
) {
    Default(
        id = DEFAULT_COLOR_SCHEME_ID,
        label = "Blue",
        dark = emptyMap(),
        light = emptyMap(),
    ),
    Emerald(
        id = "emerald",
        label = "Emerald",
        dark = surfaceTokens(Color(0xFF0A120F), Color(0xFF111C18), Color(0xFF18271F), Color(0xFF26392F)) +
            brandTokens(Color(0xFF10B981), Color(0xFF34D399), Color(0xFF059669), onBrand = Color(0xFF0B0F14)),
        light = surfaceTokens(Color(0xFFF6FBF8), Color(0xFFFFFFFF), Color(0xFFEFF7F2), Color(0xFFDCEBE2)) +
            brandTokens(Color(0xFF059669), Color(0xFF10B981), Color(0xFF047857)),
    ),
    Violet(
        id = "violet",
        label = "Violet",
        dark = surfaceTokens(Color(0xFF0E0B16), Color(0xFF16121F), Color(0xFF1F1A2C), Color(0xFF332B45)) +
            brandTokens(Color(0xFF8B5CF6), Color(0xFFA78BFA), Color(0xFF7C3AED)),
        light = surfaceTokens(Color(0xFFF9F7FC), Color(0xFFFFFFFF), Color(0xFFF3EFFA), Color(0xFFE6E0F0)) +
            brandTokens(Color(0xFF7C3AED), Color(0xFF8B5CF6), Color(0xFF6D28D9)),
    ),
    Amber(
        id = "amber",
        label = "Amber",
        dark = surfaceTokens(Color(0xFF14100A), Color(0xFF1C1710), Color(0xFF261F15), Color(0xFF3D3222)) +
            brandTokens(Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFD97706), onBrand = Color(0xFF0B0F14)),
        light = surfaceTokens(Color(0xFFFCFAF5), Color(0xFFFFFFFF), Color(0xFFF8F2E6), Color(0xFFEDE5D6)) +
            brandTokens(Color(0xFFD97706), Color(0xFFF59E0B), Color(0xFFB45309)),
    ),
    Rose(
        id = "rose",
        label = "Rose",
        dark = surfaceTokens(Color(0xFF140B0E), Color(0xFF1D1216), Color(0xFF28191F), Color(0xFF402630)) +
            brandTokens(Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFFE11D48)),
        light = surfaceTokens(Color(0xFFFCF7F8), Color(0xFFFFFFFF), Color(0xFFF9EEF1), Color(0xFFF0DFE3)) +
            brandTokens(Color(0xFFE11D48), Color(0xFFF43F5E), Color(0xFFBE123C)),
    );

    fun colors(isDark: Boolean): Map<ThemeToken<Color>, Color> = themeColors(isDark, this)

    companion object {
        fun fromId(id: String): AppColorScheme = entries.firstOrNull { it.id == id } ?: Default
    }
}

private fun brandTokens(
    brand: Color,
    hovered: Color,
    pressed: Color,
    onBrand: Color = Color.White,
) = mapOf(
    primary to brand,
    onPrimary to onBrand,
    accent to brand,
    onAccent to onBrand,
    primaryHovered to hovered,
    primaryPressed to pressed,
)

private fun surfaceTokens(
    backgroundColor: Color,
    surfaceColor: Color,
    elevatedColor: Color,
    borderColor: Color,
) = mapOf(
    background to backgroundColor,
    surface to surfaceColor,
    surfaceElevated to elevatedColor,
    border to borderColor,
    separator to borderColor,
)
