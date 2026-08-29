package com.sshellspace.core.data

import com.sshellspace.core.domain.models.ColorHex

fun isValidColorHex(colorHex: ColorHex): Boolean =
    colorHex.matches(HEX_REGEX)

private val HEX_REGEX = Regex("^0[xX][0-9a-fA-F]+$")
