package com.sshellspace.core.data.impl

import com.sshellspace.core.domain.models.ColorHex
import com.sshellspace.core.domain.repositories.HexColorParseResult
import com.sshellspace.core.domain.repositories.RgbaRepository
import com.sshellspace.core.domain.models.RgbaColor

class RgbaRepositoryImpl: RgbaRepository {
    override fun parseColorHex(colorHex: ColorHex): HexColorParseResult {
        val trimmed = colorHex.trim()
        val match = HEX_REGEX.matchEntire(trimmed)
            ?: return HexColorParseResult.Failure("'$trimmed' is not a valid hex color")

        val hex = match.groupValues[1]
        val color = when (hex.length) {
            3 -> RgbaColor(
                r = hex[0].toString().repeat(2).toInt(16),
                g = hex[1].toString().repeat(2).toInt(16),
                b = hex[2].toString().repeat(2).toInt(16)
            )
            6 -> RgbaColor(
                r = hex.substring(0, 2).toInt(16),
                g = hex.substring(2, 4).toInt(16),
                b = hex.substring(4, 6).toInt(16)
            )
            8 -> RgbaColor(
                r = hex.substring(0, 2).toInt(16),
                g = hex.substring(2, 4).toInt(16),
                b = hex.substring(4, 6).toInt(16),
                a = hex.substring(6, 8).toInt(16)
            )
            else -> return HexColorParseResult.Failure("Unsupported hex length")
        }
        return HexColorParseResult.Success(color)
    }
}

private val HEX_REGEX = Regex("^#?([A-Fa-f0-9]{6}|[A-Fa-f0-9]{8}|[A-Fa-f0-9]{3})$")
