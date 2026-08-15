package com.github.arshia_rahimi.sshellspace.feature.newtab

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.github.arshia_rahimi.core.ui.TabComponent
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.annotation.Named
import sshellspace.feature.newtab.impl.generated.resources.Res
import sshellspace.feature.newtab.impl.generated.resources.new_tab

class NewTabComponent: TabComponent {
    override val title = MutableStateFlow(Res.string.new_tab)
    override val icon = MutableStateFlow(null)

    @Composable
    override fun content(modifier: Modifier) = NewTabContent(modifier)
}
