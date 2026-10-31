package com.sshellspace.feature.vault

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.UnstyledIcon
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.onSurface
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surfaceElevated

@Composable
fun VaultTabScreen(
    viewModel: VaultTabViewModel,
) {
    val currentPage by viewModel.page.collectAsState()

    Row(
        modifier = Modifier.fillMaxSize(),
    ) {
        PageColumn(currentPage, viewModel::changePage)

        AnimatedContent(
            targetState = currentPage,
        ) {
            it.screen()
        }
    }
}

@Composable
private fun PageColumn(
    currentPage: VaultTabPage,
    onChangePage: (VaultTabPage) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxHeight()
            .width(200.dp)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Top),
    ) {
        items(
            items = VaultTabPage.entries,
            key = { it.name },
        ) {
            Button(
                onClick = { onChangePage(it) },
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (it == currentPage) Theme[colors][surfaceElevated] else Color.Transparent)
                    .padding(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val tint = if (it == currentPage) Theme[colors][primary] else Theme[colors][onSurface]
                    UnstyledIcon(it.icon, it.title, tint = tint)
                    Text(it.title, color = tint)
                }
            }
        }
    }
}
