package com.sshellspace.core.ui.designsystem.titlebar

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.composeunstyled.AnchorSide
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.common.asString
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.background
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.hoverOverlay
import com.sshellspace.core.ui.designsystem.theme.separator
import com.sshellspace.core.ui.designsystem.theme.textPrimary
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.core.ui.tabs.TabId
import com.sshellspace.core.ui.tabs.TabItem
import kotlin.math.roundToInt
import sshellspace.core.ui.generated.resources.Res
import sshellspace.core.ui.generated.resources.close_tab
import sshellspace.core.ui.generated.resources.new_tab

internal val TabStripTopPadding = 6.dp
private val TabMinWidth = 104.dp
private val TabMaxWidth = 220.dp
private val PinnedTabTitleMaxWidth = 150.dp
private val CloseButtonAlwaysWidth = 140.dp
private val TabCornerRadius = 8.dp

/** How far the selected tab's foot curves out into the strip, which joins it to the page below. */
private val TabFlare = 8.dp

/** The space between two tabs' bodies. */
private val TabGap = 4.dp

/**
 * How much each tab overlaps the next, as in a browser. A tab's flare is blank unless it's selected, so without this
 * two flares would sit side by side between every pair of tabs.
 */
internal val TabOverlap = TabFlare * 2 - TabGap
private const val SLIDE_MILLIS = 160
private val EdgeScrollZone = 32.dp
private const val EDGE_SCROLL_PIXELS_PER_FRAME = 8f

/**
 * How far apart open tabs start: shared evenly like a browser's, scrolling only once they reach their minimum. Each tab
 * is [TabOverlap] wider than this, so the last one's flare also has to fit.
 */
internal fun tabSlotWidth(available: Dp, count: Int): Dp =
    if (count == 0) TabMaxWidth else ((available - TabOverlap) / count).coerceIn(TabMinWidth, TabMaxWidth)

/** Where a dragged tab's left edge sits. Stays among the open tabs, so it can't slide under what's beside the strip. */
internal fun draggedTabPosition(contentX: Float, grabOffset: Float, count: Int, slot: Float): Float =
    (contentX - grabOffset).coerceIn(0f, ((count - 1) * slot).coerceAtLeast(0f))

/** The position a tab dragged to [position] takes among the others. */
internal fun dropIndex(position: Float, count: Int, slot: Float): Int =
    (position / slot).roundToInt().coerceIn(0, (count - 1).coerceAtLeast(0))

/**
 * The open tabs, which share the space up to the new tab button and can be dragged to reorder. Gestures are handled once
 * for the whole strip: a handler on each tab would be measuring the pointer against something moving under it.
 */
@Composable
internal fun TabStrip(
    tabs: List<TabItem>,
    selectedId: TabId,
    onSelect: (TabId) -> Unit,
    onClose: (TabId) -> Unit,
    onMove: (TabId, Int) -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier,
    interactiveModifier: Modifier = Modifier,
) {
    // Stays put while the pointer is here after closing a tab, so the next close button is under it, like a browser.
    var heldSlot by remember { mutableStateOf<Dp?>(null) }
    LaunchedEffect(tabs.size) { heldSlot = heldSlot?.takeIf { tabs.isNotEmpty() } }

    Row(
        modifier = modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Exit) heldSlot = null
                }
            }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f, fill = false).fillMaxHeight()) {
            val slot = heldSlot?.takeIf { it * tabs.size + TabOverlap <= maxWidth } ?: tabSlotWidth(maxWidth, tabs.size)
            DraggableTabs(
                tabs = tabs,
                selectedId = selectedId,
                slot = slot,
                viewportWidth = maxWidth,
                onSelect = onSelect,
                onClose = { id ->
                    heldSlot = slot
                    onClose(id)
                },
                onMove = onMove,
                modifier = interactiveModifier,
            )
        }
        Tooltip(text = Res.string.new_tab.asString(), side = AnchorSide.Bottom) {
            TitleBarIconButton(
                icon = Icons.Plus,
                contentDescription = Res.string.new_tab.asString(),
                onClick = onNewTab,
                modifier = interactiveModifier.padding(top = TabStripTopPadding),
                size = 28.dp,
                iconSize = 16.dp,
            )
        }
    }
}

private class TabDrag(val id: TabId, val grabOffset: Float, pointerX: Float) {
    /** In the visible part of the strip, which doesn't move when the tabs scroll. */
    var pointerX by mutableFloatStateOf(pointerX)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DraggableTabs(
    tabs: List<TabItem>,
    selectedId: TabId,
    slot: Dp,
    viewportWidth: Dp,
    onSelect: (TabId) -> Unit,
    onClose: (TabId) -> Unit,
    onMove: (TabId, Int) -> Unit,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val slotPx = with(density) { slot.toPx() }
    val scroll = rememberScrollState()
    var drag by remember { mutableStateOf<TabDrag?>(null) }
    val latestTabs by rememberUpdatedState(tabs)
    val latestSlot by rememberUpdatedState(slotPx)
    val latestSelect by rememberUpdatedState(onSelect)
    val latestClose by rememberUpdatedState(onClose)
    val latestMove by rememberUpdatedState(onMove)

    fun moveDraggedTab(current: TabDrag) {
        val position = draggedTabPosition(current.pointerX + scroll.value, current.grabOffset, latestTabs.size, latestSlot)
        val target = dropIndex(position, latestTabs.size, latestSlot)
        if (latestTabs.getOrNull(target)?.tab?.id != current.id) latestMove(current.id, target)
    }

    // The selected tab can be a new one past the end, or one chosen from elsewhere.
    LaunchedEffect(selectedId, tabs.size, slotPx) {
        val index = tabs.indexOfFirst { it.tab.id == selectedId }
        if (index < 0 || drag != null) return@LaunchedEffect
        val left = (index * slotPx).toInt()
        val right = left + (slotPx + with(density) { TabOverlap.toPx() }).toInt()
        val viewport = scroll.viewportSize
        if (left < scroll.value) scroll.animateScrollTo(left)
        if (right > scroll.value + viewport) scroll.animateScrollTo(right - viewport)
    }

    // Keeps the strip moving while a tab is held at its edge with the pointer still.
    drag?.let { current ->
        val edge = with(density) { EdgeScrollZone.toPx() }
        val viewport = with(density) { viewportWidth.toPx() }
        LaunchedEffect(current) {
            while (true) {
                withFrameNanos { }
                val step = when {
                    current.pointerX < edge -> -EDGE_SCROLL_PIXELS_PER_FRAME
                    current.pointerX > viewport - edge -> EDGE_SCROLL_PIXELS_PER_FRAME
                    else -> 0f
                }
                if (step != 0f && scroll.scrollBy(step) != 0f) moveDraggedTab(current)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitPress()
                    val button = currentEvent.button
                    val contentX = down.position.x + scroll.value
                    val index = (contentX / latestSlot).toInt()
                    val tab = latestTabs.getOrNull(index)?.tab?.id ?: return@awaitEachGesture

                    if (button == PointerButton.Tertiary) {
                        down.consume()
                        if (waitForUpOrCancellation() != null) latestClose(tab)
                        return@awaitEachGesture
                    }
                    if (button != PointerButton.Primary) return@awaitEachGesture

                    // On press, as browsers do, so a tab being dragged is already the one on show.
                    latestSelect(tab)
                    down.consume()
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        ?: return@awaitEachGesture
                    val current = TabDrag(tab, contentX - index * latestSlot, slop.position.x)
                    drag = current
                    try {
                        moveDraggedTab(current)
                        horizontalDrag(slop.id) { change ->
                            change.consume()
                            current.pointerX = change.position.x
                            moveDraggedTab(current)
                        }
                    } finally {
                        drag = null
                    }
                }
            }
            .horizontalScroll(scroll),
    ) {
        Box(modifier = Modifier.width(slot * tabs.size + TabOverlap).fillMaxHeight()) {
            tabs.forEachIndexed { index, item ->
                val id = item.tab.id
                key(id) {
                    val dragged = drag?.takeIf { it.id == id }
                    val selected = id == selectedId
                    val nextSelected = tabs.getOrNull(index + 1)?.tab?.id == selectedId
                    val slid by animateFloatAsState(index * slotPx, tween(SLIDE_MILLIS))
                    BrowserTab(
                        title = item.title,
                        icon = item.icon,
                        isSelected = selected,
                        showDivider = !selected && !nextSelected && drag == null,
                        tooltipEnabled = drag == null,
                        // A narrow tab needs that room for its title, as in a browser.
                        hideTrailingWhenIdle = slot < CloseButtonAlwaysWidth,
                        modifier = Modifier
                            .zIndex(if (dragged != null) 2f else if (selected) 1f else 0f)
                            .offset {
                                val x = dragged?.let {
                                    draggedTabPosition(it.pointerX + scroll.value, it.grabOffset, tabs.size, slotPx)
                                } ?: slid
                                IntOffset(x.roundToInt(), 0)
                            }
                            .width(slot + TabOverlap)
                            .fillMaxHeight()
                            .semantics {
                                role = Role.Tab
                                this.selected = selected
                                onClick {
                                    onSelect(id)
                                    true
                                }
                            },
                        trailing = {
                            TitleBarIconButton(
                                icon = Icons.Close,
                                contentDescription = Res.string.close_tab.asString(),
                                onClick = { onClose(id) },
                                size = 18.dp,
                                iconSize = 12.dp,
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * The next press of any mouse button, since `awaitFirstDown` only reports the primary one and middle-click closes a tab.
 * A close button takes its own press, which leaves it consumed here.
 */
private suspend fun AwaitPointerEventScope.awaitPress(): PointerInputChange {
    while (true) {
        awaitPointerEvent().changes.firstOrNull { it.changedToDown() && !it.isConsumed }?.let { return it }
    }
}

/** The vault's tab, which always comes first, can't be moved or closed, and sizes to its name. */
@Composable
internal fun PinnedTab(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    showDivider: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    BrowserTab(
        title = title,
        icon = icon,
        isSelected = isSelected,
        showDivider = showDivider,
        titleModifier = Modifier.widthIn(max = PinnedTabTitleMaxWidth),
        modifier = modifier
            .fillMaxHeight()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSelect),
        trailing = trailing,
    )
}

@Composable
private fun BrowserTab(
    title: String,
    icon: ImageVector?,
    isSelected: Boolean,
    showDivider: Boolean,
    modifier: Modifier,
    titleModifier: Modifier? = null,
    tooltipEnabled: Boolean = true,
    hideTrailingWhenIdle: Boolean = false,
    trailing: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var truncated by remember { mutableStateOf(false) }
    val page = Theme[colors][background]

    Box(
        modifier = modifier
            .hoverable(interaction)
            .then(if (isSelected) Modifier.background(page, BrowserTabShape) else Modifier),
    ) {
        if (hovered && !isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = TabFlare, end = TabFlare, bottom = 4.dp)
                    .clip(RoundedCornerShape(TabCornerRadius))
                    .background(Theme[colors][hoverOverlay]),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = TabFlare + 8.dp, end = TabFlare + 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val tint = Theme[colors][if (isSelected) textPrimary else textSecondary]
            icon?.let {
                UnstyledIcon(imageVector = it, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            }
            Box(modifier = titleModifier ?: Modifier.weight(1f)) {
                // Only for a shortened title, since otherwise it would just repeat what the tab already says.
                Tooltip(text = title, enabled = truncated && tooltipEnabled, side = AnchorSide.Bottom) {
                    Text(
                        title,
                        color = tint,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { truncated = it.hasVisualOverflow },
                    )
                }
            }
            if (isSelected || hovered || !hideTrailingWhenIdle) trailing()
        }
        if (showDivider && !hovered) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    // Midway between this tab's body and the next one's, which overlaps this tab's flare.
                    .padding(end = TabFlare - TabGap / 2, bottom = 4.dp)
                    .width(1.dp)
                    .height(16.dp)
                    .background(Theme[colors][separator]),
            )
        }
    }
}

/** A small round button that only shows its background on hover. */
@Composable
internal fun TitleBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    tint: Color = Theme[colors][textSecondary],
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(modifier = modifier) {
        Button(
            onClick = onClick,
            interactionSource = interaction,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (hovered) Theme[colors][hoverOverlay] else Color.Transparent),
        ) {
            UnstyledIcon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (hovered) Theme[colors][textPrimary] else tint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** Rounded at the top with feet curving outward at the bottom, so the selected tab flows into the page below it. */
private val BrowserTabShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = with(density) { TabCornerRadius.toPx() }
        val flare = with(density) { TabFlare.toPx() }
        val (width, height) = size
        return Outline.Generic(
            Path().apply {
                moveTo(0f, height)
                quadraticTo(flare, height, flare, height - flare)
                lineTo(flare, radius)
                quadraticTo(flare, 0f, flare + radius, 0f)
                lineTo(width - flare - radius, 0f)
                quadraticTo(width - flare, 0f, width - flare, radius)
                lineTo(width - flare, height - flare)
                quadraticTo(width - flare, height, width, height)
                close()
            },
        )
    }
}
