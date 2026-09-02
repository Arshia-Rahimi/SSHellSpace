package com.sshellspace.core.data

import com.sshellspace.core.domain.models.ColorHex
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun isValidColorHex(colorHex: ColorHex): Boolean =
    colorHex.matches(HEX_REGEX)

@OptIn(ExperimentalUuidApi::class)
fun generateUuid() = Uuid.generateV7()

private val HEX_REGEX = Regex("^0x[0-9a-fA-F]+$")
