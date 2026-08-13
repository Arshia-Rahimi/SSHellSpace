package com.github.arshia_rahimi.sshellspace.feature.vault.impl

import androidx.compose.runtime.Composable
import com.github.arshia_rahimi.core.compose_utils.asString
import com.github.arshia_rahimi.core.ui.Tab
import sshellspace.feature.vault.impl.generated.resources.Res
import sshellspace.feature.vault.impl.generated.resources.vault

object VaultTab: Tab {
    override val id: String = "Vault"

    override val title: String
        @Composable get() = Res.string.vault.asString()

    @Composable
    override fun content() = VaultTabContent()
}
