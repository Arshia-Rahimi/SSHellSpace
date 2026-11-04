package com.sshellspace.core.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TerminalColors(
    val name: String,
    val background: ColorHex,
    val foreground: ColorHex,
    val cursor: ColorHex,
    @SerialName("cursor_text") val cursorText: ColorHex,
    val selection: ColorHex,
    @SerialName("selection_text") val selectionText: ColorHex,
    @SerialName("search_match") val searchMatch: ColorHex,
    val hyperlink: ColorHex,
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
