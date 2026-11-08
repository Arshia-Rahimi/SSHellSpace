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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.AppUpdateState
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val SettingsMaxWidth = 720.dp
private val AUTO_LOCK_OPTIONS = listOf(5, 15, 30, 60, 0)
private val UPDATE_CHECK_INTERVALS = listOf(
    1 to "Hourly",
    6 to "Every 6 hours",
    12 to "Every 12 hours",
    24 to "Daily",
    168 to "Weekly",
)
private val UPDATE_CHECK_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** The vault tab's settings: the unlocked vault's own, then the general ones every vault shares. */
@Composable
fun SettingsPage() {
    val vaultViewModel = koinViewModel<VaultSettingsViewModel>()
    val generalViewModel = koinViewModel<GeneralSettingsViewModel>()
    val vaultState by vaultViewModel.uiState.collectAsState()
    val generalState by generalViewModel.uiState.collectAsState()
    SettingsPageContent(vaultState, vaultViewModel::execute, generalState, generalViewModel::execute)
}

@Composable
internal fun SettingsPageContent(
    vaultState: VaultSettingsUiState,
    onVaultAction: (VaultSettingsAction) -> Unit,
    generalState: GeneralSettingsUiState,
    onGeneralAction: (GeneralSettingsAction) -> Unit,
) {
    SettingsColumn {
        Text("Settings")
        vaultState.vaultName?.let { vaultName ->
            SettingsGroupHeading(
                title = "This vault",
                description = "Saved inside \"$vaultName\", so they go wherever it's exported to.",
            )
            VaultSettingsSection(vaultName, vaultState, onVaultAction)
        }
        SettingsGroupHeading(
            title = "General",
            description = "Shared by every vault on this computer. You can also change these from the unlock screen.",
        )
        GeneralSettingsSections(generalState, onGeneralAction)
    }
}

/** Centers settings at a readable width, scrolling when they don't fit. */
@Composable
internal fun SettingsColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val compact = currentWindowClass.isCompact

    Column(
        modifier = modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            // Keep lines readable on wide windows instead of stretching sections edge to edge.
            modifier = Modifier.widthIn(max = SettingsMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 16.dp else 24.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsGroupHeading(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(description, color = Theme[colors][textSecondary])
    }
}

@Composable
private fun VaultSettingsSection(
    vaultName: String,
    uiState: VaultSettingsUiState,
    onAction: (VaultSettingsAction) -> Unit,
) {
    SettingsSection(
        title = "Vault",
        description = "\"$vaultName\" is unlocked." +
            if (uiState.vaultHasPassword) "" else " It has no password, so it isn't protected.",
    ) {
        VaultActions(vaultName, uiState, onAction)
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
                        onClick = { onAction(VaultSettingsAction.SetAutoLockMinutes(minutes)) },
                    ) {
                        Text(if (minutes == 0) "Never" else "$minutes min")
                    }
                }
            }
        }
    }
}

/** The settings every vault shares. They touch no vault data, so the unlock screen shows them too. */
@Composable
internal fun GeneralSettingsSections(
    uiState: GeneralSettingsUiState,
    onAction: (GeneralSettingsAction) -> Unit,
) {
    SettingsSection(
        title = "Appearance",
        description = "Choose light or dark mode and the accent color used across the app.",
    ) {
        ThemeSelector(
            selected = uiState.themePreference,
            onSelected = { onAction(GeneralSettingsAction.SetTheme(it)) },
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
            onCheckedChange = { onAction(GeneralSettingsAction.SetAutoReconnect(it)) },
        )
        ToggleRow(
            title = "Reopen tabs from last time",
            description = "After restarting the app, reopens a vault's tabs as new sessions when it unlocks.",
            checked = uiState.restoreTabs,
            onCheckedChange = { onAction(GeneralSettingsAction.SetRestoreTabs(it)) },
        )
        ToggleRow(
            title = "Keep terminals and port forwards running while locked",
            description = "Locking then only hides them, and they're still there when you unlock. Their output " +
                "stays in memory and forwards keep accepting connections. Turn this off to close them on every lock.",
            checked = uiState.keepSessionsWhenLocked,
            onCheckedChange = { onAction(GeneralSettingsAction.SetKeepSessionsWhenLocked(it)) },
        )
        Dropdown(
            options = uiState.shells,
            selected = uiState.defaultShell,
            onSelected = { onAction(GeneralSettingsAction.SetDefaultShell(it)) },
            modifier = Modifier.widthIn(max = 480.dp),
            optionLabel = { it.label },
        )
        if (uiState.isDiscoveringShells) {
            Text("Looking for installed shells…", color = Theme[colors][textSecondary])
        }
    }

    UpdatesSection(uiState.updates, onAction)

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
private fun UpdatesSection(
    updates: UpdateSettings,
    onAction: (GeneralSettingsAction) -> Unit,
) {
    val status = updates.status
    SettingsSection(
        title = "Updates",
        description = "This is version ${updates.currentVersion}.",
    ) {
        if (status == AppUpdateState.Unsupported) {
            Text("This copy of the app can't update itself.", color = Theme[colors][textSecondary])
            return@SettingsSection
        }
        ToggleRow(
            title = "Update automatically",
            description = "Checks GitHub for new versions, downloads them, and installs them when you quit.",
            checked = updates.automatic,
            onCheckedChange = { onAction(GeneralSettingsAction.Update.SetAutomatic(it)) },
        )
        if (updates.automatic) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Check for updates", color = Theme[colors][textSecondary])
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UPDATE_CHECK_INTERVALS.forEach { (hours, label) ->
                        OptionButton(
                            selected = hours == updates.checkIntervalHours,
                            onClick = { onAction(GeneralSettingsAction.Update.SetCheckInterval(hours)) },
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }
        UpdateActions(updates, onAction)
    }
}

@Composable
private fun UpdateActions(
    updates: UpdateSettings,
    onAction: (GeneralSettingsAction) -> Unit,
) {
    val status = updates.status
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (status !is AppUpdateState.Checking && status !is AppUpdateState.Downloading) {
                OptionButton(selected = false, onClick = { onAction(GeneralSettingsAction.Update.Check) }) {
                    Text("Check for updates")
                }
            }
            if (status is AppUpdateState.Available) {
                OptionButton(selected = true, onClick = { onAction(GeneralSettingsAction.Update.Download) }) {
                    Text("Download version ${status.version}")
                }
            }
            if (status is AppUpdateState.Ready && updates.confirmingRestart == null) {
                OptionButton(selected = true, onClick = { onAction(GeneralSettingsAction.Update.Restart) }) {
                    Text("Restart to update")
                }
            }
        }
        status.message(updates.automatic)?.let { message ->
            Text(message, color = Theme[colors][if (status is AppUpdateState.Failed) error else textSecondary])
        }
        updates.lastChecked?.let { Text("Last checked ${formatUpdateCheckTime(it)}", color = Theme[colors][textSecondary]) }
        updates.confirmingRestart?.let { open ->
            Text(
                if (open == 1) "1 open tab or port forward will close." else "$open open tabs and port forwards will close.",
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OptionButton(selected = true, onClick = { onAction(GeneralSettingsAction.Update.ConfirmRestart) }) {
                    Text("Restart anyway")
                }
                OptionButton(selected = false, onClick = { onAction(GeneralSettingsAction.Update.CancelRestart) }) {
                    Text("Cancel")
                }
            }
        }
    }
}

private fun AppUpdateState.message(automatic: Boolean): String? = when (this) {
    AppUpdateState.Unsupported, AppUpdateState.Idle -> null
    AppUpdateState.Checking -> "Checking…"
    AppUpdateState.UpToDate -> "You have the latest version."
    is AppUpdateState.Available -> "Version $version is available."
    is AppUpdateState.Downloading -> "Downloading version $version… $percent%"
    is AppUpdateState.Ready -> if (automatic) {
        "Version $version is ready, and installs when you quit."
    } else {
        "Version $version is ready."
    }
    is AppUpdateState.Failed -> message
}

private fun formatUpdateCheckTime(epochMillis: Long): String =
    UPDATE_CHECK_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

@Composable
private fun VaultActions(
    vaultName: String,
    uiState: VaultSettingsUiState,
    onAction: (VaultSettingsAction) -> Unit,
) {
    val form = uiState.vaultForm

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OptionButton(selected = false, onClick = { onAction(VaultSettingsAction.LockVault) }) {
                Text("Lock vault")
            }
            OptionButton(
                selected = form.edit == VaultEdit.ChangePassword,
                onClick = {
                    val next = VaultEdit.ChangePassword.takeUnless { form.edit == it }
                    onAction(VaultSettingsAction.SetVaultEdit(next))
                },
            ) {
                Text(if (uiState.vaultHasPassword) "Change password" else "Add a password")
            }
            OptionButton(
                selected = form.edit == VaultEdit.Export,
                onClick = {
                    if (uiState.vaultHasPassword) {
                        onAction(VaultSettingsAction.SetVaultEdit(VaultEdit.Export.takeUnless { form.edit == it }))
                    } else {
                        onAction(VaultSettingsAction.ExportVault("") { pickVaultExportFile(vaultName) })
                    }
                },
            ) {
                Text("Export…")
            }
            OptionButton(
                selected = form.edit == VaultEdit.Delete,
                onClick = {
                    onAction(VaultSettingsAction.SetVaultEdit(VaultEdit.Delete.takeUnless { form.edit == it }))
                },
            ) {
                Text("Delete vault", color = Theme[colors][error])
            }
        }
        form.notice?.let { Text(it, color = Theme[colors][textSecondary]) }
        when (form.edit) {
            VaultEdit.ChangePassword -> ChangeVaultPasswordForm(uiState.vaultHasPassword, form, onAction)
            VaultEdit.Export -> ExportVaultForm(vaultName, form, onAction)
            VaultEdit.Delete -> DeleteVaultForm(vaultName, uiState.vaultHasPassword, form, onAction)
            null -> Unit
        }
        form.error?.let { Text(it, color = Theme[colors][error]) }
    }
}

@Composable
private fun ChangeVaultPasswordForm(
    hasPassword: Boolean,
    form: VaultForm,
    onAction: (VaultSettingsAction) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val submit = { onAction(VaultSettingsAction.ChangeVaultPassword(current, password, confirmation)) }
    val clearError = { onAction(VaultSettingsAction.ClearVaultError) }

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
private fun ExportVaultForm(
    vaultName: String,
    form: VaultForm,
    onAction: (VaultSettingsAction) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val submit = { value: String ->
        if (value.isNotEmpty() && !form.isBusy) {
            onAction(VaultSettingsAction.ExportVault(value) { pickVaultExportFile(vaultName) })
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier.widthIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Enter this vault's password to export it.", color = Theme[colors][textSecondary])
        PasswordField(
            value = password,
            onValueChange = {
                password = it
                onAction(VaultSettingsAction.ClearVaultError)
            },
            label = "Vault password",
            enabled = !form.isBusy,
            isError = form.error != null,
            focusRequester = focusRequester,
            onSubmit = submit,
        )
        if (password.isNotEmpty()) {
            OptionButton(selected = true, onClick = { submit(password) }) {
                Text(if (form.isBusy) "Working…" else "Choose where to save…")
            }
        }
    }
}

@Composable
private fun DeleteVaultForm(
    vaultName: String,
    hasPassword: Boolean,
    form: VaultForm,
    onAction: (VaultSettingsAction) -> Unit,
) {
    var confirmation by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

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
        if (hasPassword) {
            PasswordField(
                value = password,
                onValueChange = {
                    password = it
                    onAction(VaultSettingsAction.ClearVaultError)
                },
                label = "Vault password",
                enabled = !form.isBusy,
                isError = form.error != null,
            )
        }
        if (confirmation.trim() == vaultName && (!hasPassword || password.isNotEmpty())) {
            OptionButton(selected = true, onClick = { onAction(VaultSettingsAction.DeleteVault(password)) }) {
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
    onAction: (GeneralSettingsAction) -> Unit,
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
                    onClick = { onAction(GeneralSettingsAction.Shortcut.StartRecording(binding.shortcut)) },
                ) {
                    Text(binding.chord.label)
                }
            }
            if (!binding.isDefault) {
                OptionButton(
                    selected = false,
                    onClick = { onAction(GeneralSettingsAction.Shortcut.Reset(binding.shortcut)) },
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
private fun ShortcutRecorder(onAction: (GeneralSettingsAction) -> Unit) {
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
                else if (hadFocus) onAction(GeneralSettingsAction.Shortcut.CancelRecording)
            }
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                if (event.key == Key.Escape) {
                    onAction(GeneralSettingsAction.Shortcut.CancelRecording)
                } else {
                    event.toKeyChord()?.let { onAction(GeneralSettingsAction.Shortcut.Record(it)) }
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
    uiState: GeneralSettingsUiState,
    onAction: (GeneralSettingsAction) -> Unit,
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
                    onClick = { onAction(GeneralSettingsAction.SetColorScheme(scheme)) },
                )
            }
            uiState.userThemes.forEach { theme ->
                ColorSchemeOption(
                    label = theme.name,
                    swatch = theme.colors(isDark = true).getValue(primary),
                    selected = theme.name == uiState.activeUserThemeName,
                    onClick = { onAction(GeneralSettingsAction.SetUserTheme(theme.name)) },
                )
            }
            OptionButton(
                selected = false,
                onClick = {
                    scope.launch { pickThemeFile()?.let { onAction(GeneralSettingsAction.ImportTheme(it)) } }
                },
            ) {
                Text("Import theme…")
            }
            uiState.activeUserThemeName?.let { name ->
                OptionButton(
                    selected = false,
                    onClick = { onAction(GeneralSettingsAction.RemoveUserTheme(name)) },
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
    uiState: GeneralSettingsUiState,
    onAction: (GeneralSettingsAction) -> Unit,
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
                onClick = { onAction(GeneralSettingsAction.SetTerminalTheme(null)) },
            ) {
                Text("Follow app theme")
            }
            uiState.terminalThemes.forEach { theme ->
                TerminalThemeOption(
                    theme = theme,
                    selected = theme.name == selectedName,
                    onClick = { onAction(GeneralSettingsAction.SetTerminalTheme(theme.name)) },
                )
            }
            OptionButton(
                selected = false,
                onClick = {
                    scope.launch { pickThemeFile()?.let { onAction(GeneralSettingsAction.ImportTerminalTheme(it)) } }
                },
            ) {
                Text("Import terminal theme…")
            }
            selectedName?.takeIf { it in uiState.importedTerminalThemes }?.let { name ->
                OptionButton(
                    selected = false,
                    onClick = { onAction(GeneralSettingsAction.RemoveTerminalTheme(name)) },
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
