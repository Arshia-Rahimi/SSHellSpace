package com.sshellspace.core.ui.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sshellspace.core.domain.models.ColorHex

/** Reads a `0xAARRGGBB` value, the form imported themes are stored in. */
fun ColorHex.toColor(): Color = Color(removePrefix("0x").toULong(16).toLong())

fun Color.toColorHex(): ColorHex = "0x%08X".format(toArgb())
