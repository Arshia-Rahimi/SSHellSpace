package com.sshellspace.core.ui.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.composeunstyled.theme.ThemeBuilder
import com.composeunstyled.theme.ThemeProperty
import com.composeunstyled.theme.ThemeToken

val colors = ThemeProperty<Color>("colors")

// Surfaces
val background = ThemeToken<Color>("background")
val surface = ThemeToken<Color>("surface")
val surfaceElevated = ThemeToken<Color>("surface_elevated")

// Text & content
val onBackground = ThemeToken<Color>("on_background")
val onSurface = ThemeToken<Color>("on_surface")
val textPrimary = ThemeToken<Color>("text_primary")
val textSecondary = ThemeToken<Color>("text_secondary")
val textDisabled = ThemeToken<Color>("text_disabled")

// Brand & interactive
val primary = ThemeToken<Color>("primary")
val onPrimary = ThemeToken<Color>("on_primary")
val accent = ThemeToken<Color>("accent")
val onAccent = ThemeToken<Color>("on_accent")
val primaryHovered = ThemeToken<Color>("primary_hovered")
val primaryPressed = ThemeToken<Color>("primary_pressed")

// Borders & separators
val border = ThemeToken<Color>("border")
val separator = ThemeToken<Color>("separator")
val outline = ThemeToken<Color>("outline")

// Semantic
val error = ThemeToken<Color>("error")
val onError = ThemeToken<Color>("on_error")
val success = ThemeToken<Color>("success")
val warning = ThemeToken<Color>("warning")
val info = ThemeToken<Color>("info")

// Overlays & scrims
val scrim = ThemeToken<Color>("scrim")
val overlay = ThemeToken<Color>("overlay")
val hoverOverlay = ThemeToken<Color>("hover_overlay")
val pressedOverlay = ThemeToken<Color>("pressed_overlay")
val onOverlay = ThemeToken<Color>("on_overlay")

private val darkColorTokens = mapOf(
    background to Color(0xFF0B0F14),
    surface to Color(0xFF141A21),
    surfaceElevated to Color(0xFF1C242E),
    onBackground to Color(0xFFE6EDF3),
    onSurface to Color(0xFFE6EDF3),
    textPrimary to Color(0xFFE6EDF3),
    textSecondary to Color(0xFF8B949E),
    textDisabled to Color(0xFF484F58),
    primary to Color(0xFF3B82F6),
    onPrimary to Color(0xFFFFFFFF),
    accent to Color(0xFF3B82F6),
    onAccent to Color(0xFFFFFFFF),
    primaryHovered to Color(0xFF60A5FA),
    primaryPressed to Color(0xFF2563EB),
    border to Color(0xFF30363D),
    separator to Color(0xFF30363D),
    outline to Color(0xFF484F58),
    error to Color(0xFFF85149),
    onError to Color(0xFFFFFFFF),
    success to Color(0xFF3FB950),
    warning to Color(0xFFD29922),
    info to Color(0xFF58A6FF),
    scrim to Color.Black.copy(alpha = 0.6f),
    overlay to Color.Black.copy(alpha = 0.4f),
    hoverOverlay to Color.White.copy(alpha = 0.08f),
    pressedOverlay to Color.White.copy(alpha = 0.12f),
    onOverlay to Color.White,
)

private val lightColorTokens = mapOf(
    background to Color(0xFFFAFAFA),
    surface to Color(0xFFFFFFFF),
    surfaceElevated to Color(0xFFFFFFFF),
    onBackground to Color(0xFF0C0A09),
    onSurface to Color(0xFF0C0A09),
    textPrimary to Color(0xFF0C0A09),
    textSecondary to Color(0xFF57534E),
    textDisabled to Color(0xFFA8A29E),
    primary to Color(0xFF2563EB),
    onPrimary to Color(0xFFFFFFFF),
    accent to Color(0xFF2563EB),
    onAccent to Color(0xFFFFFFFF),
    primaryHovered to Color(0xFF3B82F6),
    primaryPressed to Color(0xFF1D4ED8),
    border to Color(0xFFE7E5E4),
    separator to Color(0xFFE7E5E4),
    outline to Color(0xFFD6D3D1),
    error to Color(0xFFDC2626),
    onError to Color(0xFFFFFFFF),
    success to Color(0xFF16A34A),
    warning to Color(0xFFB45309),
    info to Color(0xFF2563EB),
    scrim to Color.Black.copy(alpha = 0.4f),
    overlay to Color.Black.copy(alpha = 0.05f),
    hoverOverlay to Color.Black.copy(alpha = 0.05f),
    pressedOverlay to Color.Black.copy(alpha = 0.1f),
    onOverlay to Color.White,
)

internal fun themeColors(isDark: Boolean): Map<ThemeToken<Color>, Color> =
    if (isDark) darkColorTokens else lightColorTokens

internal fun ThemeBuilder.applyThemeColors(isDark: Boolean) {
    properties[colors] = themeColors(isDark)
}
