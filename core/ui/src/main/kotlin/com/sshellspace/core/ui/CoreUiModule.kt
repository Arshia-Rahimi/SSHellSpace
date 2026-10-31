package com.sshellspace.core.ui

import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

val CoreUiModule = module {
    single<ShortcutHandler>()
}
