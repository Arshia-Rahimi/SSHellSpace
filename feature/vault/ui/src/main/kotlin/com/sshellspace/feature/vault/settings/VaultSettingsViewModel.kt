package com.sshellspace.feature.vault.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.models.VaultException
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.domain.repositories.VaultPreferencesRepository
import com.sshellspace.feature.vault.gate.credentialFrom
import com.sshellspace.feature.vault.gate.passwordMismatch
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The unlocked vault's own settings. Only use while a vault is unlocked. */
class VaultSettingsViewModel(
    private val vaultManager: VaultManager,
    private val vaultPreferencesRepository: VaultPreferencesRepository,
) : ViewModel() {
    private val vaultForm = MutableStateFlow(VaultForm())

    val uiState = combine(
        vaultManager.activeVault,
        vaultPreferencesRepository.getAutoLockMinutes(),
        vaultForm,
    ) { vault, autoLockMinutes, form ->
        VaultSettingsUiState(
            vaultName = vault?.name,
            vaultHasPassword = vault?.hasPassword ?: true,
            autoLockMinutes = autoLockMinutes,
            vaultForm = form,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), VaultSettingsUiState())

    fun execute(action: VaultSettingsAction) {
        when (action) {
            VaultSettingsAction.LockVault -> viewModelScope.launch { vaultManager.lock() }
            is VaultSettingsAction.SetVaultEdit -> vaultForm.update { VaultForm(edit = action.edit) }
            VaultSettingsAction.ClearVaultError -> vaultForm.update { it.copy(error = null) }
            is VaultSettingsAction.ExportVault -> exportVault(action)
            is VaultSettingsAction.ChangeVaultPassword -> changeVaultPassword(action)
            is VaultSettingsAction.DeleteVault -> runVaultAction { vault ->
                vaultManager.delete(vault.id, credentialFrom(action.password))
            }
            is VaultSettingsAction.SetAutoLockMinutes -> viewModelScope.launch {
                vaultPreferencesRepository.setAutoLockMinutes(action.minutes)
            }
        }
    }

    private fun exportVault(action: VaultSettingsAction.ExportVault) {
        val credential = credentialFrom(action.password)
        runVaultAction { vault ->
            // First, so a mistyped password doesn't send the user through the save dialog for nothing.
            vaultManager.verifyCredential(vault.id, credential)
            val path = action.pickDestination() ?: return@runVaultAction
            vaultManager.export(vault.id, credential, path)
            vaultForm.update { VaultForm(notice = exportNotice(vault)) }
        }
    }

    private fun exportNotice(vault: VaultInfo) = if (vault.hasPassword) {
        "Exported ${vault.name}. You'll need its password to import it."
    } else {
        "Exported ${vault.name}. It has no password, so anyone with that file can read it."
    }

    private fun changeVaultPassword(action: VaultSettingsAction.ChangeVaultPassword) {
        passwordMismatch(action.password, action.confirmation)?.let { problem ->
            vaultForm.update { it.copy(error = problem) }
            return
        }
        runVaultAction { vault ->
            vaultManager.changeCredential(vault.id, credentialFrom(action.current), credentialFrom(action.password))
            val message = if (action.password.isEmpty()) {
                "Password removed. Exports you made earlier still open with the old one."
            } else {
                "Password changed. Exports you made earlier still open with the old one."
            }
            vaultForm.update { VaultForm(notice = message) }
        }
    }

    /** Runs an action on the unlocked vault, one at a time, showing progress and any failure. */
    private fun runVaultAction(block: suspend (VaultInfo) -> Unit) {
        val vault = vaultManager.activeVault.value ?: return
        if (vaultForm.value.isBusy) return
        vaultForm.update { it.copy(isBusy = true, error = null, notice = null) }
        viewModelScope.launch {
            val error = try {
                block(vault)
                null
            } catch (e: VaultException) {
                e.message
            } catch (e: IOException) {
                "Couldn't access the file: ${e.message}"
            }
            vaultForm.update { it.copy(isBusy = false, error = error) }
        }
    }
}
