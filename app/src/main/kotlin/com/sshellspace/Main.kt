package com.sshellspace

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sshellspace.core.ui.common_compose.asString
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.core.ui.designsystem.theme.SSHellSpaceTheme
import com.sshellspace.core.ui.designsystem.titlebar.TitleBar
import com.sshellspace.newtab.NewTabController
import com.sshellspace.newtab.NewTabDialog
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
            SSHellSpaceTheme {
                WindowScaffold(
                    titleBar = { TitleBar(NewTabController::emitNewTabButtonEvent) },
                    modifier = Modifier.fillMaxSize(),
                ) { ip ->
                    NewTabDialog()

                    val tabsState by koinInject<TabController>()
                        .tabsState()
                        .collectAsStateWithLifecycle()

                    AnimatedContent(
                        targetState = tabsState.currentTab,
                        modifier = Modifier.fillMaxSize()
                            .padding(ip),
                    ) {
                        it.screen()
                    }
                }
            }
        }
    }
}
