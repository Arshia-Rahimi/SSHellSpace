package com.github.arshia_rahimi.sshellspace.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arshia_rahimi.core.ui.components.ReorderableTabRow
import com.github.arshia_rahimi.sshellspace.Tabs
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.TitleBar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DecoratedWindowScope.AppTitleBar() {
    val tabs by Tabs.tabs.collectAsStateWithLifecycle(emptyList())
    val currentTabId by Tabs.currentTabId.collectAsStateWithLifecycle()

    TitleBar {
        ReorderableTabRow(
            modifier = Modifier.fillMaxWidth(),
            fixedTabs = Tabs.fixedTabs,
            tabs = tabs,
            getKey = { id },
            onSelected = { Tabs.setCurrentTab(it) },
            selectedKey = currentTabId,
            onMove = { tabId, to -> Tabs.moveTab(tabId, to) },
        ) { tab, isSelected, isFixed ->
            Row {
                Text(tab.title + isSelected)

                if (isSelected && !isFixed) {
                    Button(
                        onClick = { Tabs.closeTab(tab.id) },
                    ) {
                        Text("close")
                    }
                }
            }
        }
    }
}
