package com.sshellspace.core.ui.designsystem.titlebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledVerticalSeparator
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.common.asString
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textPrimary
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.core.ui.tabs.TabId
import com.sshellspace.core.ui.tabs.TabManager
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.noWindowDrag
import dev.nucleusframework.window.windowDragArea
import org.koin.compose.koinInject
import sshellspace.core.ui.generated.resources.Res
import sshellspace.core.ui.generated.resources.close_tab
import sshellspace.core.ui.generated.resources.new_tab

@Composable
fun DecoratedWindowScope.TitleBar(
    vaultName: String,
    onLockVault: () -> Unit,
    onExit: () -> Unit,
    onCreateNewTab: () -> Unit,
) {
    val tabManager = koinInject<TabManager>()
    val tabsState by tabManager.tabsState().collectAsStateWithLifecycle()
    var tabKeys by remember { mutableStateOf(emptyList<TabId>()) }

    // Keeps the user's drag order while picking up tabs opened or closed anywhere else.
    LaunchedEffect(tabsState.tabs) {
        val open = tabsState.tabs.map { it.tab.id }
        tabKeys = tabKeys.filter { it in open } + open.filter { it !in tabKeys }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .windowDragArea(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            space = 8.dp,
            alignment = Alignment.Start,
        ),
    ) {
        WindowControls()

        ReorderableTabRow(
            tabKeys = tabKeys,
            setTabKeys = { tabKeys = it },
            fixedTabKeys = tabsState.fixedTab.map { it.tab.id },
            gap = 4.dp,
            onSelected = { tabManager.setCurrentTab(it) },
            selectedKey = tabsState.currentTabId,
            tabModifier = Modifier.noWindowDrag(),
            separator = { UnstyledVerticalSeparator(Theme[colors][separator], Modifier.padding(vertical = 4.dp)) },
            tab = { modifier, tabId, isSelected, isFixed ->
                val tab = tabsState.getTab(tabId) ?: return@ReorderableTabRow
                Tab(
                    // The fixed tab is the unlocked vault itself, so it goes by that vault's name.
                    title = if (isFixed) vaultName else tab.title,
                    isSelected = isSelected,
                    modifier = modifier,
                    trailing = {
                        if (isFixed) {
                            VaultMenu(vaultName, onLock = onLockVault, onExit = onExit)
                        } else {
                            IconButton(
                                onClick = {
                                    tabKeys -= tabId
                                    tabManager.closeTab(tabId)
                                },
                                contentDescription = Res.string.close_tab.asString(),
                                imageVector = Icons.Cross,
                            )
                        }
                    },
                )
            },
            trailingIcon = { modifier ->
                Box(
                    modifier = modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        imageVector = Icons.Plus,
                        contentDescription = Res.string.new_tab.asString(),
                        onClick = onCreateNewTab,
                        modifier = Modifier.clip(CircleShape),
                    )
                }
            },
        )
    }
}

@Composable
fun Tab(
    title: String,
    modifier: Modifier,
    isSelected: Boolean,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Theme[colors][surfaceElevated] else Color.Transparent)
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            modifier = Modifier.widthIn(max = TabTitleMaxWidth),
            color = Theme[colors][if (isSelected) textPrimary else textSecondary],
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

private val TabTitleMaxWidth = 180.dp
