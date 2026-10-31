package com.sshellspace.feature.vault.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.Dropdown
import com.sshellspace.core.ui.designsystem.theme.AppColorScheme
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.core.ui.shortcuts.label
import com.sshellspace.core.ui.shortcuts.toKeyChord
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsPage() {
    val viewModel = koinViewModel<SettingsPageViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    SettingsPageContent(uiState, viewModel::execute)
}

@Composable
internal fun SettingsPageContent(
    uiState: SettingsPageUiState,
    onAction: (SettingsPageAction) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("Settings")

        SettingsSection(
            title = "Appearance",
            description = "Choose light or dark mode and the accent color used across the app.",
        ) {
            ThemeSelector(
                selected = uiState.themePreference,
                onSelected = { onAction(SettingsPageAction.SetTheme(it)) },
            )
            ColorSchemeSelector(
                selected = uiState.colorScheme,
                onSelected = { onAction(SettingsPageAction.SetColorScheme(it)) },
            )
        }

        SettingsSection(
            title = "Terminal",
            description = "The shell that new local terminal tabs start with.",
        ) {
            Dropdown(
                options = uiState.shells,
                selected = uiState.defaultShell,
                onSelected = { onAction(SettingsPageAction.SetDefaultShell(it)) },
                modifier = Modifier.widthIn(max = 480.dp),
                optionLabel = { it.label },
            )
            if (uiState.isDiscoveringShells) {
                Text("Looking for installed shells…", color = Theme[colors][textSecondary])
            }
        }

        SettingsSection(
            title = "Shortcuts",
            description = "Click a shortcut, then press the new key combination. Esc cancels.",
        ) {
            uiState.shortcuts.forEach { binding ->
                ShortcutRow(
                    binding = binding,
                    recording = uiState.recording?.takeIf { it.shortcut == binding.shortcut },
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    binding: ShortcutBinding,
    recording: ShortcutRecording?,
    onAction: (SettingsPageAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(binding.shortcut.label, modifier = Modifier.weight(1f))
            if (recording != null) {
                ShortcutRecorder(onAction)
            } else {
                OptionButton(
                    selected = false,
                    onClick = { onAction(SettingsPageAction.Shortcut.StartRecording(binding.shortcut)) },
                ) {
                    Text(binding.chord.label)
                }
            }
            if (!binding.isDefault) {
                OptionButton(
                    selected = false,
                    onClick = { onAction(SettingsPageAction.Shortcut.Reset(binding.shortcut)) },
                ) {
                    Text("Reset")
                }
            }
        }
        recording?.error?.let {
            Text(it, color = Theme[colors][error])
        }
    }
}

@Composable
private fun ShortcutRecorder(onAction: (SettingsPageAction) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .clip(shape)
            .background(Theme[colors][surfaceElevated])
            .border(1.dp, Theme[colors][primary], shape)
            .focusRequester(focusRequester)
            .onFocusChanged {
                if (it.isFocused) hadFocus = true
                else if (hadFocus) onAction(SettingsPageAction.Shortcut.CancelRecording)
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                if (event.key == Key.Escape) {
                    onAction(SettingsPageAction.Shortcut.CancelRecording)
                } else {
                    event.toKeyChord()?.let { onAction(SettingsPageAction.Shortcut.Record(it)) }
                }
                true
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text("Press keys…", color = Theme[colors][textSecondary])
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Theme[colors][surface])
            .border(1.dp, Theme[colors][border], RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title)
            Text(description, color = Theme[colors][textSecondary])
        }
        content()
    }
}

@Composable
private fun ThemeSelector(
    selected: ThemePreference,
    onSelected: (ThemePreference) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Theme", color = Theme[colors][textSecondary])
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemePreference.entries.forEach { theme ->
                OptionButton(
                    selected = theme == selected,
                    onClick = { onSelected(theme) },
                ) {
                    Text(theme.label)
                }
            }
        }
    }
}

@Composable
private fun ColorSchemeSelector(
    selected: AppColorScheme,
    onSelected: (AppColorScheme) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Color scheme", color = Theme[colors][textSecondary])
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppColorScheme.entries.forEach { scheme ->
                OptionButton(
                    selected = scheme == selected,
                    onClick = { onSelected(scheme) },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(12.dp)
                                .clip(CircleShape)
                                .background(scheme.colors(isDark = true).getValue(primary)),
                        )
                        Text(scheme.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionButton(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Button(
        onClick = onClick,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Theme[colors][surfaceElevated] else Color.Transparent)
            .border(1.dp, if (selected) Theme[colors][primary] else Theme[colors][border], shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        content()
    }
}
