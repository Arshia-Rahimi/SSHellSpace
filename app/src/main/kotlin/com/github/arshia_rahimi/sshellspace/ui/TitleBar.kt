package com.github.arshia_rahimi.sshellspace.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.github.arshia_rahimi.core.compose_utils.asString
import com.github.arshia_rahimi.core.ui.Icons
import com.github.arshia_rahimi.core.ui.components.IconButton
import com.github.arshia_rahimi.core.ui.components.ReorderableTabRow
import com.github.arshia_rahimi.core.ui.components.Text
import com.github.arshia_rahimi.sshellspace.core.tabs.TabId
import com.github.arshia_rahimi.sshellspace.core.tabs.TabManager
import com.github.arshia_rahimi.sshellspace.core.tabs.TabViewModel
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.noWindowDrag
import dev.nucleusframework.window.windowDragArea
import org.koin.compose.koinInject
import sshellspace.app.generated.resources.Res
import sshellspace.app.generated.resources.close_tab
import sshellspace.app.generated.resources.new_tab

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DecoratedWindowScope.TitleBar() {
    val tabManager = koinInject<TabManager>()
    val tabsState by tabManager.tabsState.collectAsStateWithLifecycle()
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
            fixedTabKeys = tabsState.fixedTabs.map { it.id },
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
