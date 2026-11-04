package com.sshellspace.feature.vault.sshkeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.emit
import com.sshellspace.core.domain.models.SshKeyType
import com.sshellspace.feature.vault.sshkeys.components.DeleteSshKeyDialogEvent
import com.sshellspace.feature.vault.domain.CreateSshKeyResult
import com.sshellspace.feature.vault.domain.SshKeyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class SshKeysPageViewModel(
    private val sshKeyRepository: SshKeyRepository,
) : ViewModel() {
    private val sshKeys = sshKeyRepository.getKeys()
        .map { it.map { key -> key.toSshKey() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val sidebarState = MutableStateFlow<SidebarState>(SidebarState.None)
    private val isSaveInFlight = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")

    val uiState = combine(
        sshKeys,
        searchQuery,
        sidebarState,
        isSaveInFlight,
        errorMessage,
    ) { keys, query, sidebar, inFlight, error ->
        SshKeysPageUiState(
            keys = keys,
            searchQuery = query,
            sidebarState = sidebar,
            isSaveInFlight = inFlight,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SshKeysPageUiState())

    fun execute(action: SshKeysPageAction) {
        when (action) {
            is SshKeysPageAction.SelectKey -> selectKey(action.key)
            is SshKeysPageAction.SetSearch -> searchQuery.update { action.value }
            is SshKeysPageAction.OpenCreate -> openCreating(action.method)
            is SshKeysPageAction.SwitchMethod -> switchMethod(action.method)
            SshKeysPageAction.CloseSidebar -> closeSidebar()
            SshKeysPageAction.DismissError -> errorMessage.update { null }

            is SshKeysPageAction.Editing.SetName -> setEditingName(action.value)
            SshKeysPageAction.Editing.Save -> saveEditing()
            SshKeysPageAction.Editing.RequestDelete -> requestDelete()

            is SshKeysPageAction.VaultKey.SetName -> updateVaultCreating { copy(nameField = action.value) }
            is SshKeysPageAction.VaultKey.SetComment -> updateVaultCreating { copy(comment = action.value) }
            is SshKeysPageAction.VaultKey.SetAlgorithm -> updateVaultCreating { copy(algorithm = action.value) }
            SshKeysPageAction.VaultKey.Save -> saveVaultKey()

            is SshKeysPageAction.DeviceKey.SetName -> updateDeviceCreating { copy(nameField = action.value) }
            is SshKeysPageAction.DeviceKey.SetComment -> updateDeviceCreating { copy(comment = action.value) }
            is SshKeysPageAction.DeviceKey.SetFileName -> updateDeviceCreating { copy(fileName = action.value) }
            is SshKeysPageAction.DeviceKey.SetPath -> updateDeviceCreating { copy(path = action.value) }
            is SshKeysPageAction.DeviceKey.SetAlgorithm -> updateDeviceCreating { copy(algorithm = action.value) }
            is SshKeysPageAction.DeviceKey.SetPassphrase -> updateDeviceCreating { copy(passphrase = action.value) }
            is SshKeysPageAction.DeviceKey.TogglePassphraseVisibility -> updateDeviceCreating { copy(passphraseVisible = action.value) }
            SshKeysPageAction.DeviceKey.Save -> saveDeviceKey()

            is SshKeysPageAction.ImportKey.SetName -> updateImportCreating { copy(nameField = action.value) }
            is SshKeysPageAction.ImportKey.SetPath -> updateImportCreating { copy(path = action.value) }
            is SshKeysPageAction.ImportKey.SetPassphrase -> updateImportCreating { copy(passphrase = action.value) }
            is SshKeysPageAction.ImportKey.TogglePassphraseVisibility -> updateImportCreating { copy(passphraseVisible = action.value) }
            SshKeysPageAction.ImportKey.Save -> importKey()

            is SshKeysPageAction.PasteKey.SetName -> updatePasteCreating { copy(nameField = action.value) }
            is SshKeysPageAction.PasteKey.SetPrivateKey -> updatePasteCreating { copy(privateKey = action.value) }
            is SshKeysPageAction.PasteKey.SetPassphrase -> updatePasteCreating { copy(passphrase = action.value) }
            is SshKeysPageAction.PasteKey.TogglePassphraseVisibility -> updatePasteCreating { copy(passphraseVisible = action.value) }
            SshKeysPageAction.PasteKey.Save -> pasteKey()
        }
    }

    private fun nameError(name: String, excludeId: Uuid? = null): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return if (sshKeys.value.any { it.id != excludeId && it.name == trimmed }) "$trimmed already exists." else null
    }

    private fun selectKey(key: SshKey) {
        errorMessage.update { null }
        sidebarState.update { SidebarState.Editing(key, key.name, null) }
        viewModelScope.launch {
            val details = sshKeyRepository.getDetails(key.id)
            sidebarState.update { state ->
                if (state is SidebarState.Editing && state.key.id == key.id) {
                    state.copy(details = details, isLoadingDetails = false)
                } else {
                    state
                }
            }
        }
    }

    private fun openCreating(method: CreationMethod) {
        errorMessage.update { null }
        sidebarState.update { newCreating(method, "") }
    }

    private fun switchMethod(method: CreationMethod) {
        errorMessage.update { null }
        sidebarState.update { state ->
            val name = (state as? SidebarState.Creating)?.nameField ?: ""
            if ((state as? SidebarState.Creating)?.method == method) state else newCreating(method, name)
        }
    }

    private fun newCreating(method: CreationMethod, name: String): SidebarState.Creating {
        val error = nameError(name)
        return when (method) {
            CreationMethod.CreateVault -> SidebarState.VaultKeyCreating(nameField = name, nameFieldError = error)
            CreationMethod.CreateDevice -> SidebarState.DeviceKeyCreating(nameField = name, nameFieldError = error)
            CreationMethod.Import -> SidebarState.ImportKeyCreating(nameField = name, nameFieldError = error)
            CreationMethod.Paste -> SidebarState.PasteKeyCreating(nameField = name, nameFieldError = error)
        }
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
                nameFieldError = nameError(name, state.key.id),
            )
        }
    }

    private fun saveEditing() {
        val state = sidebarState.value as? SidebarState.Editing ?: return
        if (state.nameField.isBlank()) {
            errorMessage.update { "Name cannot be empty" }
            return
        }
        if (state.nameFieldError != null) return
        val name = state.nameField.trim()
        if (name == state.key.name) {
            closeSidebar()
            return
        }
        isSaveInFlight.update { true }
        viewModelScope.launch {
            sshKeyRepository.updateName(state.key.id, name)
            isSaveInFlight.update { false }
            closeSidebar()
        }
    }

    private fun requestDelete() {
        val state = sidebarState.value as? SidebarState.Editing ?: return
        val key = state.key
        DeleteSshKeyDialogEvent(
            keyName = key.name,
            isDeviceKey = key.type == SshKeyType.DEVICE,
            onConfirm = { deleteKey(key.id) },
        ).emit()
    }

    private fun deleteKey(id: Uuid) {
        isSaveInFlight.update { true }
        viewModelScope.launch {
            sshKeyRepository.delete(id)
            isSaveInFlight.update { false }
            if ((sidebarState.value as? SidebarState.Editing)?.key?.id == id) closeSidebar()
        }
    }

    private fun updateVaultCreating(transform: SidebarState.VaultKeyCreating.() -> SidebarState.VaultKeyCreating) {
        sidebarState.update { state ->
            if (state !is SidebarState.VaultKeyCreating) return@update state
            state.transform().let { it.copy(nameFieldError = nameError(it.nameField)) }
        }
    }

    private fun updateDeviceCreating(transform: SidebarState.DeviceKeyCreating.() -> SidebarState.DeviceKeyCreating) {
        sidebarState.update { state ->
            if (state !is SidebarState.DeviceKeyCreating) return@update state
            state.transform().let { it.copy(nameFieldError = nameError(it.nameField)) }
        }
    }

    private fun updateImportCreating(transform: SidebarState.ImportKeyCreating.() -> SidebarState.ImportKeyCreating) {
        sidebarState.update { state ->
            if (state !is SidebarState.ImportKeyCreating) return@update state
            state.transform().let { it.copy(nameFieldError = nameError(it.nameField)) }
        }
    }

    private fun updatePasteCreating(transform: SidebarState.PasteKeyCreating.() -> SidebarState.PasteKeyCreating) {
        sidebarState.update { state ->
            if (state !is SidebarState.PasteKeyCreating) return@update state
            state.transform().let { it.copy(nameFieldError = nameError(it.nameField)) }
        }
    }

    private fun saveVaultKey() {
        val state = sidebarState.value as? SidebarState.VaultKeyCreating ?: return
        if (state.nameField.isBlank()) {
            errorMessage.update { "Name cannot be empty" }
            return
        }
        if (state.nameFieldError != null) return
        isSaveInFlight.update { true }
        viewModelScope.launch {
            when (val result = sshKeyRepository.createVaultKey(state.nameField.trim(), state.comment, state.algorithm)) {
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
        if (state.nameFieldError != null) return
        isSaveInFlight.update { true }
        viewModelScope.launch {
            when (val result = sshKeyRepository.createDeviceKey(
                state.nameField.trim(),
                state.comment,
                state.fileName.takeIf { it.isNotBlank() },
                state.path,
                state.algorithm,
                state.passphrase.ifEmpty { null },
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
        if (state.nameFieldError != null) return
        if (state.path.isBlank()) {
            errorMessage.update { "Path cannot be empty" }
            return
        }
        isSaveInFlight.update { true }
        viewModelScope.launch {
            when (val result = sshKeyRepository.importDeviceKey(
                state.nameField.trim(),
                state.path,
                state.passphrase.takeIf { it.isNotBlank() },
            )) {
                is CreateSshKeyResult.Success -> closeSidebar()
                is CreateSshKeyResult.Error -> errorMessage.update { result.message ?: "Failed to import key" }
            }
            isSaveInFlight.update { false }
        }
    }

    private fun pasteKey() {
        val state = sidebarState.value as? SidebarState.PasteKeyCreating ?: return
        if (state.nameField.isBlank()) {
            errorMessage.update { "Name cannot be empty" }
            return
        }
        if (state.nameFieldError != null) return
        if (state.privateKey.isBlank()) {
            errorMessage.update { "Private key cannot be empty" }
            return
        }
        isSaveInFlight.update { true }
        viewModelScope.launch {
            when (val result = sshKeyRepository.importVaultKey(
                state.nameField.trim(),
                state.privateKey,
                state.passphrase.takeIf { it.isNotBlank() },
            )) {
                is CreateSshKeyResult.Success -> closeSidebar()
                is CreateSshKeyResult.Error -> errorMessage.update { result.message ?: "Failed to import key" }
            }
            isSaveInFlight.update { false }
        }
    }
}
