package com.sshellspace.feature.vault.knownhosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.models.KnownHost
import com.sshellspace.feature.vault.domain.DeviceKnownHosts
import com.sshellspace.feature.vault.domain.KnownHostExportResult
import com.sshellspace.feature.vault.domain.KnownHostImportResult
import com.sshellspace.feature.vault.domain.KnownHostRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KnownHostsNotice(val message: String, val isError: Boolean = false)

data class KnownHostsPageUiState(
    val vaultHosts: List<KnownHost> = emptyList(),
    val device: DeviceKnownHosts = DeviceKnownHosts(path = ""),
    val searchQuery: String = "",
    val notice: KnownHostsNotice? = null,
) {
    val filteredVaultHosts: List<KnownHost> get() = vaultHosts.filter(::matchesSearch)
    val filteredDeviceHosts: List<KnownHost> get() = device.hosts.filter(::matchesSearch)

    private fun matchesSearch(host: KnownHost): Boolean {
        val query = searchQuery.trim()
        return host.hosts.any { it.contains(query, ignoreCase = true) } ||
            host.fingerprint.contains(query, ignoreCase = true) ||
            host.keyType.contains(query, ignoreCase = true)
    }
}

sealed interface KnownHostsPageAction {
    data class SetSearch(val value: String) : KnownHostsPageAction
    data object RefreshDevice : KnownHostsPageAction
    data class Remove(val host: KnownHost) : KnownHostsPageAction
    data class Import(val pickSource: suspend () -> String?) : KnownHostsPageAction
    data class Export(val pickDestination: suspend () -> String?) : KnownHostsPageAction
    data object DismissNotice : KnownHostsPageAction
}

class KnownHostsPageViewModel(
    private val repository: KnownHostRepository,
) : ViewModel() {
    private val device = MutableStateFlow(DeviceKnownHosts(path = ""))
    private val searchQuery = MutableStateFlow("")
    private val notice = MutableStateFlow<KnownHostsNotice?>(null)

    val uiState = combine(repository.getVaultHosts(), device, searchQuery, notice, ::KnownHostsPageUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), KnownHostsPageUiState())

    fun execute(action: KnownHostsPageAction) {
        when (action) {
            is KnownHostsPageAction.SetSearch -> searchQuery.update { action.value }
            KnownHostsPageAction.RefreshDevice -> viewModelScope.launch { device.value = repository.getDeviceHosts() }
            is KnownHostsPageAction.Remove -> viewModelScope.launch { repository.removeFromVault(action.host) }
            is KnownHostsPageAction.Import -> import(action.pickSource)
            is KnownHostsPageAction.Export -> export(action.pickDestination)
            KnownHostsPageAction.DismissNotice -> notice.update { null }
        }
    }

    private fun import(pickSource: suspend () -> String?) {
        viewModelScope.launch {
            val path = pickSource() ?: return@launch
            notice.value = when (val result = repository.importToVault(path)) {
                is KnownHostImportResult.Success -> KnownHostsNotice(importSummary(result))
                is KnownHostImportResult.Error -> KnownHostsNotice(result.message, isError = true)
            }
        }
    }

    private fun export(pickDestination: suspend () -> String?) {
        viewModelScope.launch {
            val path = pickDestination() ?: return@launch
            notice.value = when (val result = repository.exportVault(path)) {
                is KnownHostExportResult.Success ->
                    KnownHostsNotice("Exported ${hosts(result.count)}. Host names aren't encrypted in that file.")
                is KnownHostExportResult.Error -> KnownHostsNotice(result.message, isError = true)
            }
        }
    }
}

internal fun importSummary(result: KnownHostImportResult.Success): String = listOfNotNull(
    "Added ${hosts(result.added)}.",
    "${hosts(result.alreadyKnown)} already here.".takeIf { result.alreadyKnown > 0 },
    "Skipped ${hosts(result.conflicting)} with a different key from the one trusted here. Remove that one first to replace it."
        .takeIf { result.conflicting > 0 },
    "Skipped ${hosts(result.notAllowed)} using a wildcard or certificate authority, which could trust a key for many servers."
        .takeIf { result.notAllowed > 0 },
    "Skipped ${result.unreadable} unreadable ${if (result.unreadable == 1) "line" else "lines"}.".takeIf { result.unreadable > 0 },
).joinToString(" ")

private fun hosts(count: Int) = if (count == 1) "1 host" else "$count hosts"
