package com.github.arshia_rahimi.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

@Composable
fun <T, K : Any> ReorderableTabRow(
    fixedTabs: List<T>,
    tabs: List<T>,
    getKey: T.() -> K,
    selectedKey: K,
    onSelected: (K) -> Unit,
    onMove: (key: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    tabModifier: Modifier = Modifier,
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal =
        if (!reverseLayout) Arrangement.Start else Arrangement.End,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    separator: @Composable () -> Unit = {},
    tab: @Composable (item: T, isSelected: Boolean, isFixed: Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        reverseLayout = reverseLayout,
    ) {
        items(
            items = fixedTabs,
            key = { it.getKey() },
        ) { item ->
            Box(
                modifier = tabModifier.clickable { onSelected(item.getKey()) }
            ) {
                tab(item, item.getKey() == selectedKey, true)
            }
        }

        item {
            separator()
        }

        itemsIndexed(
            items = tabs,
            key = { _, item -> item.getKey() }
        ) { index, item ->
            val isDragging = draggingIndex == index

            Box(
                modifier = tabModifier
                    .then(
                        if (isDragging) {
                            Modifier.graphicsLayer {
                                translationX = dragOffset
                                alpha = 0.8f
                            }
                        } else {
                            Modifier
                        }
                    )
                    .animateItem()
                    .clickable { onSelected(item.getKey()) }
                    .pointerInput(tabs) {
                        detectDragGestures(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                            },

                            onDragEnd = {
                                draggingIndex = null
                                dragOffset = 0f
                            },

                            onDragCancel = {
                                draggingIndex = null
                                dragOffset = 0f
                            },

                            onDrag = { change, dragAmount ->
                                change.consume()

                                val currentIndex = draggingIndex
                                    ?: return@detectDragGestures

                                dragOffset += dragAmount.x

                                val visibleItems =
                                    listState.layoutInfo.visibleItemsInfo

                                val currentInfo =
                                    visibleItems.firstOrNull {
                                        it.index == currentIndex
                                    } ?: return@detectDragGestures

                                val draggedCenter =
                                    currentInfo.offset +
                                            currentInfo.size / 2 +
                                            dragOffset

                                // Find an item whose center we crossed.
                                val target = visibleItems
                                    .firstOrNull { info ->
                                        if (info.index == currentIndex)
                                            return@firstOrNull false

                                        val center =
                                            info.offset + info.size / 2

                                        if (dragOffset > 0) {
                                            draggedCenter > center &&
                                                    info.index > currentIndex
                                        } else {
                                            draggedCenter < center &&
                                                    info.index < currentIndex
                                        }
                                    }

                                if (target != null) {
                                    val to = target.index

                                    onMove(currentIndex, to)

                                    draggingIndex = to

                                    // Reset relative movement after swap.
                                    dragOffset -=
                                        target.offset - currentInfo.offset
                                }

                                // Edge scrolling.
                                val viewportStart =
                                    listState.layoutInfo.viewportStartOffset

                                val viewportEnd =
                                    listState.layoutInfo.viewportEndOffset

                                val edgeDistance = 80

                                if (
                                    draggedCenter <
                                    viewportStart + edgeDistance
                                ) {
                                    scope.launch {
                                        listState.scrollBy(-12f)
                                    }
                                } else if (
                                    draggedCenter >
                                    viewportEnd - edgeDistance
                                ) {
                                    scope.launch {
                                        listState.scrollBy(12f)
                                    }
                                }
                            }
                        )
                    }
            ) {
                tab(item, item.getKey() == selectedKey, false)
            }
        }
    }
}
