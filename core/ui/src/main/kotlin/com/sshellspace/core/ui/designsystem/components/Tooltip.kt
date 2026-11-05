package com.sshellspace.core.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.composeunstyled.AnchorSide
import com.composeunstyled.Text
import com.composeunstyled.TooltipPanel
import com.composeunstyled.UnstyledTooltip
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated

private val TooltipShape = RoundedCornerShape(6.dp)
private val TooltipMaxWidth = 480.dp

/** Shows [text] next to [anchor] on hover or keyboard focus. Needs a TooltipHost above it (see SSHellSpaceTheme). */
@Composable
fun Tooltip(
    text: String,
    enabled: Boolean = true,
    side: AnchorSide = AnchorSide.Top,
    anchor: @Composable () -> Unit,
) {
    UnstyledTooltip(
        enabled = enabled,
        side = side,
        sideOffset = 6.dp,
        hoverDelayMillis = 300L,
        panel = {
            TooltipPanel {
                Text(
                    text = text,
                    modifier = Modifier
                        .widthIn(max = TooltipMaxWidth)
                        .shadow(4.dp, TooltipShape)
                        .clip(TooltipShape)
                        .background(Theme[colors][surfaceElevated])
                        .border(1.dp, Theme[colors][border], TooltipShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        },
        anchor = anchor,
    )
}
