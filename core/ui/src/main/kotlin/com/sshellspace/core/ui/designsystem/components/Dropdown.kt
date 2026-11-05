package com.sshellspace.core.ui.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composeunstyled.DropdownMenuPanel
import com.composeunstyled.DropdownMenuScope
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledDropdownMenuItem
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.hoverOverlay
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary

private val MenuShape = RoundedCornerShape(8.dp)
private val MenuMaxHeight = 320.dp

@Composable
fun <T> Dropdown(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    optionLabel: (T) -> String = { it.toString() },
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (label != null) {
            Text(
                text = label,
                color = Theme[colors][textSecondary],
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        UnstyledDropdownMenu(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth(),
            sideOffset = 4.dp,
            anchor = {
                Button(
                    onClick = { expanded = !expanded },
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { anchorWidth = with(density) { it.width.toDp() } }
                        .fieldContainer(focused = expanded, enabled = enabled),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(contentPadding),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(optionLabel(selected), modifier = Modifier.weight(1f), maxLines = 1)
                        UnstyledIcon(
                            imageVector = Icons.ChevronDown,
                            contentDescription = null,
                            tint = Theme[colors][textSecondary],
                        )
                    }
                }
            },
            panel = {
                MenuPanel(width = anchorWidth, onDismiss = { expanded = false }) {
                    options.forEach { option ->
                        MenuItem(
                            onClick = { onSelected(option) },
                            contentPadding = contentPadding,
                        ) {
                            Text(optionLabel(option), modifier = Modifier.weight(1f))
                            if (option == selected) {
                                UnstyledIcon(
                                    imageVector = Icons.Check,
                                    contentDescription = "Selected",
                                    tint = Theme[colors][primary],
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

/**
 * A styled [DropdownMenuPanel]. Pass [width] to size it, e.g. to match its anchor.
 *
 * @param onDismiss closes the menu on Esc. The menu's own Esc handling listens for AWT key events, which the Tao
 * backend doesn't send.
 */
@Composable
fun DropdownMenuScope.MenuPanel(
    onDismiss: () -> Unit,
    width: Dp = Dp.Unspecified,
    content: @Composable () -> Unit,
) {
    DropdownMenuPanel(
        modifier = Modifier
            .onPreviewKeyEvent { event ->
                val isEscape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                if (isEscape) onDismiss()
                isEscape
            }
            .then(if (width != Dp.Unspecified && width > 0.dp) Modifier.width(width) else Modifier)
            .heightIn(max = MenuMaxHeight)
            .shadow(8.dp, MenuShape)
            .clip(MenuShape)
            .background(Theme[colors][surfaceElevated])
            .border(1.dp, Theme[colors][border], MenuShape)
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
    ) {
        content()
    }
}

/** A styled [UnstyledDropdownMenuItem] that highlights on hover and keyboard focus. */
@Composable
fun MenuItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()

    UnstyledDropdownMenuItem(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered || focused) Theme[colors][hoverOverlay] else Color.Transparent),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(contentPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}
