package com.sshellspace.feature.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.AnchorSide
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.adaptive.currentWindowClass
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.Tooltip
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onSurface
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated

private val ExpandedNavWidth = 200.dp
private val CompactNavWidth = 56.dp

@Composable
fun VaultTabScreen(
    viewModel: VaultTabViewModel,
) {
    val currentPage by viewModel.page.collectAsState()

    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        PageColumn(
            currentPage = currentPage,
            onChangePage = viewModel::changePage,
            iconsOnly = currentWindowClass.isCompact,
        )

        AnimatedContent(
            targetState = currentPage,
            modifier = Modifier.fillMaxSize(),
        ) {
            it.screen()
        }
    }
}

@Composable
private fun PageColumn(
    currentPage: VaultTabPage,
    onChangePage: (VaultTabPage) -> Unit,
    iconsOnly: Boolean,
) {
    val width by animateDpAsState(if (iconsOnly) CompactNavWidth else ExpandedNavWidth)

    LazyColumn(
        modifier = Modifier.fillMaxHeight()
            .width(width)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
    ) {
        items(
            items = VaultTabPage.entries,
            key = { it.name },
        ) {
            Tooltip(
                text = it.title,
                enabled = iconsOnly,
                side = AnchorSide.End,
            ) {
                PageButton(
                    page = it,
                    selected = it == currentPage,
                    iconsOnly = iconsOnly,
                    onClick = { onChangePage(it) },
                )
            }
        }
    }
}

@Composable
private fun PageButton(
    page: VaultTabPage,
    selected: Boolean,
    iconsOnly: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) Theme[colors][primary] else Theme[colors][onSurface]
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Theme[colors][surfaceElevated] else Color.Transparent)
            .padding(8.dp),
    ) {
        if (iconsOnly) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                UnstyledIcon(page.icon, page.title, tint = tint)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                UnstyledIcon(page.icon, page.title, tint = tint)
                Text(page.title, color = tint, maxLines = 1)
            }
        }
    }
}
