package com.sshellspace.feature.vault.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.PasswordStrength
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.models.vaultPasswordStrength
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.Switch
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private val GateMaxWidth = 460.dp

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
private fun VaultGateContent(
    uiState: VaultGateUiState,
    onAction: (VaultGateAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // Tall content has to scroll to still fit the minimum window height.
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = GateMaxWidth).fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][border], RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val mode = uiState.mode) {
                is VaultGateMode.Select -> VaultList(uiState, mode.selectedId, onAction)
                VaultGateMode.Create -> CreateVaultForm(uiState, onAction)
                is VaultGateMode.Import -> ImportVaultForm(uiState, mode.path, onAction)
                is VaultGateMode.ChangePassword -> ChangePasswordForm(uiState, mode.vault, onAction)
            }
            uiState.error?.let { Text(it, color = Theme[colors][error]) }
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

    Text("Choose a vault")
    if (uiState.unencryptedItemCount > 0) {
        Text(
            "${uiState.unencryptedItemCount} hosts, keys and forwards from before vaults are still unencrypted on " +
                "this device. Create a new vault to move them.",
            color = Theme[colors][error],
        )
    }
    uiState.vaults.forEach { vault ->
        VaultRow(
            vault = vault,
            selected = vault.id == (selectedId ?: uiState.vaults.singleOrNull()?.id),
            isBusy = uiState.isBusy,
            onAction = onAction,
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GateButton("New vault", enabled = !uiState.isBusy) { onAction(VaultGateAction.ShowCreate) }
        GateButton("Import vault…", enabled = !uiState.isBusy) {
            scope.launch { pickVaultFile()?.let { onAction(VaultGateAction.ShowImport(it)) } }
        }
    }
}

@Composable
private fun VaultRow(
    vault: VaultInfo,
    selected: Boolean,
    isBusy: Boolean,
    onAction: (VaultGateAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var password by remember(vault.id, selected) { mutableStateOf("") }
    var confirmingDelete by remember(vault.id, selected) { mutableStateOf(false) }
    val unlock = { onAction(VaultGateAction.Unlock(vault.id, password)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GateButton(
            label = vault.name,
            selected = selected,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { onAction(VaultGateAction.SelectVault(vault.id)) }

        if (selected) {
            TextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                isPassword = true,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                onSubmit = { unlock() },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GateButton(if (isBusy) "Working…" else "Unlock", selected = true, enabled = !isBusy) { unlock() }
                GateButton("Change password", enabled = !isBusy) {
                    onAction(VaultGateAction.ShowChangePassword(vault))
                }
                GateButton("Export…", enabled = !isBusy) {
                    scope.launch {
                        pickVaultExportFile(vault.name)?.let { onAction(VaultGateAction.Export(vault.id, it)) }
                    }
                }
                if (confirmingDelete) {
                    GateButton("Delete permanently", enabled = !isBusy) { onAction(VaultGateAction.Delete(vault.id)) }
                    GateButton("Keep", enabled = !isBusy) { confirmingDelete = false }
                } else {
                    GateButton("Delete", enabled = !isBusy) { confirmingDelete = true }
                }
            }
            if (confirmingDelete) {
                Text(
                    "Everything in this vault will be gone unless you have an export of it.",
                    color = Theme[colors][error],
                )
            }
        }
    }
}

@Composable
private fun CreateVaultForm(
    uiState: VaultGateUiState,
    onAction: (VaultGateAction) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var moveData by remember { mutableStateOf(true) }
    val create = { onAction(VaultGateAction.Create(name, password, confirmation, moveData)) }

    Text("Create a vault")
    Text(
        "Your hosts, keys and passwords are encrypted with this password. It can't be recovered: " +
            "if you forget it, the vault can't be opened.",
        color = Theme[colors][textSecondary],
    )
    TextField(
        value = name,
        onValueChange = { name = it },
        label = "Name",
        enabled = !uiState.isBusy,
        modifier = Modifier.fillMaxWidth(),
    )
    TextField(
        value = password,
        onValueChange = { password = it },
        label = "Password",
        isPassword = true,
        enabled = !uiState.isBusy,
        modifier = Modifier.fillMaxWidth(),
    )
    PasswordStrengthText(password)
    TextField(
        value = confirmation,
        onValueChange = { confirmation = it },
        label = "Confirm password",
        isPassword = true,
        enabled = !uiState.isBusy,
        modifier = Modifier.fillMaxWidth(),
        onSubmit = { create() },
    )
    if (uiState.unencryptedItemCount > 0) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Switch(checked = moveData, onCheckedChange = { moveData = it })
            Text(
                "Move my ${uiState.unencryptedItemCount} existing hosts, keys and forwards into this vault, and " +
                    "erase the unencrypted copies. Backups of this computer may still hold them, so consider " +
                    "changing any saved passwords and keys.",
                color = Theme[colors][textSecondary],
                modifier = Modifier.weight(1f),
            )
        }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GateButton(if (uiState.isBusy) "Working…" else "Create", selected = true, enabled = !uiState.isBusy) { create() }
        if (uiState.vaults.isEmpty()) {
            GateButton("Import vault…", enabled = !uiState.isBusy) {
                scope.launch { pickVaultFile()?.let { onAction(VaultGateAction.ShowImport(it)) } }
            }
        } else {
            GateButton("Back", enabled = !uiState.isBusy) { onAction(VaultGateAction.BackToList) }
        }
    }
}

@Composable
private fun ImportVaultForm(
    uiState: VaultGateUiState,
    path: String,
    onAction: (VaultGateAction) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    val import = { onAction(VaultGateAction.Import(path, password)) }

    Text("Import a vault")
    Text(path, color = Theme[colors][textSecondary])
    TextField(
        value = password,
        onValueChange = { password = it },
        label = "The vault's password",
        isPassword = true,
        enabled = !uiState.isBusy,
        modifier = Modifier.fillMaxWidth(),
        onSubmit = { import() },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GateButton(if (uiState.isBusy) "Working…" else "Import", selected = true, enabled = !uiState.isBusy) { import() }
        GateButton("Back", enabled = !uiState.isBusy) { onAction(VaultGateAction.BackToList) }
    }
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
    val change = { onAction(VaultGateAction.ChangePassword(vault.id, current, password, confirmation)) }

    Text("Change the password of ${vault.name}")
    Text(
        "Exports you made earlier still open with the old password.",
        color = Theme[colors][textSecondary],
    )
    TextField(
        value = current,
        onValueChange = { current = it },
        label = "Current password",
        isPassword = true,
        enabled = !uiState.isBusy,
        modifier = Modifier.fillMaxWidth(),
    )
    TextField(
        value = password,
        onValueChange = { password = it },
        label = "New password",
        isPassword = true,
        enabled = !uiState.isBusy,
        modifier = Modifier.fillMaxWidth(),
    )
    PasswordStrengthText(password)
    TextField(
        value = confirmation,
        onValueChange = { confirmation = it },
        label = "Confirm new password",
        isPassword = true,
        enabled = !uiState.isBusy,
        modifier = Modifier.fillMaxWidth(),
        onSubmit = { change() },
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GateButton(if (uiState.isBusy) "Working…" else "Change password", selected = true, enabled = !uiState.isBusy) {
            change()
        }
        GateButton("Back", enabled = !uiState.isBusy) { onAction(VaultGateAction.BackToList) }
    }
}

@Composable
private fun PasswordStrengthText(password: String) {
    if (password.isEmpty()) return
    val strength = vaultPasswordStrength(password)
    Text(
        "Password strength: ${strength.name.lowercase()}",
        color = if (strength == PasswordStrength.Weak) Theme[colors][error] else Theme[colors][textSecondary],
    )
}

@Composable
private fun GateButton(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .clip(shape)
            .background(if (selected) Theme[colors][surfaceElevated] else Color.Transparent)
            .border(1.dp, if (selected) Theme[colors][primary] else Theme[colors][border], shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Box { Text(label) }
    }
}
