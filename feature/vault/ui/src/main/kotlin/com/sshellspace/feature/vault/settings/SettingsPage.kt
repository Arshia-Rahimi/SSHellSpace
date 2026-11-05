package com.sshellspace.feature.vault.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.sshellspace.core.domain.models.TerminalColors
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.ui.designsystem.adaptive.currentWindowClass
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.Dropdown
import com.sshellspace.core.ui.designsystem.components.Switch
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.AppColorScheme
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.core.ui.designsystem.theme.toColor
import com.sshellspace.core.ui.shortcuts.label
import com.sshellspace.core.ui.shortcuts.toKeyChord
import com.sshellspace.feature.vault.gate.NewPasswordFields
import com.sshellspace.feature.vault.gate.PasswordField
import com.sshellspace.feature.vault.gate.pickVaultExportFile
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val SettingsMaxWidth = 720.dp
private val AUTO_LOCK_OPTIONS = listOf(5, 15, 30, 60, 0)

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
    val compact = currentWindowClass.isCompact

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            // Keep lines readable on wide windows instead of stretching sections edge to edge.
            modifier = Modifier.widthIn(max = SettingsMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp),
        ) {
            SettingsSections(uiState, onAction)
        }
    }
}

@Composable
private fun SettingsSections(
    uiState: SettingsPageUiState,
    onAction: (SettingsPageAction) -> Unit,
) {
    Text("Settings")

    uiState.vaultName?.let { vaultName ->
        SettingsSection(
            title = "Vault",
            description = "\"$vaultName\" is unlocked." +
                if (uiState.vaultHasPassword) "" else " It has no password, so it isn't protected.",
        ) {
            VaultActions(vaultName, uiState, onAction)
            ToggleRow(
                title = "Keep terminals and port forwards running while locked",
                description = "Locking then only hides them, and they're still there when you unlock. Their output " +
                    "stays in memory and forwards keep accepting connections. Turn this off to close them on every lock.",
                checked = uiState.keepSessionsWhenLocked,
                onCheckedChange = { onAction(SettingsPageAction.SetKeepSessionsWhenLocked(it)) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Lock this vault when idle or when this computer sleeps. Each vault has its own setting. " +
                        "Output in a terminal doesn't count as activity.",
                    color = Theme[colors][textSecondary],
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AUTO_LOCK_OPTIONS.forEach { minutes ->
                        OptionButton(
                            selected = minutes == uiState.autoLockMinutes,
                            onClick = { onAction(SettingsPageAction.SetAutoLockMinutes(minutes)) },
                        ) {
                            Text(if (minutes == 0) "Never" else "$minutes min")
                        }
                    }
                }
            }
        }
    }

    SettingsSection(
        title = "Appearance",
        description = "Choose light or dark mode and the accent color used across the app.",
    ) {
        ThemeSelector(
            selected = uiState.themePreference,
            onSelected = { onAction(SettingsPageAction.SetTheme(it)) },
        )
        ColorSchemeSelector(uiState, onAction)
    }

    SettingsSection(
        title = "Terminal",
        description = "The terminal's colors, and the shell that new local terminal tabs start with.",
    ) {
        TerminalThemeSelector(uiState, onAction)
        ToggleRow(
            title = "Reconnect dropped SSH sessions",
            description = "Keeps trying for about a minute. Sessions you end yourself, such as with exit, stay closed.",
            checked = uiState.autoReconnect,
            onCheckedChange = { onAction(SettingsPageAction.SetAutoReconnect(it)) },
        )
        ToggleRow(
            title = "Reopen tabs from last time",
            description = "After restarting the app, reopens this vault's tabs as new sessions.",
            checked = uiState.restoreTabs,
            onCheckedChange = { onAction(SettingsPageAction.SetRestoreTabs(it)) },
        )
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

@Composable
private fun VaultActions(
    vaultName: String,
    uiState: SettingsPageUiState,
    onAction: (SettingsPageAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val form = uiState.vaultForm

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OptionButton(selected = false, onClick = { onAction(SettingsPageAction.LockVault) }) {
                Text("Lock vault")
            }
            OptionButton(
                selected = form.edit == VaultEdit.ChangePassword,
                onClick = {
                    val next = VaultEdit.ChangePassword.takeUnless { form.edit == it }
                    onAction(SettingsPageAction.SetVaultEdit(next))
                },
            ) {
                Text(if (uiState.vaultHasPassword) "Change password" else "Add a password")
            }
            OptionButton(
                selected = false,
                onClick = {
                    scope.launch {
                        pickVaultExportFile(vaultName)?.let { onAction(SettingsPageAction.ExportVault(it)) }
                    }
                },
            ) {
                Text("Export…")
            }
            OptionButton(
                selected = form.edit == VaultEdit.Delete,
                onClick = {
                    onAction(SettingsPageAction.SetVaultEdit(VaultEdit.Delete.takeUnless { form.edit == it }))
                },
            ) {
                Text("Delete vault", color = Theme[colors][error])
            }
        }
        form.notice?.let { Text(it, color = Theme[colors][textSecondary]) }
        when (form.edit) {
            VaultEdit.ChangePassword -> ChangeVaultPasswordForm(uiState.vaultHasPassword, form, onAction)
            VaultEdit.Delete -> DeleteVaultForm(vaultName, form, onAction)
            null -> Unit
        }
        form.error?.let { Text(it, color = Theme[colors][error]) }
    }
}

@Composable
private fun ChangeVaultPasswordForm(
    hasPassword: Boolean,
    form: VaultForm,
    onAction: (SettingsPageAction) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val submit = { onAction(SettingsPageAction.ChangeVaultPassword(current, password, confirmation)) }
    val clearError = { onAction(SettingsPageAction.ClearVaultError) }

    Column(
        modifier = Modifier.widthIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (hasPassword) {
            PasswordField(
                value = current,
                onValueChange = {
                    current = it
                    clearError()
                },
                label = "Current password",
                enabled = !form.isBusy,
                isError = form.error != null,
            )
        }
        NewPasswordFields(
            password = password,
            onPasswordChange = {
                password = it
                clearError()
            },
            confirmation = confirmation,
            onConfirmationChange = {
                confirmation = it
                clearError()
            },
            enabled = !form.isBusy,
            onSubmit = submit,
            label = "New password",
        )
        OptionButton(selected = true, onClick = submit) {
            Text(
                when {
                    form.isBusy -> "Working…"
                    password.isEmpty() -> "Remove the password"
                    else -> "Save new password"
                },
            )
        }
    }
}

@Composable
private fun DeleteVaultForm(
    vaultName: String,
    form: VaultForm,
    onAction: (SettingsPageAction) -> Unit,
) {
    var confirmation by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.widthIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "Its hosts, keys, snippets and saved passwords are removed from this computer for good, and its open " +
                "terminals close. Export a copy first if you might want them back.",
            color = Theme[colors][textSecondary],
        )
        TextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = "Type $vaultName to confirm",
            enabled = !form.isBusy,
        )
        if (confirmation.trim() == vaultName) {
            OptionButton(selected = true, onClick = { onAction(SettingsPageAction.DeleteVault) }) {
                Text(if (form.isBusy) "Deleting…" else "Delete $vaultName permanently", color = Theme[colors][error])
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title)
            Text(description, color = Theme[colors][textSecondary])
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, accessibilityLabel = title)
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
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
    uiState: SettingsPageUiState,
    onAction: (SettingsPageAction) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Color scheme", color = Theme[colors][textSecondary])
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppColorScheme.entries.forEach { scheme ->
                ColorSchemeOption(
                    label = scheme.label,
                    swatch = scheme.colors(isDark = true).getValue(primary),
                    selected = uiState.activeUserThemeName == null && scheme == uiState.colorScheme,
                    onClick = { onAction(SettingsPageAction.SetColorScheme(scheme)) },
                )
            }
            uiState.userThemes.forEach { theme ->
                ColorSchemeOption(
                    label = theme.name,
                    swatch = theme.colors(isDark = true).getValue(primary),
                    selected = theme.name == uiState.activeUserThemeName,
                    onClick = { onAction(SettingsPageAction.SetUserTheme(theme.name)) },
                )
            }
            OptionButton(
                selected = false,
                onClick = {
                    scope.launch { pickThemeFile()?.let { onAction(SettingsPageAction.ImportTheme(it)) } }
                },
            ) {
                Text("Import theme…")
            }
            uiState.activeUserThemeName?.let { name ->
                OptionButton(
                    selected = false,
                    onClick = { onAction(SettingsPageAction.RemoveUserTheme(name)) },
                ) {
                    Text("Remove \"$name\"")
                }
            }
        }
        uiState.themeImportError?.let {
            Text(it, color = Theme[colors][error])
        }
    }
}

@Composable
private fun ColorSchemeOption(
    label: String,
    swatch: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OptionButton(selected = selected, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(12.dp)
                    .clip(CircleShape)
                    .background(swatch),
            )
            Text(label)
        }
    }
}

@Composable
private fun TerminalThemeSelector(
    uiState: SettingsPageUiState,
    onAction: (SettingsPageAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val selectedName = uiState.terminalThemeName?.takeIf { name -> uiState.terminalThemes.any { it.name == name } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Terminal colors", color = Theme[colors][textSecondary])
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OptionButton(
                selected = selectedName == null,
                onClick = { onAction(SettingsPageAction.SetTerminalTheme(null)) },
            ) {
                Text("Follow app theme")
            }
            uiState.terminalThemes.forEach { theme ->
                TerminalThemeOption(
                    theme = theme,
                    selected = theme.name == selectedName,
                    onClick = { onAction(SettingsPageAction.SetTerminalTheme(theme.name)) },
                )
            }
            OptionButton(
                selected = false,
                onClick = {
                    scope.launch { pickThemeFile()?.let { onAction(SettingsPageAction.ImportTerminalTheme(it)) } }
                },
            ) {
                Text("Import terminal theme…")
            }
            selectedName?.takeIf { it in uiState.importedTerminalThemes }?.let { name ->
                OptionButton(
                    selected = false,
                    onClick = { onAction(SettingsPageAction.RemoveTerminalTheme(name)) },
                ) {
                    Text("Remove \"$name\"")
                }
            }
        }
        Text(
            "Imports take this app's terminal tokens, a Windows Terminal color scheme, or an app theme file.",
            color = Theme[colors][textSecondary],
        )
        uiState.terminalThemeImportError?.let {
            Text(it, color = Theme[colors][error])
        }
    }
}

/** Shows the theme's page color with its text and cursor colors on it, which is most of what sets themes apart. */
@Composable
private fun TerminalThemeOption(
    theme: TerminalColors,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val swatchShape = RoundedCornerShape(3.dp)
    OptionButton(selected = selected, onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(swatchShape)
                    .background(theme.background.toColor())
                    .border(1.dp, Theme[colors][border], swatchShape)
                    .padding(horizontal = 4.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(width = 9.dp, height = 3.dp).background(theme.foreground.toColor()))
                Box(modifier = Modifier.size(width = 3.dp, height = 8.dp).background(theme.cursor.toColor()))
            }
            Text(theme.name)
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
