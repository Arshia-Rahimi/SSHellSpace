package com.sshellspace.feature.terminal.domain.models

import com.sshellspace.core.domain.models.ColorHex
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TerminalColors(
    val background: ColorHex,
    val foreground: ColorHex,
    @SerialName("cursor_color") val cursorColor: ColorHex,
    val black: ColorHex,
    @SerialName("bright_black") val brightBlack: ColorHex,
    val red: ColorHex,
    @SerialName("bright_red") val brightRed: ColorHex,
    val green: ColorHex,
    @SerialName("bright_green") val brightGreen: ColorHex,
    val yellow: ColorHex,
    @SerialName("bright_yellow") val brightYellow: ColorHex,
    val blue: ColorHex,
    @SerialName("bright_blue") val brightBlue: ColorHex,
    val purple: ColorHex,
    @SerialName("bright_purple") val brightPurple: ColorHex,
    val cyan: ColorHex,
    @SerialName("bright_cyan") val brightCyan: ColorHex,
    val white: ColorHex,
    @SerialName("bright_white") val brightWhite: ColorHex,
)
