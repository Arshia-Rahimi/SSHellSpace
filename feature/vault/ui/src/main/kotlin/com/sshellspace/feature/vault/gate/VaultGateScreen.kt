package com.sshellspace.feature.vault.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.composeunstyled.AnchorAlignment
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.MIN_VAULT_PASSWORD_LENGTH
import com.sshellspace.core.domain.models.PasswordStrength
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.models.vaultPasswordStrength
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.MenuItem
import com.sshellspace.core.ui.designsystem.components.MenuPanel
import com.sshellspace.core.ui.designsystem.components.Switch
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.onError
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.success
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.core.ui.designsystem.theme.warning
import java.io.File
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val GateMaxWidth = 420.dp
private val ButtonShape = RoundedCornerShape(6.dp)

/** Shown until a vault is unlocked: pick a vault and authenticate, or create, import, export or delete one. */
@Composable
fun VaultGateScreen(modifier: Modifier = Modifier) {
    // There's no tab to own this ViewModel yet, and it should go as soon as a vault unlocks.
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }

    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        val viewModel = koinViewModel<VaultGateViewModel>()
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        VaultGateContent(uiState, viewModel::execute, modifier)
    }
}

@Composable
internal fun VaultGateContent(
    uiState: VaultGateUiState,
    onAction: (VaultGateAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        // Centered when it fits, scrolling when it doesn't, so it still works at the minimum window height.
        val availableHeight = maxHeight
        Box(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = availableHeight),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(16.dp).widthIn(max = GateMaxWidth).fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Theme[colors][surface])
                    .border(1.dp, Theme[colors][border], RoundedCornerShape(10.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (val mode = uiState.mode) {
                    is VaultGateMode.Select -> VaultList(uiState, mode.selectedId, onAction)
                    VaultGateMode.Create -> CreateVaultForm(uiState, onAction)
                    is VaultGateMode.Import -> ImportVaultForm(uiState, mode.path, onAction)
                    is VaultGateMode.ChangePassword -> ChangePasswordForm(uiState, mode.vault, onAction)
                    is VaultGateMode.Delete -> DeleteVaultForm(uiState, mode.vault, onAction)
                }
            }
        }
    }
}

@Composable
private fun VaultList(
    uiState: VaultGateUiState,
    selectedId: kotlin.uuid.Uuid?,
    onAction: (VaultGateAction) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Heading(
        title = "Unlock a vault",
        subtitle = if (selectedId == null) "Choose the vault to open." else "Enter the password to open it.",
    )
    uiState.notice?.let { Callout(it, Theme[colors][success]) }
    if (uiState.unencryptedItemCount > 0) {
        Callout(
            "${uiState.unencryptedItemCount} hosts, keys and forwards from an earlier version aren't encrypted yet. " +
                "Create a new vault to move them into it.",
            Theme[colors][warning],
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        uiState.vaults.forEach { vault ->
            VaultRow(
                vault = vault,
                selected = vault.id == selectedId,
                uiState = uiState,
                onAction = onAction,
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Theme[colors][separator]))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GateButton(
            "New vault",
            modifier = Modifier.weight(1f),
            enabled = !uiState.isBusy,
            onClick = { onAction(VaultGateAction.ShowCreate) },
        )
        GateButton(
            "Import file…",
            modifier = Modifier.weight(1f),
            enabled = !uiState.isBusy,
            onClick = { scope.launch { pickVaultFile()?.let { onAction(VaultGateAction.ShowImport(it)) } } },
        )
    }
}

@Composable
private fun VaultRow(
    vault: VaultInfo,
    selected: Boolean,
    uiState: VaultGateUiState,
    onAction: (VaultGateAction) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(if (selected) Theme[colors][surfaceElevated] else Color.Transparent)
            .border(1.dp, if (selected) Theme[colors][primary] else Theme[colors][border], shape),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { onAction(VaultGateAction.SelectVault(vault.id)) },
                enabled = !uiState.isBusy,
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    UnstyledIcon(
                        imageVector = Icons.Vault,
                        contentDescription = null,
                        tint = Theme[colors][if (selected) primary else textSecondary],
                        modifier = Modifier.size(16.dp),
                    )
                    Text(vault.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            VaultActionsMenu(vault, enabled = !uiState.isBusy, onAction = onAction)
        }
        if (selected) UnlockForm(vault, uiState, onAction)
    }
}

@Composable
private fun UnlockForm(
    vault: VaultInfo,
    uiState: VaultGateUiState,
    onAction: (VaultGateAction) -> Unit,
) {
    var password by remember(vault.id) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val unlock = { onAction(VaultGateAction.Unlock(vault.id, password)) }

    LaunchedEffect(vault.id) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PasswordField(
            value = password,
            onValueChange = {
                password = it
                onAction(VaultGateAction.ClearError)
            },
            label = "Password",
            enabled = !uiState.isBusy,
            isError = uiState.error != null,
            focusRequester = focusRequester,
            onSubmit = { unlock() },
        )
        ErrorText(uiState.error)
        PrimaryButton(if (uiState.isBusy) "Unlocking…" else "Unlock", enabled = !uiState.isBusy, onClick = unlock)
    }
}

/** Everything else you can do with a vault. Kept out of the way, so unlocking is the obvious next step. */
@Composable
private fun VaultActionsMenu(
    vault: VaultInfo,
    enabled: Boolean,
    onAction: (VaultGateAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    UnstyledDropdownMenu(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        alignment = AnchorAlignment.End,
        sideOffset = 4.dp,
        anchor = {
            IconButton(
                onClick = { expanded = !expanded },
                imageVector = Icons.More,
                contentDescription = "Actions for ${vault.name}",
                tint = Theme[colors][textSecondary],
                enabled = enabled,
                modifier = Modifier.padding(end = 8.dp),
            )
        },
        panel = {
            MenuPanel(width = 190.dp) {
                MenuItem(onClick = { onAction(VaultGateAction.ShowChangePassword(vault)) }) {
                    Text("Change password")
                }
                MenuItem(
                    onClick = {
                        scope.launch {
                            pickVaultExportFile(vault.name)?.let { onAction(VaultGateAction.Export(vault, it)) }
                        }
                    },
                ) {
                    Text("Export to a file…")
                }
                MenuItem(onClick = { onAction(VaultGateAction.ShowDelete(vault)) }) {
                    Text("Delete", color = Theme[colors][error])
                }
            }
        },
    )
}

@Composable
private fun CreateVaultForm(
    uiState: VaultGateUiState,
    onAction: (VaultGateAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isFirstVault = uiState.vaults.isEmpty()
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var moveData by remember { mutableStateOf(true) }
    val focusRequester = remember { FocusRequester() }
    val create = { onAction(VaultGateAction.Create(name, password, confirmation, moveData)) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Heading(
        title = if (isFirstVault) "Create your first vault" else "Create a vault",
        subtitle = "A vault keeps your hosts, keys and passwords encrypted. Use separate vaults for things like " +
            "work and personal.",
    )
    TextField(
        value = name,
        onValueChange = {
            name = it
            onAction(VaultGateAction.ClearError)
        },
        label = "Vault name",
        placeholder = "Personal",
        enabled = !uiState.isBusy,
        focusRequester = focusRequester,
    )
    NewPasswordFields(
        password = password,
        onPasswordChange = {
            password = it
            onAction(VaultGateAction.ClearError)
        },
        confirmation = confirmation,
        onConfirmationChange = {
            confirmation = it
            onAction(VaultGateAction.ClearError)
        },
        enabled = !uiState.isBusy,
        onSubmit = create,
    )
    Callout(
        "There's no password recovery. If you forget this password, nobody can open the vault.",
        Theme[colors][warning],
    )
    if (uiState.unencryptedItemCount > 0) {
        MoveDataOption(uiState.unencryptedItemCount, moveData) { moveData = it }
    }
    ErrorText(uiState.error)
    PrimaryButton(if (uiState.isBusy) "Creating…" else "Create vault", enabled = !uiState.isBusy, onClick = create)
    if (isFirstVault) {
        GateButton(
            "Import an existing vault instead…",
            enabled = !uiState.isBusy,
            onClick = { scope.launch { pickVaultFile()?.let { onAction(VaultGateAction.ShowImport(it)) } } },
        )
    } else {
        BackButton(uiState, onAction)
    }
}

@Composable
private fun MoveDataOption(count: Long, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Move my existing data into this vault")
            Text(
                if (checked) {
                    "$count hosts, keys and forwards will move in, and the unencrypted copies will be erased. " +
                        "Backups of this computer may still hold them, so consider changing saved passwords."
                } else {
                    "$count hosts, keys and forwards stay unencrypted and won't appear in this vault."
                },
                color = Theme[colors][textSecondary],
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, accessibilityLabel = "Move my existing data")
    }
}

@Composable
private fun ImportVaultForm(
    uiState: VaultGateUiState,
    path: String,
    onAction: (VaultGateAction) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val import = { onAction(VaultGateAction.Import(path, password)) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Heading(title = "Import a vault", subtitle = "Enter the password this vault had when it was exported.")
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(File(path).parent.orEmpty(), color = Theme[colors][textSecondary], maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    PasswordField(
        value = password,
        onValueChange = {
            password = it
            onAction(VaultGateAction.ClearError)
        },
        label = "Vault password",
        enabled = !uiState.isBusy,
        isError = uiState.error != null,
        focusRequester = focusRequester,
        onSubmit = { import() },
    )
    ErrorText(uiState.error)
    PrimaryButton(if (uiState.isBusy) "Importing…" else "Import vault", enabled = !uiState.isBusy, onClick = import)
    BackButton(uiState, onAction)
}

@Composable
private fun ChangePasswordForm(
    uiState: VaultGateUiState,
    vault: VaultInfo,
    onAction: (VaultGateAction) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val change = { onAction(VaultGateAction.ChangePassword(vault.id, current, password, confirmation)) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Heading(
        title = "Change password",
        subtitle = "For ${vault.name}. Exports you made earlier still open with the old password.",
    )
    PasswordField(
        value = current,
        onValueChange = {
            current = it
            onAction(VaultGateAction.ClearError)
        },
        label = "Current password",
        enabled = !uiState.isBusy,
        focusRequester = focusRequester,
    )
    NewPasswordFields(
        password = password,
        onPasswordChange = {
            password = it
            onAction(VaultGateAction.ClearError)
        },
        confirmation = confirmation,
        onConfirmationChange = {
            confirmation = it
            onAction(VaultGateAction.ClearError)
        },
        enabled = !uiState.isBusy,
        label = "New password",
        onSubmit = change,
    )
    ErrorText(uiState.error)
    PrimaryButton(if (uiState.isBusy) "Changing…" else "Change password", enabled = !uiState.isBusy, onClick = change)
    BackButton(uiState, onAction)
}

@Composable
private fun DeleteVaultForm(
    uiState: VaultGateUiState,
    vault: VaultInfo,
    onAction: (VaultGateAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirmation by remember { mutableStateOf("") }

    Heading(
        title = "Delete ${vault.name}?",
        subtitle = "Its hosts, keys and saved passwords are removed from this computer for good. Key files stored " +
            "elsewhere on this computer aren't touched.",
    )
    GateButton(
        "Export a copy first…",
        enabled = !uiState.isBusy,
        onClick = {
            scope.launch { pickVaultExportFile(vault.name)?.let { onAction(VaultGateAction.Export(vault, it)) } }
        },
    )
    uiState.notice?.let { Callout(it, Theme[colors][success]) }
    TextField(
        value = confirmation,
        onValueChange = { confirmation = it },
        label = "Type ${vault.name} to confirm",
        enabled = !uiState.isBusy,
    )
    ErrorText(uiState.error)
    FilledButton(
        label = if (uiState.isBusy) "Deleting…" else "Delete vault",
        background = Theme[colors][error],
        content = Theme[colors][onError],
        enabled = !uiState.isBusy && confirmation.trim() == vault.name,
        onClick = { onAction(VaultGateAction.Delete(vault)) },
    )
    BackButton(uiState, onAction)
}

/** A new password with live feedback, so problems show while typing rather than after submitting. */
@Composable
private fun NewPasswordFields(
    password: String,
    onPasswordChange: (String) -> Unit,
    confirmation: String,
    onConfirmationChange: (String) -> Unit,
    enabled: Boolean,
    onSubmit: () -> Unit,
    label: String = "Password",
) {
    val mismatch = confirmation.isNotEmpty() && confirmation != password

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PasswordField(value = password, onValueChange = onPasswordChange, label = label, enabled = enabled)
        PasswordStrengthMeter(password)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PasswordField(
            value = confirmation,
            onValueChange = onConfirmationChange,
            label = "Confirm ${label.lowercase()}",
            enabled = enabled,
            isError = mismatch,
            onSubmit = { onSubmit() },
        )
        if (mismatch) ErrorText("The passwords don't match.")
    }
}

@Composable
private fun PasswordStrengthMeter(password: String) {
    val tooShort = password.length < MIN_VAULT_PASSWORD_LENGTH
    val strength = vaultPasswordStrength(password)
    val (filled, color, message) = when {
        password.isEmpty() -> Triple(0, Theme[colors][border], "Use at least $MIN_VAULT_PASSWORD_LENGTH characters.")
        tooShort -> Triple(
            1,
            Theme[colors][error],
            "${MIN_VAULT_PASSWORD_LENGTH - password.length} more characters needed.",
        )
        strength == PasswordStrength.Weak -> Triple(1, Theme[colors][error], "Too easy to guess.")
        strength == PasswordStrength.Fair -> Triple(2, Theme[colors][warning], "Fair. Longer is stronger.")
        else -> Triple(3, Theme[colors][success], "Strong.")
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { index ->
            Box(
                Modifier.weight(1f).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index < filled) color else Theme[colors][border]),
            )
        }
    }
    Text(message, color = Theme[colors][textSecondary])
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    isError: Boolean = false,
    focusRequester: FocusRequester? = null,
    onSubmit: ((String) -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        isPassword = !visible,
        enabled = enabled,
        isError = isError,
        focusRequester = focusRequester,
        onSubmit = onSubmit,
        // The icon is taller than a line of text, so this keeps the field as tall as the plain ones beside it.
        contentPadding = PaddingValues(start = 12.dp, end = 8.dp, top = 5.dp, bottom = 5.dp),
        trailing = {
            IconButton(
                onClick = { visible = !visible },
                imageVector = if (visible) Icons.EyeClosed else Icons.EyeOpen,
                contentDescription = if (visible) "Hide password" else "Show password",
                tint = Theme[colors][textSecondary],
            )
        },
    )
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Theme[colors][textSecondary])
    }
}

@Composable
private fun Callout(text: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(ButtonShape)
            .background(color.copy(alpha = CALLOUT_ALPHA))
            .padding(10.dp),
    ) {
        Text(text)
    }
}

@Composable
private fun ErrorText(text: String?) {
    if (text != null) Text(text, color = Theme[colors][error])
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledButton(label, Theme[colors][primary], Theme[colors][onPrimary], enabled, onClick)
}

@Composable
private fun FilledButton(label: String, background: Color, content: Color, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(ButtonShape)
            .background(background)
            .padding(vertical = 10.dp),
    ) {
        Text(label, color = content)
    }
}

@Composable
private fun BackButton(uiState: VaultGateUiState, onAction: (VaultGateAction) -> Unit) {
    GateButton("Back", enabled = !uiState.isBusy, onClick = { onAction(VaultGateAction.BackToList) })
}

@Composable
private fun GateButton(
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(ButtonShape)
            .border(1.dp, Theme[colors][border], ButtonShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label)
    }
}

private const val CALLOUT_ALPHA = 0.14f
private const val DISABLED_ALPHA = 0.5f
