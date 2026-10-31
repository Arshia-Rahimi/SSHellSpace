package com.sshellspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.sshellspace.core.domain.emit
import com.sshellspace.core.domain.models.DEFAULT_COLOR_SCHEME_ID
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.ui.designsystem.adaptive.DefaultWindowSize
import com.sshellspace.core.ui.designsystem.adaptive.MinWindowSize
import com.sshellspace.core.ui.designsystem.adaptive.ProvideWindowClass
import com.sshellspace.core.ui.designsystem.common.asString
import com.sshellspace.core.ui.designsystem.dialog.AppDialog
import com.sshellspace.core.ui.designsystem.theme.AppColorScheme
import com.sshellspace.core.ui.designsystem.theme.SSHellSpaceTheme
import com.sshellspace.core.ui.designsystem.titlebar.TitleBar
import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.newtab.ui.NewTabDialogEvent
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
        val shortcutHandler = koinInject<ShortcutHandler>()

        DecoratedWindow(
            onCloseRequest = ::exitApplication,
            state = rememberWindowState(size = DefaultWindowSize),
            title = Res.string.title.asString(),
            minimumSize = MinWindowSize,
            onPreviewKeyEvent = shortcutHandler::onKeyEvent,
        ) {
            val preferences = koinInject<CorePreferencesRepository>()
            val themePreference by remember { preferences.getTheme() }
                .collectAsStateWithLifecycle(ThemePreference.SYSTEM_DEFAULT)
            val colorSchemeId by remember { preferences.getColorScheme() }
                .collectAsStateWithLifecycle(DEFAULT_COLOR_SCHEME_ID)

            SSHellSpaceTheme(
                themePreference = themePreference,
                colorScheme = AppColorScheme.fromId(colorSchemeId),
            ) {
                ProvideWindowClass(Modifier.fillMaxSize()) {
                    WindowScaffold(
                        titleBar = { TitleBar(NewTabDialogEvent::emit) },
                        modifier = Modifier.fillMaxSize(),
                    ) { ip ->
                        AppDialog()

                        TabHost(Modifier.fillMaxSize().padding(ip))
                    }
                }
            }
        }
    }
}

@Composable
private fun TabHost(
    modifier: Modifier = Modifier,
) {
    val tabsState by koinInject<TabManager>()
        .tabsState()
        .collectAsStateWithLifecycle()

    Box(modifier) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides tabsState.currentTab.tab) {
            tabsState.currentTab.content()
        }

    }
}
