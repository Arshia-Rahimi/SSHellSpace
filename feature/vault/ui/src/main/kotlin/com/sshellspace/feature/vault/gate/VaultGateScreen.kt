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
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.domain.models.AppUpdateState
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.Switch
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
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
    // Only for the moment a read takes, and an empty card would flicker.
    if (uiState.mode == VaultGateMode.Loading) return

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
                    VaultGateMode.Loading -> Unit
                    VaultGateMode.Welcome -> WelcomeSteps(onAction)
                    is VaultGateMode.Select -> VaultList(uiState, mode.selectedId, onAction)
                    VaultGateMode.Create -> CreateVaultForm(uiState, onAction)
                    is VaultGateMode.Import -> ImportVaultForm(uiState, mode.path, onAction)
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
        subtitle = "Choose the vault to open.",
    )
    uiState.notice?.let { Callout(it, Theme[colors][success]) }
    uiState.update?.let { UpdateHint(it, uiState, onAction) }
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
private fun UpdateHint(
    update: AppUpdateState,
    uiState: VaultGateUiState,
    onAction: (VaultGateAction) -> Unit,
) {
    val color = Theme[colors][if (update is AppUpdateState.Failed) error else primary]
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(ButtonShape)
            .background(color.copy(alpha = CALLOUT_ALPHA))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (update) {
            is AppUpdateState.Available -> {
                Text("Version ${update.version} is available.")
                GateButton("Download", onClick = { onAction(VaultGateAction.Update.Download) })
            }
            is AppUpdateState.Downloading -> Text("Downloading version ${update.version}… ${update.percent}%")
            is AppUpdateState.Ready -> {
                Text("Version ${update.version} is ready to install.")
                RestartToUpdate(uiState, onAction)
            }
            is AppUpdateState.Failed -> Text(update.message)
            else -> Unit
        }
    }
}

@Composable
private fun RestartToUpdate(
    uiState: VaultGateUiState,
    onAction: (VaultGateAction) -> Unit,
) {
    val open = uiState.confirmingRestart
    if (open == null) {
        GateButton(
            "Restart to update",
            enabled = !uiState.isBusy,
            onClick = { onAction(VaultGateAction.Update.Restart) },
        )
        return
    }
    Text(
        if (open == 1) {
            "1 tab or port forward is still running behind this lock, and will close."
        } else {
            "$open tabs and port forwards are still running behind this lock, and will close."
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GateButton(
            "Restart anyway",
            modifier = Modifier.weight(1f),
            enabled = !uiState.isBusy,
            onClick = { onAction(VaultGateAction.Update.ConfirmRestart) },
        )
        GateButton(
            "Cancel",
            modifier = Modifier.weight(1f),
            onClick = { onAction(VaultGateAction.Update.CancelRestart) },
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
                    Text(vault.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!vault.hasPassword) Text("No password", color = Theme[colors][textSecondary])
                }
            }
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

    LaunchedEffect(vault.id) { if (vault.hasPassword) focusRequester.requestFocus() }

    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (vault.hasPassword) {
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
        }
        ErrorText(uiState.error)
        PrimaryButton(
            label = when {
                uiState.isBusy -> "Opening…"
                vault.hasPassword -> "Unlock"
                else -> "Open"
            },
            enabled = !uiState.isBusy,
            onClick = unlock,
        )
    }
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
    if (password.isNotEmpty()) {
        Callout(
            "There's no password recovery. If you forget this password, nobody can open the vault.",
            Theme[colors][warning],
        )
    }
    if (uiState.unencryptedItemCount > 0) {
        MoveDataOption(uiState.unencryptedItemCount, moveData) { moveData = it }
    }
    ErrorText(uiState.error)
    PrimaryButton(
        label = when {
            uiState.isBusy -> "Creating…"
            password.isEmpty() -> "Create without a password"
            else -> "Create vault"
        },
        enabled = !uiState.isBusy,
        onClick = create,
    )
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

    Heading(
        title = "Import a vault",
        subtitle = "Enter the password this vault had when it was exported. Leave it empty if it had none.",
    )
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
internal fun Heading(title: String, subtitle: String) {
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
internal fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
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
internal fun GateButton(
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
