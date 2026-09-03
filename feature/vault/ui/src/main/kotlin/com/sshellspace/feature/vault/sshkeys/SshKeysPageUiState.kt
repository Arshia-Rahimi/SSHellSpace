package com.sshellspace.feature.vault.sshkeys

import com.sshellspace.feature.vault.SshKey

data class SshKeysPageUiState(
    val keys: List<SshKey> = emptyList(),
    val selectedKeyState: SelectedKeyState = SelectedKeyState.None,
)

sealed interface SelectedKeyState {
    data object None : SelectedKeyState
    data class Selected(val key: SshKey) : SelectedKeyState
}
