package com.sshellspace.feature.vault.settings

import com.sshellspace.core.domain.models.DEFAULT_AUTO_LOCK_MINUTES

/** Settings of the unlocked vault alone, stored inside it. */
data class VaultSettingsUiState(
    val vaultName: String? = null,
    val vaultHasPassword: Boolean = true,
    val vaultForm: VaultForm = VaultForm(),
    val autoLockMinutes: Int = DEFAULT_AUTO_LOCK_MINUTES,
)

/** A vault action that needs more than one click, open in the Vault section. */
enum class VaultEdit { ChangePassword, Export, Delete }

data class VaultForm(
    val edit: VaultEdit? = null,
    val isBusy: Boolean = false,
    val error: String? = null,
    /** Confirms something that finished, such as an export. */
    val notice: String? = null,
)
