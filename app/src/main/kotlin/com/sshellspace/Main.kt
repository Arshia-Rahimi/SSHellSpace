package com.sshellspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.sshellspace.config.SaveMainWindow
import com.sshellspace.config.rememberMainWindowState
import com.sshellspace.config.savedWindow
import com.sshellspace.core.data.vault.VaultAutoLock
import com.sshellspace.core.domain.emit
import com.sshellspace.core.domain.models.AppUpdateState
import com.sshellspace.core.domain.models.DEFAULT_COLOR_SCHEME_ID
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.domain.repositories.AppUpdater
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.designsystem.adaptive.MinWindowSize
import com.sshellspace.core.ui.designsystem.adaptive.ProvideWindowClass
import com.sshellspace.core.ui.designsystem.common.asString
import com.sshellspace.core.ui.designsystem.dialog.AppDialog
import com.sshellspace.core.ui.designsystem.theme.AppColorScheme
import com.sshellspace.core.ui.designsystem.theme.SSHellSpaceTheme
import com.sshellspace.core.ui.designsystem.titlebar.LockedTitleBar
import com.sshellspace.core.ui.designsystem.titlebar.TitleBar
import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import com.sshellspace.core.ui.tabs.LocalTabActive
import com.sshellspace.core.ui.tabs.TabId
import com.sshellspace.core.ui.tabs.TabItem
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.newtab.ui.NewTabDialogEvent
import com.sshellspace.feature.vault.gate.VaultGateScreen
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.WindowScaffold
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import sshellspace.app.generated.resources.Res
import sshellspace.app.generated.resources.icon
import sshellspace.app.generated.resources.title

fun main() {
    System.setProperty("nucleus.tao.trackpadPanEvents", "false")
    
    val savedWindow = initKoin().savedWindow()

    nucleusApplication(backend = NucleusBackend.Tao) {
        val shortcutHandler = koinInject<ShortcutHandler>()
        val autoLock = koinInject<VaultAutoLock>()
        val appUpdater = koinInject<AppUpdater>()
        val windowState = rememberMainWindowState(savedWindow)
        SaveMainWindow(windowState, savedWindow, koinInject())

        DecoratedWindow(
            onCloseRequest = {
                appUpdater.installOnQuit()
                exitApplication()
            },
            state = windowState,
            title = Res.string.title.asString(),
            icon = painterResource(Res.drawable.icon),
            minimumSize = MinWindowSize,
            onPreviewKeyEvent = { event ->
                autoLock.recordActivity()
                shortcutHandler.onKeyEvent(event)
            },
        ) {
            val preferences = koinInject<CorePreferencesRepository>()
            val themePreference by remember { preferences.getTheme() }
                .collectAsStateWithLifecycle(ThemePreference.SYSTEM_DEFAULT)
            val colorSchemeId by remember { preferences.getColorScheme() }
                .collectAsStateWithLifecycle(DEFAULT_COLOR_SCHEME_ID)
            val userTheme by remember { preferences.getActiveUserTheme() }
                .collectAsStateWithLifecycle(null)
            val vaultManager = koinInject<VaultManager>()
            val activeVault by vaultManager.activeVault.collectAsStateWithLifecycle()
            val updateState by appUpdater.state.collectAsStateWithLifecycle()
            val unlocked = activeVault != null
            val scope = rememberCoroutineScope()

            LaunchedEffect(unlocked) { shortcutHandler.isPaused = !unlocked }

            SSHellSpaceTheme(
                themePreference = themePreference,
                colorScheme = AppColorScheme.fromId(colorSchemeId),
                userTheme = userTheme,
            ) {
                ProvideWindowClass(Modifier.fillMaxSize()) {
                    WindowScaffold(
                        titleBar = {
                            activeVault?.let { vault ->
                                TitleBar(
                                    vaultName = vault.name,
                                    onLockVault = { scope.launch { vaultManager.lock() } },
                                    onExit = {
                                        scope.launch {
                                            // Locking first closes sessions and forwards cleanly.
                                            try {
                                                vaultManager.lock()
                                            } finally {
                                                appUpdater.installOnQuit()
                                                exitApplication()
                                            }
                                        }
                                    },
                                    onCreateNewTab = NewTabDialogEvent::emit,
                                    readyUpdate = (updateState as? AppUpdateState.Ready)?.version,
                                    openSessionCount = appUpdater::openSessionCount,
                                    onRestartToUpdate = appUpdater::restartToInstall,
                                )
                            } ?: LockedTitleBar()
                        },
                        modifier = Modifier.fillMaxSize().pointerInput(autoLock) {
                            // The initial pass sees events even when the terminal goes on to consume them.
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial)
                                    autoLock.recordActivity()
                                }
                            }
                        },
                    ) { ip ->
                        if (unlocked) {
                            // A new owner per unlock: dialog ViewModels hold vault data, which mustn't outlive the lock.
                            key(activeVault?.id) {
                                val dialogOwner = remember {
                                    object : ViewModelStoreOwner {
                                        override val viewModelStore = ViewModelStore()
                                    }
                                }
                                DisposableEffect(dialogOwner) { onDispose { dialogOwner.viewModelStore.clear() } }
                                CompositionLocalProvider(LocalViewModelStoreOwner provides dialogOwner) {
                                    AppDialog()
                                }
                            }

                            TabHost(Modifier.fillMaxSize().padding(ip))
                        } else {
                            VaultGateScreen(Modifier.fillMaxSize().padding(ip))
                        }
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

    val current = tabsState.currentTab
    // The keep-alive tabs shown so far. Not state: it only changes along with tabsState, which recomposes this anyway.
    val shown = remember { mutableSetOf<TabId>() }
    if (current.keepAlive) shown += current.tab.id
    shown.retainAll(tabsState.tabsSequence.map { it.tab.id }.toSet())

    Box(modifier) {
        if (!current.keepAlive) {
            key(current.tab.id) {
                CompositionLocalProvider(LocalViewModelStoreOwner provides current.tab) {
                    current.content()
                }
            }
        }
        tabsState.tabs.filter { it.keepAlive && it.tab.id in shown }.forEach { item ->
            key(item.tab.id) {
                KeptAliveTab(item, active = item == current)
            }
        }
    }
}

/**
 * A tab that stays composed while hidden, so switching back doesn't rebuild it. Hidden, it's measured but not placed,
 * so it keeps its size without drawing or taking input, and it gives up the keyboard.
 */
@Composable
private fun KeptAliveTab(item: TabItem, active: Boolean) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var wasActive by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        when {
            !active && hasFocus -> focusManager.clearFocus(force = true)
            // Its content took focus when it first appeared, and won't again by itself.
            active && wasActive -> focusRequester.requestFocus()
        }
        if (active) wasActive = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) { if (active) placeable.place(0, 0) }
            }
            .focusRequester(focusRequester)
            .onFocusChanged { hasFocus = it.hasFocus },
    ) {
        CompositionLocalProvider(LocalViewModelStoreOwner provides item.tab, LocalTabActive provides active) {
            item.content()
        }
    }
}

