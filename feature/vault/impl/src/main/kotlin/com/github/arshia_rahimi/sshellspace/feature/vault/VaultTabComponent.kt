package com.github.arshia_rahimi.sshellspace.feature.vault

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.github.arshia_rahimi.core.compose_utils.asString
import com.github.arshia_rahimi.core.ui.TabComponent
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.Named
import sshellspace.feature.vault.impl.generated.resources.Res
import sshellspace.feature.vault.impl.generated.resources.vault

class VaultTabComponent: TabComponent {
    override val title = MutableStateFlow(Res.string.vault)
    override val icon = MutableStateFlow(null)

    @Composable
    override fun content(modifier: Modifier) = VaultTabContent(modifier)
}
