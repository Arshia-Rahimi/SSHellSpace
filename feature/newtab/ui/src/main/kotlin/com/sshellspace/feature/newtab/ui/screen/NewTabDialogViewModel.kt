package com.sshellspace.feature.newtab.ui.screen

import androidx.lifecycle.ViewModel
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.terminal.domain.models.Shell
import com.sshellspace.feature.terminal.domain.models.TerminalParams

class NewTabDialogViewModel(
    private val tabManager: TabManager,
) : ViewModel() {
    fun createNewTab() {
        tabManager.createNewTab(
            TerminalParams(Shell("zsh", "/bin/zsh"))
        )
    }
}
