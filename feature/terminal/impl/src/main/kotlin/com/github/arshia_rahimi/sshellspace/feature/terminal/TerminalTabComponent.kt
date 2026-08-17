package com.github.arshia_rahimi.sshellspace.feature.terminal

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.arshia_rahimi.core.ui.TabComponent
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.annotation.Named
import sshellspace.feature.terminal.impl.generated.resources.Res
import sshellspace.feature.terminal.impl.generated.resources.terminal

class TerminalTabComponent: TabComponent {
    override val title = MutableStateFlow(Res.string.terminal)
    override val icon = MutableStateFlow(null)

    @Composable
    override fun content(modifier: Modifier) = TerminalTabContent(modifier)
}
