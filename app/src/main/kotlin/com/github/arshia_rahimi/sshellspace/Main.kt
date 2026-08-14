package com.github.arshia_rahimi.sshellspace

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arshia_rahimi.core.compose_utils.asString
import com.github.arshia_rahimi.sshellspace.ui.AppTheme
import com.github.arshia_rahimi.sshellspace.ui.TitleBar
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.WindowScaffold
import org.koin.compose.koinInject
import sshellspace.app.generated.resources.Res
import sshellspace.app.generated.resources.title

fun main() {
    initKoin()

    nucleusApplication(backend = NucleusBackend.Tao) {
        DecoratedWindow(
            onCloseRequest = ::exitApplication,
            title = Res.string.title.asString(),
        ) {
            AppTheme {
                WindowScaffold(
                    titleBar = { TitleBar() },
                ) { ip ->
                    val tabManager = koinInject<TabManager>()
                    val tabsState by tabManager.tabsState.collectAsStateWithLifecycle()
                    tabsState.currentTab.component.content(Modifier.fillMaxSize().padding(ip))
                }
            }
        }
    }
}
