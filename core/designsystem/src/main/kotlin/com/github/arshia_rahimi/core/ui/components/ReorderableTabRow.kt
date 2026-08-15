package com.github.arshia_rahimi.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun <T, K : Any> ReorderableTabRow(
    fixedTabs: List<T>,
    tabs: List<T>,
    tabsOrder: List<K>,
    getKey: T.() -> K,
    selectedKey: K,
    onSelected: (K) -> Unit,
    onTabsReordered: (key: K, to: K) -> Unit,
    modifier: Modifier = Modifier,
    lazyRowModifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    tabModifier: Modifier = Modifier,
    separator: @Composable () -> Unit = {},
    trailingIcon: @Composable (Modifier) -> Unit = {},
    tab: @Composable (modifier: Modifier, item: T, isSelected: Boolean, isFixed: Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        val from = tabs.first { it.getKey() == from.key }.getKey()
        val to = tabs.first { it.getKey() == to.key }.getKey()
        onTabsReordered(from, to)
    }
    val canScroll by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 &&
                    (info.visibleItemsInfo.firstOrNull()?.index != 0 ||
                            info.visibleItemsInfo.lastOrNull()?.index != info.totalItemsCount - 1 ||
                            (info.visibleItemsInfo.firstOrNull()?.offset ?: 0) < info.beforeContentPadding ||
                            (info.visibleItemsInfo.lastOrNull()?.let { it.offset + it.size } ?: 0) >
                            info.viewportEndOffset - info.afterContentPadding)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        fixedTabs.forEach { item ->
            Box(
                modifier = tabModifier.clickable { onSelected(item.getKey()) }
            ) {
                tab(Modifier, item, item.getKey() == selectedKey, true)
            }
        }

        separator()

        LazyRow(
            state = listState,
            modifier = lazyRowModifier,
            reverseLayout = false,
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(
                items = tabsOrder,
                key = { it },
            ) { tabId ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = tabId,
                ) {
                    tab(
                        Modifier
                            .clickable { onSelected(tabId) }
                            .animateItem().draggableHandle(),
                        tabs.find { it.getKey() == tabId }!!,
                        tabId == selectedKey,
                        false,
                    )
                }

            }

            if (!canScroll) {
                item(key = "new-tab") {
                    trailingIcon(Modifier.animateItem())
                }
            }
        }

        if (canScroll) {
            trailingIcon(Modifier)
        }
    }
}
