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
    onMove: (key: Int, to: Int) -> Unit,
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
        onMove(from.index - 1, to.index - 1)
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
            reverseLayout = true,
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stickyHeader(key = "new-tab") {
                trailingIcon(Modifier.animateItem())
            }

            items(
                items = tabsOrder.reversed(),
                key = { it },
            ) { tabId ->
                ReorderableItem(
                    modifier = tabModifier
                        .clickable { onSelected(tabId) },
                    state = reorderableLazyListState,
                    key = tabId,
                ) {
                    tab(Modifier.draggableHandle(), tabs.find { it.getKey() == tabId }!!, tabId == selectedKey, false)
                }

            }
        }
    }
}
