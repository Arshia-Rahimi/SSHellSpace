package com.github.arshia_rahimi.sshellspace.tabs

import com.github.arshia_rahimi.core.ui.TabComponent
import com.github.arshia_rahimi.sshellspace.Tab
import org.koin.core.annotation.Named

class VaultTab(
    @Named("Vault") override var component: TabComponent,
): Tab {
    override val id = "Vault"
}
