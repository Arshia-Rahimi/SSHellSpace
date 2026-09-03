package com.sshellspace.core.ui.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.composeunstyled.DropdownMenuPanel
import com.composeunstyled.Text
import com.composeunstyled.UnstyledDropdownMenu
import com.composeunstyled.UnstyledDropdownMenuItem
import com.composeunstyled.UnstyledHorizontalSeparator
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.theme.border
import com.sshellspace.core.ui.designsystem.theme.colors

@Composable
fun <T> Dropdown(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    optionLabel: (T) -> String = { it.toString() },
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (label != null) {
            Text(
                text = label,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp)),
        ) {
            UnstyledDropdownMenu(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth(),
                anchor = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(contentPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(optionLabel(selected))
                    }
                },
                panel = {
                    DropdownMenuPanel(Modifier.fillMaxWidth()) {
                        DropdownPanelContent(
                            options = options,
                            contentPadding = contentPadding,
                            optionLabel = optionLabel,
                            onSelected = {
                                onSelected(it)
                                expanded = false
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun <T> DropdownPanelContent(
    options: List<T>,
    contentPadding: PaddingValues,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
) {
    options.forEachIndexed { index, option ->
        UnstyledDropdownMenuItem(
            onClick = { onSelected(option) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(optionLabel(option))
            }
        }
        if (index != options.lastIndex) {
            UnstyledHorizontalSeparator(
                color = Theme[colors][border],
                thickness = 1.dp,
            )
        }
    }
}
