package com.sshellspace.core.ui.designsystem.components

import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.composeunstyled.UnstyledButton
import com.composeunstyled.UnstyledIcon

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
) {
    UnstyledButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
    ) {
        UnstyledIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}
