package com.sshellspace.core.ui.designsystem.titlebar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composeunstyled.Text
import com.composeunstyled.UnstyledVerticalSeparator
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.common.asString
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.tabs.TabId
import com.sshellspace.core.ui.tabs.TabItem
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
    onCreateNewTab: () -> Unit,
) {
    val tabManager = koinInject<TabManager>()
    val tabsState by tabManager.tabsState().collectAsStateWithLifecycle()
    var tabKeys by remember { mutableStateOf(emptyList<TabId>()) }

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
            separator = { UnstyledVerticalSeparator(Color.Black, Modifier.padding(vertical = 4.dp)) },
            tab = { modifier, tabId, isSelected, isFixed ->
                Tab(
                    tab = tabsState.getTab(tabId) ?: return@ReorderableTabRow,
                    isSelected = isSelected,
                    isFixed = isFixed,
                    closeIcon = Icons.Cross,
                    closeIconDescription = Res.string.close_tab.asString(),
                    onCloseTab = {
                        tabKeys -= tabId
                        tabManager.closeTab(tabId)
                    },
                    modifier = modifier,
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
    tab: TabItem,
    modifier: Modifier,
    isSelected: Boolean,
    isFixed: Boolean,
    closeIcon: ImageVector,
    closeIconDescription: String?,
    onCloseTab: () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(tab.title)

        if (!isFixed) {
            IconButton(
                onClick = onCloseTab,
                contentDescription = closeIconDescription,
                imageVector = closeIcon,
            )
        }
    }
}
