package com.github.arshia_rahimi.sshellspace.feature.newtab

import androidx.compose.runtime.Composable
import com.github.arshia_rahimi.sshellspace.core.tabs.TabViewModel

class NewTabViewModel: TabViewModel(
    initialTitle = "New Tab",
    initialIcon = null,
) {
    @Composable
    override fun screen() = NewTabScreen()
}