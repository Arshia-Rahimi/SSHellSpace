package com.sshellspace.feature.vault.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.models.MIN_VAULT_PASSWORD_LENGTH
import com.sshellspace.core.domain.models.PasswordStrength
import com.sshellspace.core.domain.models.VaultCredential
import com.sshellspace.core.domain.models.VaultException
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.models.vaultPasswordStrength
import com.sshellspace.core.domain.repositories.VaultManager
import java.io.IOException
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface VaultGateMode {
    data class Select(val selectedId: Uuid? = null) : VaultGateMode
    data object Create : VaultGateMode
    data class Import(val path: String) : VaultGateMode
    data class ChangePassword(val vault: VaultInfo) : VaultGateMode
    data class Delete(val vault: VaultInfo) : VaultGateMode
}

data class VaultGateUiState(
    val vaults: List<VaultInfo> = emptyList(),
    val mode: VaultGateMode = VaultGateMode.Select(),
    val isBusy: Boolean = false,
    val error: String? = null,
    /** Confirms something that finished without changing what's on screen, such as an export. */
    val notice: String? = null,
    val unencryptedItemCount: Long = 0,
)

sealed interface VaultGateAction {
    data class SelectVault(val id: Uuid) : VaultGateAction
    data class Unlock(val id: Uuid, val password: String) : VaultGateAction
    data object ShowCreate : VaultGateAction
    data class Create(
        val name: String,
        val password: String,
        val confirmation: String,
        val moveUnencryptedData: Boolean,
    ) : VaultGateAction
    data class ShowImport(val path: String) : VaultGateAction
    data class Import(val path: String, val password: String) : VaultGateAction
    data class Export(val vault: VaultInfo, val path: String) : VaultGateAction
    data class ShowDelete(val vault: VaultInfo) : VaultGateAction
    data class Delete(val vault: VaultInfo) : VaultGateAction
    data class ShowChangePassword(val vault: VaultInfo) : VaultGateAction
    data class ChangePassword(
        val id: Uuid,
        val current: String,
        val password: String,
        val confirmation: String,
    ) : VaultGateAction
    data object BackToList : VaultGateAction

    /** The user started correcting their input, so the last error no longer applies. */
    data object ClearError : VaultGateAction
}

class VaultGateViewModel(
    private val vaultManager: VaultManager,
) : ViewModel() {
    private val form = MutableStateFlow(VaultGateUiState())

    val uiState = combine(vaultManager.vaults, form) { vaults, form ->
        form.copy(vaults = vaults, mode = resolveMode(form.mode, vaults))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), VaultGateUiState(vaults = vaultManager.vaults.value))

    init {
        viewModelScope.launch {
            val count = vaultManager.unencryptedItemCount()
            form.update { it.copy(unencryptedItemCount = count) }
        }
    }

    fun execute(action: VaultGateAction) {
        when (action) {
            is VaultGateAction.SelectVault -> setMode(VaultGateMode.Select(action.id))
            VaultGateAction.ShowCreate -> setMode(VaultGateMode.Create)
            is VaultGateAction.ShowImport -> setMode(VaultGateMode.Import(action.path))
            is VaultGateAction.ShowChangePassword -> setMode(VaultGateMode.ChangePassword(action.vault))
            is VaultGateAction.ShowDelete -> setMode(VaultGateMode.Delete(action.vault))
            VaultGateAction.BackToList -> setMode(VaultGateMode.Select())
            VaultGateAction.ClearError -> form.update { it.copy(error = null) }
            is VaultGateAction.Unlock -> unlock(action)
            is VaultGateAction.Create -> create(action)
            is VaultGateAction.Import -> import(action)
            is VaultGateAction.Export -> run {
                vaultManager.export(action.vault.id, action.path)
                form.update { it.copy(notice = "Exported ${action.vault.name}. You'll need its password to import it.") }
            }
            is VaultGateAction.Delete -> run {
                vaultManager.delete(action.vault.id)
                setMode(VaultGateMode.Select())
                form.update { it.copy(notice = "Deleted ${action.vault.name}.") }
            }
            is VaultGateAction.ChangePassword -> changePassword(action)
        }
    }

    private fun resolveMode(mode: VaultGateMode, vaults: List<VaultInfo>): VaultGateMode = when {
        mode !is VaultGateMode.Select -> mode
        // With no vaults there is nothing to pick from, so the first thing to do is make one.
        vaults.isEmpty() -> VaultGateMode.Create
        // With only one, choosing it would be a pointless extra click.
        vaults.none { it.id == mode.selectedId } -> VaultGateMode.Select(vaults.singleOrNull()?.id)
        else -> mode
    }

    private fun unlock(action: VaultGateAction.Unlock) {
        if (action.password.isEmpty()) {
            form.update { it.copy(error = "Enter the vault's password.") }
            return
        }
        run { vaultManager.unlock(action.id, VaultCredential.Password(action.password)) }
    }

    private fun import(action: VaultGateAction.Import) {
        if (action.password.isEmpty()) {
            form.update { it.copy(error = "Enter the password this vault was exported with.") }
            return
        }
        run {
            val imported = vaultManager.import(action.path, VaultCredential.Password(action.password))
            setMode(VaultGateMode.Select(imported.id))
            form.update { it.copy(notice = "Imported ${imported.name}. Enter its password to unlock it.") }
        }
    }

    private fun create(action: VaultGateAction.Create) {
        val problem = if (action.name.isBlank()) {
            "Give the vault a name."
        } else {
            passwordProblem(action.password, action.confirmation)
        }
        if (problem != null) {
            form.update { it.copy(error = problem) }
            return
        }
        run {
            vaultManager.create(action.name, VaultCredential.Password(action.password), action.moveUnencryptedData)
        }
    }

    private fun changePassword(action: VaultGateAction.ChangePassword) {
        val problem = passwordProblem(action.password, action.confirmation)
        if (problem != null) {
            form.update { it.copy(error = problem) }
            return
        }
        run {
            vaultManager.changeCredential(
                action.id,
                VaultCredential.Password(action.current),
                VaultCredential.Password(action.password),
            )
            setMode(VaultGateMode.Select(action.id))
            form.update { it.copy(notice = "Password changed. Exports you made earlier still use the old one.") }
        }
    }

    private fun passwordProblem(password: String, confirmation: String) = when {
        password.length < MIN_VAULT_PASSWORD_LENGTH ->
            "The password needs at least $MIN_VAULT_PASSWORD_LENGTH characters."
        vaultPasswordStrength(password) == PasswordStrength.Weak -> "This password is too easy to guess."
        password != confirmation -> "The passwords don't match."
        else -> null
    }

    private fun setMode(mode: VaultGateMode) {
        form.update { it.copy(mode = mode, error = null, notice = null) }
    }

    /** Runs a vault operation one at a time, showing progress and any failure. */
    private fun run(block: suspend () -> Unit) {
        if (form.value.isBusy) return
        form.update { it.copy(isBusy = true, error = null, notice = null) }
        viewModelScope.launch {
            val error = try {
                block()
                null
            } catch (e: VaultException) {
                e.message
            } catch (e: IOException) {
                "Couldn't access the file: ${e.message}"
            }
            form.update { it.copy(isBusy = false, error = error) }
        }
    }
}
