package com.github.arshia_rahimi.sshellspace.feature.vault.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.github.arshia_rahimi.core.compose_utils.asString
import com.github.arshia_rahimi.core.ui.TabComponent
import sshellspace.feature.vault.impl.generated.resources.Res
import sshellspace.feature.vault.impl.generated.resources.vault

object VaultTab: TabComponent {
    override val id: String = "Vault"
    override val name: String = id
    override val icon: Painter?
        @Composable get() = null
    override val title: String
        @Composable get() = Res.string.vault.asString()

    @Composable
    override fun content(modifier: Modifier) = VaultTabContent(modifier)
}
