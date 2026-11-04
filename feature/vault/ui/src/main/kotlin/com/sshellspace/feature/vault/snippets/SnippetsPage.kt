package com.sshellspace.feature.vault.snippets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.adaptive.currentWindowClass
import com.sshellspace.core.ui.designsystem.components.Button
import com.sshellspace.core.ui.designsystem.components.IconButton
import com.sshellspace.core.ui.designsystem.components.Switch
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.Icons
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.onPrimary
import com.sshellspace.core.ui.designsystem.theme.primary
import com.sshellspace.core.ui.designsystem.theme.surface
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.vault.components.SidebarPanel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SnippetsPage() {
    val viewModel = koinViewModel<SnippetsPageViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    SnippetsPageContent(uiState, viewModel::execute)
}

@Composable
internal fun SnippetsPageContent(
    uiState: SnippetsPageUiState,
    onAction: (SnippetsPageAction) -> Unit,
) {
    val compact = currentWindowClass.isCompact

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            SnippetList(uiState, onAction, compact, Modifier.fillMaxHeight().weight(1f))
            if (!compact && uiState.form != null) SnippetSidebar(uiState, uiState.form, onAction)
        }
        // Not enough room side by side, so the form floats over the list.
        if (compact && uiState.form != null) {
            Box(Modifier.align(Alignment.CenterEnd).shadow(16.dp)) { SnippetSidebar(uiState, uiState.form, onAction) }
        }
    }
}

@Composable
private fun SnippetList(
    uiState: SnippetsPageUiState,
    onAction: (SnippetsPageAction) -> Unit,
    compact: Boolean,
    modifier: Modifier,
) {
    Column(modifier = modifier.padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Snippets")
            TextField(
                value = uiState.searchQuery,
                onValueChange = { onAction(SnippetsPageAction.SetSearch(it)) },
                modifier = Modifier.weight(1f),
                placeholder = "Search by name or command",
            )
            NewSnippetButton(onClick = { onAction(SnippetsPageAction.OpenCreate) })
        }
        Text(
            "Saved in this vault. To send one to the terminal you're in, open quick connect (Cmd or Ctrl + Shift + K " +
                "unless you've changed it in Settings).",
            color = Theme[colors][textSecondary],
            modifier = Modifier.padding(vertical = 12.dp),
        )
        when {
            uiState.snippets.isEmpty() -> Text("No snippets yet.", color = Theme[colors][textSecondary])
            uiState.filteredSnippets.isEmpty() ->
                Text("No snippets match \"${uiState.searchQuery.trim()}\".", color = Theme[colors][textSecondary])
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.filteredSnippets, key = { it.id }) { snippet ->
                    Button(
                        onClick = { onAction(SnippetsPageAction.Select(snippet)) },
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Theme[colors][surface])
                            .padding(12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(snippet.name)
                            Text(
                                snippet.command,
                                color = Theme[colors][textSecondary],
                                fontFamily = FontFamily.Monospace,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewSnippetButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(Theme[colors][primary])
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("New snippet", color = Theme[colors][onPrimary])
    }
}

@Composable
private fun SnippetSidebar(
    uiState: SnippetsPageUiState,
    form: SnippetForm,
    onAction: (SnippetsPageAction) -> Unit,
) {
    val edit = { update: SnippetForm.() -> SnippetForm -> onAction(SnippetsPageAction.EditForm { it.update() }) }

    SidebarPanel(
        title = if (form.editingId == null) "New snippet" else "Edit snippet",
        onClose = { onAction(SnippetsPageAction.CloseForm) },
        footer = {
            uiState.errorMessage?.let { Text(it, color = Theme[colors][error]) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAction(SnippetsPageAction.Save) },
                    modifier = Modifier.weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Theme[colors][primary])
                        .padding(vertical = 10.dp),
                ) {
                    Text(if (form.editingId == null) "Create" else "Save", color = Theme[colors][onPrimary])
                }
                if (form.editingId != null) {
                    IconButton(
                        onClick = { onAction(SnippetsPageAction.Delete) },
                        imageVector = Icons.Trash,
                        contentDescription = "Delete snippet",
                    )
                }
            }
        },
    ) {
        TextField(
            value = form.name,
            onValueChange = { edit { copy(name = it) } },
            label = "Name",
            placeholder = "Tail nginx errors",
        )
        TextField(
            value = form.command,
            onValueChange = { edit { copy(command = it) } },
            label = "Command",
            placeholder = "tail -f /var/log/nginx/error.log",
            singleLine = false,
            minLines = 4,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Run immediately")
                Text(
                    "Press Enter after sending. When off, the command waits on the prompt so you can check it first.",
                    color = Theme[colors][textSecondary],
                )
            }
            Switch(
                checked = form.runImmediately,
                onCheckedChange = { edit { copy(runImmediately = it) } },
                accessibilityLabel = "Run immediately",
            )
        }
    }
}
