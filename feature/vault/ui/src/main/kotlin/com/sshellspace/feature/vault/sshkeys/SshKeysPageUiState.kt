package com.sshellspace.feature.vault.sshkeys

import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.SshKeyInfo
import com.sshellspace.core.domain.models.SshKeyType
import kotlin.uuid.Uuid

data class SshKeysPageUiState(
    val keys: List<SshKey> = emptyList(),
    val sidebarState: SidebarState = SidebarState.None,
    val isSaveInFlight: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface SidebarState {
    data object None : SidebarState

    data class Editing(
        val key: SshKey,
        val nameField: String,
        val nameFieldError: String?,
    ) : SidebarState

    data class VaultKeyCreating(
        val nameField: String = "",
        val nameFieldError: String? = null,
        val comment: String = "",
        val algorithm: Algorithm = Algorithm.Ed25519,
    ) : SidebarState

    data class DeviceKeyCreating(
        val nameField: String = "",
        val nameFieldError: String? = null,
        val comment: String = "",
        val fileName: String = "",
        val path: String = "",
        val algorithm: Algorithm = Algorithm.Ed25519,
    ) : SidebarState

    data class ImportKeyCreating(
        val nameField: String = "",
        val nameFieldError: String? = null,
        val path: String = "",
        val passphrase: String = "",
        val passphraseVisible: Boolean = false,
    ) : SidebarState
}

data class SshKey(
    val id: Uuid,
    val name: String,
    val algorithm: Algorithm,
    val type: SshKeyType,
)

fun SshKeyInfo.toSshKey() = SshKey(
    id = this.id,
    name = this.name,
    algorithm = this.algorithm,
    type = this.type,
)

enum class CreationMethod(val label: String) {
    CreateVault("Generate new key"),
    CreateDevice("Generate to file"),
    Import("Import existing key"),
}
