package com.sshellspace.feature.newtab.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated
import com.sshellspace.core.ui.designsystem.theme.textSecondary

@Composable
fun QuickConnectPalette(
    viewModel: QuickConnectViewModel,
    onClose: (() -> Unit)?,
) {
    val uiState by viewModel.uiState.collectAsState()
    QuickConnectContent(
        uiState = uiState,
        onChoose = { item ->
            viewModel.execute(QuickConnectAction.Choose(item))
            onClose?.invoke()
        },
        onDismiss = { onClose?.invoke() },
    )
}

@Composable
internal fun QuickConnectContent(
    uiState: QuickConnectUiState,
    onChoose: (PaletteItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(0) }
    val results = remember(uiState, query) { uiState.search(query) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val shape = RoundedCornerShape(10.dp)
    val choose = { results.getOrNull(selected)?.takeIf(uiState::canChoose)?.let(onChoose) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(results.size) { selected = selected.coerceIn(0, (results.size - 1).coerceAtLeast(0)) }
    LaunchedEffect(selected) { if (results.isNotEmpty()) listState.animateScrollToItem(selected) }

    Box(modifier = Modifier.fillMaxSize().padding(top = 64.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 16.dp)
                .clip(shape)
                .background(Theme[colors][surface])
                .border(1.dp, Theme[colors][border], shape)
                .padding(10.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> {
                            if (results.isNotEmpty()) selected = (selected + 1) % results.size
                            true
                        }
                        Key.DirectionUp -> {
                            if (results.isNotEmpty()) selected = (selected - 1 + results.size) % results.size
                            true
                        }
                        Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = query,
                onValueChange = {
                    query = it
                    selected = 0
                },
                placeholder = "Search hosts and snippets",
                focusRequester = focusRequester,
                onSubmit = { choose() },
            )
            if (results.isEmpty()) {
                Text(
                    if (query.isBlank()) "No saved hosts or snippets yet." else "Nothing matches \"${query.trim()}\".",
                    color = Theme[colors][textSecondary],
                    modifier = Modifier.padding(8.dp),
                )
            }
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp), state = listState) {
                itemsIndexed(results, key = { _, item -> item.key }) { index, item ->
                    PaletteRow(
                        item = item,
                        highlighted = index == selected,
                        enabled = uiState.canChoose(item),
                        onClick = { onChoose(item) },
                    )
                }
            }
            Text(
                "↑↓ to move, Enter to choose, Esc to close",
                color = Theme[colors][textSecondary],
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun PaletteRow(item: PaletteItem, highlighted: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlighted) Theme[colors][surfaceElevated] else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UnstyledIcon(
                imageVector = if (item is PaletteItem.Host) Icons.Host else Icons.Snippet,
                contentDescription = null,
                tint = if (highlighted) Theme[colors][primary] else Theme[colors][textSecondary],
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (enabled) item.subtitle else "Open a terminal tab to use snippets",
                    color = Theme[colors][textSecondary],
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (enabled) Text(item.actionLabel, color = Theme[colors][textSecondary])
        }
    }
}
