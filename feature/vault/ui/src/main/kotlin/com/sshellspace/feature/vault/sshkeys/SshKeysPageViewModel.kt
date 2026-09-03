package com.sshellspace.feature.vault.sshkeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.feature.vault.domain.SshKeyRepository
import com.sshellspace.feature.vault.toSshKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SshKeysPageViewModel(
    private val sshKeyRepository: SshKeyRepository,
): ViewModel() {
    private val sshKeys = sshKeyRepository.getKeys()
        .map { it.map { sshKey -> sshKey.toSshKey() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val uiState = combine(
        sshKeys,
    ) {
        SshKeysPageUiState(
            keys = it[0],
            selectedKeyState = SelectedKeyState.None,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SshKeysPageUiState())
}
