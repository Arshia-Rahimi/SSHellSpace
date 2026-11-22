package com.sshellspace.feature.newtab.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledHorizontalSeparator
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary

@Composable
fun NewTabDialog(
    viewModel: NewTabDialogViewModel,
    onCloseDialog: (() -> Unit)?,
) {
    val uiState by viewModel.uiState.collectAsState()
    NewTabDialogContent(
        uiState = uiState,
        onChoose = { action ->
            viewModel.execute(action)
            onCloseDialog?.invoke()
        },
        onDismiss = { onCloseDialog?.invoke() },
    )
}

@Composable
internal fun NewTabDialogContent(
    uiState: NewTabDialogState,
    onChoose: (NewTabDialogAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = remember(uiState) { uiState.choices() }
    var selected by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val shape = RoundedCornerShape(8.dp)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(choices) { selected = selected.coerceIn(0, (choices.size - 1).coerceAtLeast(0)) }
    // Re-request when the selected row changes, including when shells arrive and the row at 0 changes.
    LaunchedEffect(choices.getOrNull(selected)?.key) { bringIntoViewRequester.bringIntoView() }

    Box(
        modifier = Modifier.fillMaxSize()
            .padding(top = 48.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .heightIn(max = 480.dp)
                .clip(shape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][border], shape)
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            if (choices.isNotEmpty()) selected = moveSelection(selected, 1, choices.size)
                            true
                        }
                        Key.DirectionUp -> {
                            if (choices.isNotEmpty()) selected = moveSelection(selected, -1, choices.size)
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            choices.getOrNull(selected)?.action?.let(onChoose)
                            true
                        }
                        Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                uiState.isLoading -> SectionLabel("Looking for shells…")
                uiState.defaultShell == null -> SectionLabel("No shells found")
            }
            choices.forEachIndexed { index, choice ->
                if (choice.section != null) {
                    if (index > 0) Separator()
                    SectionLabel(choice.section)
                }
                OptionRow(
                    title = choice.title,
                    subtitle = choice.subtitle,
                    highlighted = index == selected,
                    trailing = choice.trailing,
                    onClick = { onChoose(choice.action) },
                    action = choice.secondaryAction?.let { secondary ->
                        @Composable {
                            Tooltip(text = "Browse files") {
                                IconButton(
                                    onClick = { onChoose(secondary) },
                                    imageVector = Icons.Folder,
                                    contentDescription = "Browse files on ${choice.title}",
                                )
                            }
                        }
                    },
                    modifier = if (index == selected) Modifier.bringIntoViewRequester(bringIntoViewRequester) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun Separator() {
    UnstyledHorizontalSeparator(
        color = Theme[colors][separator],
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Theme[colors][textSecondary],
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String?,
    highlighted: Boolean,
    trailing: String?,
    onClick: () -> Unit,
    action: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
            .clip(shape)
            .background(if (highlighted) Theme[colors][surfaceElevated] else Color.Transparent)
            .border(1.dp, if (highlighted) Theme[colors][primary] else Color.Transparent, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title)
                subtitle?.let { Text(it, color = Theme[colors][textSecondary]) }
            }
            if (trailing != null) {
                Text(trailing, color = Theme[colors][textSecondary])
            }
            action?.invoke()
        }
    }
}
