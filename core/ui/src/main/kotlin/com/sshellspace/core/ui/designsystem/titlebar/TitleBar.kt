package com.sshellspace.core.ui.designsystem.titlebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.theme.background
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onBackground
import com.sshellspace.core.ui.shortcuts.AppShortcut
import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import com.sshellspace.core.ui.shortcuts.label
import com.sshellspace.core.ui.tabs.TabManager
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.noWindowDrag
import dev.nucleusframework.window.windowDragArea
import org.koin.compose.koinInject

internal val TitleBarHeight = 42.dp
private const val STRIP_TINT = 0.07f

/**
 * Behind the tabs. Mixed from the page's colors rather than a token, so that the selected tab, which has the page's
 * color, stands out from it in every scheme and imported theme.
 */
@Composable
internal fun tabStripColor(): Color = lerp(Theme[colors][background], Theme[colors][onBackground], STRIP_TINT)

@Composable
fun DecoratedWindowScope.TitleBar(
    vaultName: String,
    onLockVault: () -> Unit,
    onExit: () -> Unit,
    onCreateNewTab: () -> Unit,
) {
    val tabManager = koinInject<TabManager>()
    val shortcuts = koinInject<ShortcutHandler>()
    val tabsState by tabManager.tabsState().collectAsStateWithLifecycle()
    val bindings by shortcuts.bindings.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TitleBarHeight)
            .background(tabStripColor())
            .windowDragArea(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WindowControls()

        val tabsArea = Modifier.fillMaxHeight().padding(top = TabStripTopPadding)
        tabsState.fixedTab.forEach { fixed ->
            val id = fixed.tab.id
            PinnedTab(
                // The fixed tab is the unlocked vault itself, so it goes by that vault's name.
                title = vaultName,
                icon = Icons.Vault,
                isSelected = id == tabsState.currentTabId,
                showDivider = id != tabsState.currentTabId && tabsState.tabs.firstOrNull()?.tab?.id != tabsState.currentTabId,
                onSelect = { tabManager.setCurrentTab(id) },
                modifier = tabsArea.noWindowDrag(),
                trailing = {
                    VaultMenu(
                        vaultName = vaultName,
                        lockShortcut = bindings[AppShortcut.LockVault]?.label,
                        onLock = onLockVault,
                        onExit = onExit,
                    )
                },
            )
        }

        TabStrip(
            tabs = tabsState.tabs,
            selectedId = tabsState.currentTabId,
            onSelect = tabManager::setCurrentTab,
            onClose = tabManager::closeTab,
            onMove = tabManager::moveTab,
            onNewTab = onCreateNewTab,
            // Keeps a space to drag the window by, even with so many tabs that they fill the strip.
            modifier = Modifier.weight(1f).fillMaxHeight().padding(end = WindowDragGap),
            interactiveModifier = Modifier.noWindowDrag(),
        )
    }
}

private val WindowDragGap = 48.dp
