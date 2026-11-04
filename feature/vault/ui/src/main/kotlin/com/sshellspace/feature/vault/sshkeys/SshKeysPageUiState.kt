package com.sshellspace.feature.vault.sshkeys

import com.sshellspace.core.domain.models.Algorithm
import com.sshellspace.core.domain.models.SshKeyInfo
import com.sshellspace.core.domain.models.SshKeyType
import com.sshellspace.feature.vault.domain.SshKeyDetails
import java.io.File
import kotlin.uuid.Uuid

data class SshKeysPageUiState(
    val keys: List<SshKey> = emptyList(),
    val searchQuery: String = "",
    val sidebarState: SidebarState = SidebarState.None,
    val isSaveInFlight: Boolean = false,
    val errorMessage: String? = null,
) {
    val filteredKeys: List<SshKey>
        get() = if (searchQuery.isBlank()) keys else keys.filter {
            it.name.contains(searchQuery.trim(), ignoreCase = true) ||
                it.algorithm.name.contains(searchQuery.trim(), ignoreCase = true)
        }
}

sealed interface SidebarState {
    data object None : SidebarState

    sealed interface Creating : SidebarState {
        val method: CreationMethod
        val nameField: String
        val nameFieldError: String?
    }

    data class Editing(
        val key: SshKey,
        val nameField: String,
        val nameFieldError: String?,
        val details: SshKeyDetails? = null,
        val isLoadingDetails: Boolean = true,
    ) : SidebarState

    data class VaultKeyCreating(
        override val nameField: String = "",
        override val nameFieldError: String? = null,
        val comment: String = "",
        val algorithm: Algorithm = Algorithm.Ed25519,
    ) : Creating {
        override val method = CreationMethod.CreateVault
    }

    data class DeviceKeyCreating(
        override val nameField: String = "",
        override val nameFieldError: String? = null,
        val comment: String = "",
        val fileName: String = "",
        val path: String = DEFAULT_SSH_DIR,
        val algorithm: Algorithm = Algorithm.Ed25519,
        val passphrase: String = "",
        val passphraseVisible: Boolean = false,
    ) : Creating {
        override val method = CreationMethod.CreateDevice
    }

    data class ImportKeyCreating(
        override val nameField: String = "",
        override val nameFieldError: String? = null,
        val path: String = "",
        val passphrase: String = "",
        val passphraseVisible: Boolean = false,
    ) : Creating {
        override val method = CreationMethod.Import
    }

    data class PasteKeyCreating(
        override val nameField: String = "",
        override val nameFieldError: String? = null,
        val privateKey: String = "",
        val passphrase: String = "",
        val passphraseVisible: Boolean = false,
    ) : Creating {
        override val method = CreationMethod.Paste
    }
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
    Import("Import from file"),
    Paste("Paste key"),
}

val DEFAULT_SSH_DIR: String = File(System.getProperty("user.home"), ".ssh").path
