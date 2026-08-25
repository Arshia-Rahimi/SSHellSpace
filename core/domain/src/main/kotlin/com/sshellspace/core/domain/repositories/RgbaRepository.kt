package com.sshellspace.core.domain.repositories

import com.sshellspace.core.domain.models.ColorHex
import com.sshellspace.core.domain.models.RgbaColor

interface RgbaRepository {
    fun parseColorHex(colorHex: ColorHex): HexColorParseResult
}

sealed class HexColorParseResult {
    data class Success(val color: RgbaColor) : HexColorParseResult()
    data class Failure(val reason: String) : HexColorParseResult()
}
