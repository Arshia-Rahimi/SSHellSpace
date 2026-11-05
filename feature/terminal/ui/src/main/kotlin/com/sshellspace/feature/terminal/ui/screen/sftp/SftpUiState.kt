package com.sshellspace.feature.terminal.ui.screen.sftp

import com.sshellspace.feature.terminal.domain.AuthChallenge
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.domain.RemoteFileKind
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.terminal.domain.TransferProgress
import java.nio.file.Path

data class SftpUiState(
    val connection: SftpConnectionState,
    val browser: SftpBrowserState = SftpBrowserState(),
    val transfers: List<SftpTransfer> = emptyList(),
)

sealed interface SftpConnectionState {
    data class Connecting(val title: String) : SftpConnectionState
    data class AwaitingHostKey(val prompt: HostKeyPrompt) : SftpConnectionState
    data class AwaitingSecret(val prompt: SecretPrompt) : SftpConnectionState
    data class AwaitingChallenge(val challenge: AuthChallenge) : SftpConnectionState
    data object Connected : SftpConnectionState
    data class Failed(val message: String) : SftpConnectionState
    data object Cancelled : SftpConnectionState
    data class Disconnected(val title: String) : SftpConnectionState
}

enum class SftpSortColumn { Name, Size, Modified }

data class SftpBrowserState(
    /** The folder on show. Empty until the first listing arrives. */
    val path: String = "",
    val entries: List<RemoteFile> = emptyList(),
    val truncated: Boolean = false,
    val isLoading: Boolean = false,
    /** What a running rename or delete is doing. */
    val busyMessage: String? = null,
    val notice: String? = null,
    val selection: Set<String> = emptySet(),
    /** Where a shift-click range starts. */
    val selectionAnchor: String? = null,
    /** Where the arrow keys move from: the last row clicked or moved to. */
    val selectionCursor: String? = null,
    /**
     * The list should scroll to the cursor. Only for the keyboard and for leaving a folder: scrolling on a click would
     * move the row out from under the pointer, and turn a double click into single clicks on two rows.
     */
    val revealCursor: Boolean = false,
    val showHidden: Boolean = false,
    /** Narrows the list to names containing this. It only lasts while in the folder. */
    val filter: String = "",
    val sortColumn: SftpSortColumn = SftpSortColumn.Name,
    val sortAscending: Boolean = true,
    val dialog: SftpDialog? = null,
    val transfersCollapsed: Boolean = false,
    /** Browsing this computer's own files rather than a server's. */
    val isThisDevice: Boolean = false,
) {
    /** What the list shows, in order: folders first, then the chosen sort. */
    fun visibleEntries(): List<RemoteFile> {
        val shown = entries.filter { (showHidden || !it.isDotfile) && it.displayName.contains(filter, ignoreCase = true) }
        val byColumn: Comparator<RemoteFile> = when (sortColumn) {
            SftpSortColumn.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            // The size a server gives a folder or link says nothing about what's in it.
            SftpSortColumn.Size -> compareBy { if (it.kind == RemoteFileKind.File) it.size else 0L }
            SftpSortColumn.Modified -> compareBy { it.modifiedMillis ?: 0L }
        }
        val ordered = if (sortAscending) byColumn else byColumn.reversed()
        return shown.sortedWith(compareBy<RemoteFile> { !it.isDirectory }.then(ordered))
    }

    /** Selected files still on show, so hiding dotfiles or filtering also keeps them out of a delete. */
    fun selectedFiles(): List<RemoteFile> = visibleEntries().filter { it.path in selection }
}

internal val RemoteFile.isDotfile: Boolean get() = name.startsWith('.')

sealed interface SftpDialog {
    data class NewFolder(val error: String? = null) : SftpDialog
    data class Rename(val file: RemoteFile, val error: String? = null) : SftpDialog
    data class ConfirmDelete(val files: List<RemoteFile>) : SftpDialog

    /** Some of what [pending] would write already exists under [names]. */
    data class Conflict(val names: List<String>, val pending: PendingTransfer) : SftpDialog

    /** A picture being looked at. Until [image] or [error] is set, it's still loading. */
    data class Preview(val file: RemoteFile, val image: SftpPreviewImage? = null, val error: String? = null) : SftpDialog
}

sealed interface PendingTransfer {
    data class Download(val files: List<RemoteFile>, val directory: Path) : PendingTransfer
    data class Upload(val sources: List<Path>, val directory: String) : PendingTransfer
}

enum class SftpDirection { Download, Upload }

sealed interface SftpTransferStatus {
    data object Queued : SftpTransferStatus
    data object Running : SftpTransferStatus
    data object Done : SftpTransferStatus
    data object Cancelled : SftpTransferStatus
    data class Failed(val message: String) : SftpTransferStatus

    val isFinished: Boolean get() = this != Queued && this != Running
}

data class SftpTransfer(
    val id: Long,
    val direction: SftpDirection,
    val name: String,
    val status: SftpTransferStatus = SftpTransferStatus.Queued,
    /** Null until the transfer knows how much there is to do. */
    val progress: TransferProgress? = null,
    val skipped: List<String> = emptyList(),
)

enum class SftpClickMode { Single, Toggle, Range }

enum class SftpConflictChoice { Replace, KeepBoth }

sealed interface SftpAction {
    data class AnswerHostKey(val trust: Boolean) : SftpAction
    data class SubmitSecret(val value: String) : SftpAction
    data class SubmitChallenge(val answers: List<String>) : SftpAction

    /** Dismisses the open prompt, or stops connecting if no prompt is open. */
    data object CancelConnecting : SftpAction
    data object Reconnect : SftpAction

    data class Navigate(val path: String) : SftpAction

    /** Goes to a path the user typed, which may be relative or start with `~`. */
    data class GoToPath(val text: String) : SftpAction
    data object GoUp : SftpAction
    data object GoHome : SftpAction
    data object Refresh : SftpAction
    data object ToggleHidden : SftpAction
    data class SetFilter(val text: String) : SftpAction
    data class Sort(val column: SftpSortColumn) : SftpAction
    data class Click(val file: RemoteFile, val mode: SftpClickMode) : SftpAction

    /** Moves the cursor [delta] rows, staying inside the list. With no cursor on show, it counts from an end of the list. */
    data class MoveSelection(val delta: Int, val extend: Boolean = false) : SftpAction
    data object SelectAll : SftpAction
    data object ClearSelection : SftpAction
    data object DismissNotice : SftpAction

    data class OpenPreview(val file: RemoteFile) : SftpAction

    /** Goes [delta] pictures along, among those on show in the list, and stops at the ends. */
    data class MovePreview(val delta: Int) : SftpAction

    data object RequestNewFolder : SftpAction
    data class CreateFolder(val name: String) : SftpAction
    data object RequestRename : SftpAction
    data class Rename(val file: RemoteFile, val name: String) : SftpAction
    data object RequestDelete : SftpAction
    data class Delete(val files: List<RemoteFile>) : SftpAction
    data object DismissDialog : SftpAction

    data class Download(val files: List<RemoteFile>, val directory: Path) : SftpAction
    data class Upload(val sources: List<Path>) : SftpAction
    data class ResolveConflict(val choice: SftpConflictChoice) : SftpAction
    data class CancelTransfer(val id: Long) : SftpAction
    data object ClearFinishedTransfers : SftpAction
    data object ToggleTransfers : SftpAction
}
