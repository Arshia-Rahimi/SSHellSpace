package com.sshellspace

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sshellspace.core.domain.emit
import com.sshellspace.core.ui.designsystem.common.asString
import com.sshellspace.core.ui.designsystem.dialog.AppDialog
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.core.ui.designsystem.theme.SSHellSpaceTheme
import com.sshellspace.core.ui.designsystem.titlebar.TitleBar
import com.sshellspace.events.NewTabButtonEvent
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
                    titleBar = { TitleBar(NewTabButtonEvent::emit) },
                    modifier = Modifier.fillMaxSize(),
                ) { ip ->
                    AppDialog()

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
