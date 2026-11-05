package com.sshellspace.feature.terminal.ui.screen.sftp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.composeunstyled.Text
import com.composeunstyled.theme.Theme
import com.sshellspace.core.ui.designsystem.components.TextField
import com.sshellspace.core.ui.designsystem.theme.colors
import com.sshellspace.core.ui.designsystem.theme.error
import com.sshellspace.core.ui.designsystem.theme.scrim
import com.sshellspace.core.ui.designsystem.theme.textSecondary
import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.ui.screen.ssh.Actions
import com.sshellspace.feature.terminal.ui.screen.ssh.Card

private const val MAX_LISTED_NAMES = 5

@Composable
internal fun Dialog(
    dialog: SftpDialog,
    state: SftpBrowserState,
    visible: List<RemoteFile>,
    pickers: SftpPickers,
    onAction: (SftpAction) -> Unit,
) {
    val dismiss = { onAction(SftpAction.DismissDialog) }
    val focusRequester = remember { FocusRequester() }
    // Takes the keyboard from the list behind, so Esc works here and its keys can't change what's being asked about.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Theme[colors][scrim])
            // Keeps clicks away from the list behind.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    dismiss()
                    true
                } else {
                    false
                }
            },
    ) {
        when (dialog) {
            is SftpDialog.NewFolder -> NameDialog(
                title = "New folder",
                initial = "",
                confirmLabel = "Create",
                errorMessage = dialog.error,
                onConfirm = { onAction(SftpAction.CreateFolder(it)) },
                onDismiss = dismiss,
            )
            is SftpDialog.Rename -> NameDialog(
                title = "Rename ${dialog.file.displayName}",
                initial = dialog.file.name,
                confirmLabel = "Rename",
                errorMessage = dialog.error,
                onConfirm = { onAction(SftpAction.Rename(dialog.file, it)) },
                onDismiss = dismiss,
            )
            is SftpDialog.Preview -> ImagePreview(
                preview = dialog,
                pictures = visible.filter { it.canPreview },
                downloadLabel = state.downloadLabel,
                onDownload = { pickers.download(listOf(dialog.file)) },
                onAction = onAction,
            )
            is SftpDialog.ConfirmDelete -> Card {
                val only = dialog.files.singleOrNull()
                Text(if (only != null) "Delete ${only.displayName}?" else "Delete ${dialog.files.size} items?", color = Theme[colors][error])
                Text(deleteMessage(dialog.files), color = Theme[colors][textSecondary])
                Actions(
                    primaryAction = "Delete" to { onAction(SftpAction.Delete(dialog.files)) },
                    secondaryAction = "Cancel" to dismiss,
                    destructive = true,
                )
            }
            is SftpDialog.Conflict -> Card {
                Text(if (dialog.names.size == 1) "${dialog.names.single()} already exists" else "${dialog.names.size} items already exist")
                Text(
                    listOfNotNull(
                        dialog.names.takeIf { it.size > 1 }?.take(MAX_LISTED_NAMES)?.joinToString(", "),
                        "Replacing overwrites files with the same name and merges folders. It can't be undone.",
                    ).joinToString("\n"),
                    color = Theme[colors][textSecondary],
                )
                Actions(
                    primaryAction = "Replace" to { onAction(SftpAction.ResolveConflict(SftpConflictChoice.Replace)) },
                    secondaryAction = "Cancel" to dismiss,
                    middleAction = "Keep both" to { onAction(SftpAction.ResolveConflict(SftpConflictChoice.KeepBoth)) },
                    destructive = true,
                )
            }
        }
    }
}

private fun deleteMessage(files: List<RemoteFile>): String = buildString {
    if (files.size > 1) append(files.take(MAX_LISTED_NAMES).joinToString(", ") { it.displayName }).append(". ")
    if (files.any { it.isDirectory && !it.isSymlink }) append("Folders are deleted with everything in them. ")
    if (files.any { it.isSymlink }) append("Links are removed, not what they point to. ")
    append("This can't be undone.")
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    errorMessage: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Card {
        Text(title)
        Box(
            modifier = Modifier.onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        ) {
            TextField(
                value = value,
                onValueChange = { value = it },
                label = "Name",
                isError = errorMessage != null,
                focusRequester = focusRequester,
                onSubmit = onConfirm,
            )
        }
        errorMessage?.let { Text(it, color = Theme[colors][error]) }
        Actions(
            primaryAction = confirmLabel to { onConfirm(value) },
            secondaryAction = "Cancel" to onDismiss,
        )
    }
}
