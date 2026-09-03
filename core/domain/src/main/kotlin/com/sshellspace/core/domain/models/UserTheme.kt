package com.sshellspace.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserTheme(
    val name: String,
    val dark: ThemePalette,
    val light: ThemePalette,
)

@Serializable
data class ThemePalette(
    val background: ColorHex,
    val surface: ColorHex,
    @SerialName("surface_elevated") val surfaceElevated: ColorHex,
    @SerialName("on_background") val onBackground: ColorHex,
    @SerialName("on_surface") val onSurface: ColorHex,
    @SerialName("text_primary") val textPrimary: ColorHex,
    @SerialName("text_secondary") val textSecondary: ColorHex,
    @SerialName("text_disabled") val textDisabled: ColorHex,
    val primary: ColorHex,
    @SerialName("on_primary") val onPrimary: ColorHex,
    val accent: ColorHex,
    @SerialName("on_accent") val onAccent: ColorHex,
    @SerialName("primary_hovered") val primaryHovered: ColorHex,
    @SerialName("primary_pressed") val primaryPressed: ColorHex,
    val border: ColorHex,
    val separator: ColorHex,
    val outline: ColorHex,
    val error: ColorHex,
    @SerialName("on_error") val onError: ColorHex,
    val success: ColorHex,
    val warning: ColorHex,
    val info: ColorHex,
    val scrim: ColorHex,
    val overlay: ColorHex,
    @SerialName("hover_overlay") val hoverOverlay: ColorHex,
    @SerialName("pressed_overlay") val pressedOverlay: ColorHex,
    @SerialName("on_overlay") val onOverlay: ColorHex,
)
