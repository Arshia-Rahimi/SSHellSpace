package com.sshellspace.feature.vault

import com.sshellspace.feature.vault.connections.ConnectionsPageViewModel
import com.sshellspace.feature.vault.gate.VaultGateViewModel
import com.sshellspace.feature.vault.portforwarding.PortForwardingPageViewModel
import com.sshellspace.feature.vault.settings.SettingsPageViewModel
import com.sshellspace.feature.vault.snippets.SnippetsPageViewModel
import com.sshellspace.feature.vault.sshkeys.SshKeysPageViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

val VaultUiModule = module {
    single<VaultTabViewModel>()
    viewModel<ConnectionsPageViewModel>()
    viewModel<SshKeysPageViewModel>()
    viewModel<PortForwardingPageViewModel>()
    viewModel<SnippetsPageViewModel>()
    viewModel<SettingsPageViewModel>()
    viewModel<VaultGateViewModel>()
}
