package com.sshellspace.feature.vault.sshkeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.feature.vault.domain.CreateSshKeyResult
import com.sshellspace.feature.vault.domain.SshKeyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SshKeysPageViewModel(
    private val sshKeyRepository: SshKeyRepository,
) : ViewModel() {
    private val sshKeys = sshKeyRepository.getKeys()
        .map { it.map { key -> key.toSshKey() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val sidebarState = MutableStateFlow<SidebarState>(SidebarState.None)
    private val isSaveInFlight = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState = combine(
        sshKeys,
        sidebarState,
        isSaveInFlight,
        errorMessage,
    ) { keys, sidebar, inFlight, error ->
        SshKeysPageUiState(
            keys = keys,
            sidebarState = sidebar,
            isSaveInFlight = inFlight,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SshKeysPageUiState())

    fun execute(action: SshKeysPageAction) {
        when (action) {
            is SshKeysPageAction.SelectKey -> selectKey(action.key)
            SshKeysPageAction.CreateNewKey -> openVaultCreating()
            SshKeysPageAction.CloseSidebar -> closeSidebar()
            SshKeysPageAction.DismissError -> errorMessage.update { null }

            is SshKeysPageAction.Editing.SetName -> setEditingName(action.value)
            SshKeysPageAction.Editing.Save -> saveEditing()
            SshKeysPageAction.Editing.Delete -> deleteKey()

            is SshKeysPageAction.VaultKey.SetName -> updateVaultCreating { copy(nameField = action.value) }
            is SshKeysPageAction.VaultKey.SetComment -> updateVaultCreating { copy(comment = action.value) }
            is SshKeysPageAction.VaultKey.SetAlgorithm -> updateVaultCreating { copy(algorithm = action.value) }
            SshKeysPageAction.VaultKey.Save -> saveVaultKey()

            is SshKeysPageAction.DeviceKey.SetName -> updateDeviceCreating { copy(nameField = action.value) }
            is SshKeysPageAction.DeviceKey.SetComment -> updateDeviceCreating { copy(comment = action.value) }
            is SshKeysPageAction.DeviceKey.SetFileName -> updateDeviceCreating { copy(fileName = action.value) }
            is SshKeysPageAction.DeviceKey.SetPath -> updateDeviceCreating { copy(path = action.value) }
            is SshKeysPageAction.DeviceKey.SetAlgorithm -> updateDeviceCreating { copy(algorithm = action.value) }
            SshKeysPageAction.DeviceKey.Save -> saveDeviceKey()

            is SshKeysPageAction.ImportKey.SetName -> updateImportCreating { copy(nameField = action.value) }
            is SshKeysPageAction.ImportKey.SetPath -> updateImportCreating { copy(path = action.value) }
            is SshKeysPageAction.ImportKey.SetPassphrase -> updateImportCreating { copy(passphrase = action.value) }
            is SshKeysPageAction.ImportKey.TogglePassphraseVisibility -> updateImportCreating { copy(passphraseVisible = action.value) }
            SshKeysPageAction.ImportKey.Save -> importKey()
        }
    }

    private fun selectKey(key: SshKey) {
        errorMessage.update { null }
        sidebarState.update {
            SidebarState.Editing(key, key.name, null)
        }
    }

    private fun openVaultCreating() {
        errorMessage.update { null }
        sidebarState.update { SidebarState.VaultKeyCreating() }
    }

    private fun closeSidebar() {
        errorMessage.update { null }
        sidebarState.update { SidebarState.None }
    }

    private fun setEditingName(name: String) {
        sidebarState.update { state ->
            if (state !is SidebarState.Editing) return@update state
            state.copy(
                nameField = name,
                nameFieldError = if (sshKeys.value.none { it.name == name }) null else "$name already exists.",
            )
        }
    }

    private fun saveEditing() {
        val state = sidebarState.value as? SidebarState.Editing ?: return
        if (state.nameField.isBlank()) {
            errorMessage.update { "Name cannot be empty" }
            return
        }
        if (state.nameField == state.key.name) {
            closeSidebar()
            return
        }
        isSaveInFlight.update { true }
        viewModelScope.launch {
            sshKeyRepository.updateName(state.key.id, state.nameField)
            isSaveInFlight.update { false }
            closeSidebar()
        }
    }

    private fun deleteKey() {
        val state = sidebarState.value as? SidebarState.Editing ?: return
        isSaveInFlight.update { true }
        viewModelScope.launch {
            sshKeyRepository.delete(state.key.id)
            isSaveInFlight.update { false }
            closeSidebar()
        }
    }

    private fun updateVaultCreating(transform: SidebarState.VaultKeyCreating.() -> SidebarState.VaultKeyCreating) {
        sidebarState.update { state ->
            if (state !is SidebarState.VaultKeyCreating) return@update state
            state.transform()
        }
    }

    private fun updateDeviceCreating(transform: SidebarState.DeviceKeyCreating.() -> SidebarState.DeviceKeyCreating) {
        sidebarState.update { state ->
            if (state !is SidebarState.DeviceKeyCreating) return@update state
            state.transform()
        }
    }

    private fun updateImportCreating(transform: SidebarState.ImportKeyCreating.() -> SidebarState.ImportKeyCreating) {
        sidebarState.update { state ->
            if (state !is SidebarState.ImportKeyCreating) return@update state
            state.transform()
        }
    }

    private fun saveVaultKey() {
        val state = sidebarState.value as? SidebarState.VaultKeyCreating ?: return
        if (state.nameField.isBlank()) {
            errorMessage.update { "Name cannot be empty" }
            return
        }
        isSaveInFlight.update { true }
        viewModelScope.launch {
            when (val result = sshKeyRepository.createVaultKey(state.nameField, state.comment, state.algorithm)) {
                is CreateSshKeyResult.Success -> closeSidebar()
                is CreateSshKeyResult.Error -> errorMessage.update { result.message ?: "Failed to create vault key" }
            }
            isSaveInFlight.update { false }
        }
    }

    private fun saveDeviceKey() {
        val state = sidebarState.value as? SidebarState.DeviceKeyCreating ?: return
        if (state.nameField.isBlank()) {
            errorMessage.update { "Name cannot be empty" }
            return
        }
        isSaveInFlight.update { true }
        viewModelScope.launch {
            when (val result = sshKeyRepository.createDeviceKey(
                state.nameField,
                state.comment,
                state.fileName.takeIf { it.isNotBlank() },
                state.path,
                state.algorithm,
            )) {
                is CreateSshKeyResult.Success -> closeSidebar()
                is CreateSshKeyResult.Error -> errorMessage.update { result.message ?: "Failed to create device key" }
            }
            isSaveInFlight.update { false }
        }
    }

    private fun importKey() {
        val state = sidebarState.value as? SidebarState.ImportKeyCreating ?: return
        if (state.nameField.isBlank()) {
            errorMessage.update { "Name cannot be empty" }
            return
        }
        if (state.path.isBlank()) {
            errorMessage.update { "Path cannot be empty" }
            return
        }
        isSaveInFlight.update { true }
        viewModelScope.launch {
            when (val result = sshKeyRepository.importDeviceKey(
                state.nameField,
                state.path,
                state.passphrase.takeIf { it.isNotBlank() },
            )) {
                is CreateSshKeyResult.Success -> closeSidebar()
                is CreateSshKeyResult.Error -> errorMessage.update { result.message ?: "Failed to import key" }
            }
            isSaveInFlight.update { false }
        }
    }
}
