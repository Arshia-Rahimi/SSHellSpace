package com.sshellspace.core.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surface

internal val FieldShape = RoundedCornerShape(6.dp)

private const val DISABLED_ALPHA = 0.5f

/** The shared look of text fields and dropdown anchors. */
@Composable
internal fun Modifier.fieldContainer(
    focused: Boolean,
    enabled: Boolean = true,
    isError: Boolean = false,
): Modifier {
    val borderColor = when {
        isError -> Theme[colors][error]
        focused -> Theme[colors][primary]
        else -> Theme[colors][border]
    }
    return this
        .alpha(if (enabled) 1f else DISABLED_ALPHA)
        .clip(FieldShape)
        .background(Theme[colors][surface])
        .border(1.dp, borderColor, FieldShape)
}
