package com.github.arshia_rahimi.sshellspace.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arshia_rahimi.core.compose_utils.asString
import com.github.arshia_rahimi.core.ui.Icons
import com.github.arshia_rahimi.core.ui.components.ReorderableTabRow
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
            .windowDragArea()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        WindowControls()

        ReorderableTabRow(
            fixedTabs = tabsState.fixedTabs,
            tabs = tabsState.tabs,
            getKey = { id },
            onSelected = { tabManager.setCurrentTab(it) },
            selectedKey = tabsState.currentTabId,
            tabModifier = Modifier.noWindowDrag(),
            onMove = { from, to -> tabManager.moveTab(from, to) },
        ) { tab, isSelected, isFixed ->
            Row {
                val title by tab.component.title.collectAsStateWithLifecycle()
                Text(title.asString())

                if (isSelected && !isFixed) {
                    IconButton(
                        onClick = { tabManager.closeTab(tab.id) },
                    ) {
                        Icon(Icons.Cross, Res.string.close_tab.asString())
                    }
                }
            }
        }

        IconButton(
            onClick = { tabManager.createNewTab() },
        ) {
            Icon(Icons.Plus, Res.string.new_tab.asString())
        }
    }
}
