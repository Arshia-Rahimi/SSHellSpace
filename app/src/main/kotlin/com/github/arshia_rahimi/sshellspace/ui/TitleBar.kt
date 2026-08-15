package com.github.arshia_rahimi.sshellspace.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.github.arshia_rahimi.sshellspace.TabManager
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
            gap = 4.dp,
            fixedTabs = tabsState.fixedTabs,
            tabs = tabsState.tabs,
            tabsOrder = tabsState.tabsOrder,
            getKey = { id },
            onSelected = { println(it); tabManager.setCurrentTab(it) },
            selectedKey = tabsState.currentTabId,
            tabModifier = Modifier.noWindowDrag(),
            onTabsReordered = { from, to -> tabManager.moveTab(from, to) },
            separator = {
                VerticalDivider(
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            },
            tab = { modifier, tab, isSelected, isFixed ->
                val title by tab.component.title.collectAsStateWithLifecycle()
                Tab(
                    title = title.asString(),
                    isSelected = isSelected,
                    isFixed = isFixed,
                    closeIcon = Icons.Cross,
                    closeIconDescription = Res.string.close_tab.asString(),
                    onCloseTab = { tabManager.closeTab(tab.id) },
                    modifier = modifier,
                )
            },
            trailingIcon = { modifier ->
                IconButton(
                    onClick = { tabManager.createNewTab() },
                    imageVector = Icons.Plus,
                    contentDescription = Res.string.new_tab.asString(),
                    modifier = modifier.clip(CircleShape),
                    contentColor = MaterialTheme.colorScheme.onBackground,
                )
            },
        )
    }
}

@Composable
fun Tab(
    modifier: Modifier,
    title: String,
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
        Text(title)

        if (!isFixed) {
            IconButton(
                imageVector = closeIcon,
                onClick = onCloseTab,
                contentDescription = closeIconDescription,
            )
        }
    }
}
