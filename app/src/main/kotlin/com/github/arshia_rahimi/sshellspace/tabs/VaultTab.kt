package com.github.arshia_rahimi.sshellspace.tabs

import com.github.arshia_rahimi.sshellspace.Tab
import com.github.arshia_rahimi.sshellspace.feature.vault.VaultTabComponent

class VaultTab(
    override var component: VaultTabComponent,
): Tab<VaultTabComponent> {
    override val id = "Vault"
}
