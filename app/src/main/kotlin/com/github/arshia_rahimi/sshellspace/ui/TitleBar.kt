package com.github.arshia_rahimi.sshellspace.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.arshia_rahimi.core.ui.components.ReorderableTabRow
import com.github.arshia_rahimi.sshellspace.Tabs
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.noWindowDrag
import dev.nucleusframework.window.windowDragArea

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DecoratedWindowScope.TitleBar() {
    val tabs by Tabs.tabs.collectAsStateWithLifecycle(emptyList())
    val currentTabId by Tabs.currentTabId.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .windowDragArea()
            .padding(horizontal = 12.dp),
    ) {
        ReorderableTabRow(
            modifier = Modifier
                .padding(start = 40.dp)
                .fillMaxWidth(),
            fixedTabs = Tabs.fixedTabs,
            tabs = tabs,
            getKey = { id },
            onSelected = { Tabs.setCurrentTab(it) },
            selectedKey = currentTabId,
            tabModifier = Modifier.noWindowDrag(),
            onMove = { tabId, to -> Tabs.moveTab(tabId, to) },
        ) { tab, isSelected, isFixed ->
            Row {
                Text(tab.title)

                if (isSelected && !isFixed) {
                    Button(
                        onClick = { Tabs.closeTab(tab.id) },
                    ) {
                        Text("close" + if(isSelected) "selected" else "")
                    }
                }
            }
        }

        WindowControls()
    }
}
