package com.github.arshia_rahimi.core.ui.designsystem.titlebar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arshia_rahimi.core.ui.common_compose.asString
import com.github.arshia_rahimi.core.ui.designsystem.Icons
import com.github.arshia_rahimi.core.ui.designsystem.components.IconButton
import com.github.arshia_rahimi.core.ui.designsystem.components.Text
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabId
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabManager
import com.github.arshia_rahimi.sshellspace.core.ui.tabs.TabViewModel
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.noWindowDrag
import dev.nucleusframework.window.windowDragArea
import org.koin.compose.koinInject
import sshellspace.core.ui.designsystem.generated.resources.Res
import sshellspace.core.ui.designsystem.generated.resources.close_tab
import sshellspace.core.ui.designsystem.generated.resources.new_tab

@Composable
fun DecoratedWindowScope.TitleBar() {
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
            fixedTabKeys = tabsState.fixedTab.map { it.id },
            gap = 4.dp,
            onSelected = { tabManager.setCurrentTab(it) },
            selectedKey = tabsState.currentTabId,
            tabModifier = Modifier.noWindowDrag(),
            separator = { VerticalDivider(Modifier.padding(vertical = 4.dp)) },
            tab = { modifier, tabId, isSelected, isFixed ->
                Tab(
                    tab = tabsState.getTab(tabId),
                    isSelected = isSelected,
                    isFixed = isFixed,
                    closeIcon = Icons.Cross,
                    closeIconDescription = Res.string.close_tab.asString(),
                    onCloseTab = {
                        tabManager.closeTab(tabId)
                        tabKeys -= tabId
                    },
                    modifier = modifier,
                )
            },
            trailingIcon = { modifier ->
                Box(
                    modifier = modifier.fillMaxHeight()
                        .background(color = MaterialTheme.colorScheme.background)
                ) {
                    IconButton(
                        onClick = {
                            val id = tabManager.createNewTab()
                            tabKeys = listOf(id) + tabKeys
                        },
                        imageVector = Icons.Plus,
                        contentDescription = Res.string.new_tab.asString(),
                        modifier = Modifier.clip(CircleShape),
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
        )
    }
}

@Composable
fun Tab(
    tab: TabViewModel,
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
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(tab.title)

        if (!isFixed) {
            IconButton(
                imageVector = closeIcon,
                onClick = onCloseTab,
                contentDescription = closeIconDescription,
            )
        }
    }
}
