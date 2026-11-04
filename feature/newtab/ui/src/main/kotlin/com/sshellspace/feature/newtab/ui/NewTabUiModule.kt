package com.sshellspace.feature.newtab.ui

import com.sshellspace.feature.newtab.ui.screen.NewTabDialogViewModel
import com.sshellspace.feature.newtab.ui.screen.QuickConnectViewModel
import org.koin.dsl.module
import org.koin.plugin.module.dsl.viewModel

val NewTabUiModule = module {
    viewModel<NewTabDialogViewModel>()
    viewModel<QuickConnectViewModel>()
}
