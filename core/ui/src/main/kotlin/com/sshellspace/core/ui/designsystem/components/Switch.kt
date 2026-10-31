package com.sshellspace.core.ui.designsystem.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.Thumb
import com.composeunstyled.Track
import com.composeunstyled.UnstyledSwitch
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accessibilityLabel: String? = null,
) {
    UnstyledSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.alpha(if (enabled) 1f else 0.5f),
        enabled = enabled,
        accessibilityLabel = accessibilityLabel,
    ) {
        Track(
            modifier = Modifier
                .size(width = 36.dp, height = 20.dp)
                .clip(CircleShape)
                .background(if (checked) Theme[colors][primary] else Theme[colors][surfaceElevated])
                .border(1.dp, if (checked) Theme[colors][primary] else Theme[colors][border], CircleShape)
                .padding(2.dp),
        ) {
            Thumb(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (checked) Theme[colors][onPrimary] else Theme[colors][textSecondary]),
                animationSpec = tween(durationMillis = 150),
            )
        }
    }
}
