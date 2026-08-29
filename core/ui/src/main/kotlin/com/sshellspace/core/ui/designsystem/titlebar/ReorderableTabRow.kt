package com.sshellspace.core.ui.designsystem.titlebar

import androidx.compose.animation.core.tween
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
fun <K : Any> ReorderableTabRow(
    tabKeys: List<K>,
    setTabKeys: (List<K>) -> Unit,
    fixedTabKeys: List<K>,
    selectedKey: K,
    onSelected: (K) -> Unit,
    gap: Dp = 0.dp,
    modifier: Modifier = Modifier,
    lazyRowModifier: Modifier = Modifier,
    tabModifier: Modifier = Modifier,
    separator: @Composable () -> Unit = {},
    trailingIcon: (@Composable (Modifier) -> Unit)? = null,
    tab: @Composable (modifier: Modifier, key: K, isSelected: Boolean, isFixed: Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = listState,
        scrollThreshold = 40.dp,
    ) { from, to ->
        val trailingIconIndexLength = if (trailingIcon != null) 1 else 0
        setTabKeys(
            tabKeys.toMutableList()
                .apply { add(to.index - trailingIconIndexLength, removeAt(from.index - trailingIconIndexLength)) }
        )
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        fixedTabKeys.forEach { key ->
            Box(
                modifier = tabModifier.clickable { onSelected(key) }
            ) {
                tab(Modifier, key, key == selectedKey, true)
            }
        }

        separator()

        LazyRow(
            state = listState,
            modifier = lazyRowModifier.weight(1f),
            reverseLayout = true,
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailingIcon?.let {
                stickyHeader(key = "new-tab") {
                    it(
                        Modifier.animateItem(
                            placementSpec = tween(),
                        )
                    )
                }
            }

            items(
                items = tabKeys.reversed(),
                key = { it },
            ) { key ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = key,
                ) { isDragging ->
                    tab(
                        Modifier
                            .clickable { onSelected(key) }
                            .draggableHandle()
                            .animateItem(
                                fadeInSpec = tween(),
                                fadeOutSpec = tween(),
                                placementSpec = if (isDragging) null else tween(),
                            ),
                        key,
                        key == selectedKey,
                        false,
                    )
                }

            }
        }
    }
}
