package com.sshellspace.feature.terminal.ui.screen.sftp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.AuthChallenge
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.LocalFilesService
import com.sshellspace.feature.terminal.domain.RemoteFile
import com.sshellspace.feature.terminal.domain.RemoteListing
import com.sshellspace.feature.terminal.domain.SecretPrompt
import com.sshellspace.feature.terminal.domain.SftpFailure
import com.sshellspace.feature.terminal.domain.SftpSession
import com.sshellspace.feature.terminal.domain.ShellSession
import com.sshellspace.feature.terminal.domain.SshConnectResult
import com.sshellspace.feature.terminal.domain.SshConnectionService
import com.sshellspace.feature.terminal.domain.SshPrompter
import com.sshellspace.feature.terminal.domain.TransferProgress
import com.sshellspace.feature.terminal.domain.TransferSummary
import com.sshellspace.feature.terminal.domain.isSafeLocalName
import com.sshellspace.feature.terminal.domain.isSafeRemoteName
import com.sshellspace.feature.terminal.domain.joinRemotePath
import com.sshellspace.feature.terminal.domain.models.SftpParams
import com.sshellspace.feature.terminal.domain.parentRemotePath
import com.sshellspace.feature.terminal.domain.printableRemoteText
import com.sshellspace.feature.terminal.domain.uniqueFileName
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val MAX_PARALLEL_TRANSFERS = 3

internal fun sftpTabTitle(name: String) = "$name (files)"

/**
 * Browses a saved connection's files over SFTP. The tab has its own SSH session, separate from any terminal, so
 * closing the tab ends it along with its transfers. Connection questions work as in the terminal tab: each
 * [SshPrompter] call shows a prompt state and suspends until the user responds.
 *
 * With no connection in [params] it browses this computer through [localFiles] instead, with no SSH or questions.
 */
class SftpBrowserViewModel(
    private val tabController: TabController,
    private val params: SftpParams,
    private val connectionService: SshConnectionService,
    private val vaultManager: VaultManager,
    private val localFiles: LocalFilesService,
) : ViewModel(), SshPrompter {

    private val connection = MutableStateFlow<SftpConnectionState>(SftpConnectionState.Connecting(params.title))
    private val browser = MutableStateFlow(SftpBrowserState(isThisDevice = params.connectionId == null))
    private val transfers = MutableStateFlow(emptyList<SftpTransfer>())

    val uiState = combine(connection, browser, transfers, ::SftpUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SftpUiState(connection.value))

    @Volatile
    private var pendingAnswer: CompletableDeferred<Any?>? = null
    private var connectJob: Job? = null
    private var loadJob: Job? = null
    private var previewJob: Job? = null
    private var ssh: ShellSession.Ssh? = null
    private var sftp: SftpSession? = null
    private var home: String = ""

    private val transferSlots = Semaphore(MAX_PARALLEL_TRANSFERS)
    private val transferJobs = mutableMapOf<Long, Job>()
    private var nextTransferId = 0L

    init {
        connect()
    }

    fun execute(action: SftpAction) {
        when (action) {
            is SftpAction.AnswerHostKey -> answer(action.trust)
            is SftpAction.SubmitSecret -> answer(action.value)
            is SftpAction.SubmitChallenge -> answer(action.answers)
            SftpAction.CancelConnecting -> if (pendingAnswer != null) answer(null) else cancelConnecting()
            SftpAction.Reconnect -> connect()
            is SftpAction.Navigate -> load(action.path)
            is SftpAction.GoToPath -> goToPath(action.text)
            // Lands on the folder it came out of, so the arrow keys carry on from there.
            SftpAction.GoUp -> browser.value.path.let { path -> parentRemotePath(path)?.let { load(it, reveal = path) } }
            SftpAction.GoHome -> load(home)
            SftpAction.Refresh -> load(currentFolder(), keepSelection = true)
            SftpAction.ToggleHidden -> browser.update { it.copy(showHidden = !it.showHidden) }
            is SftpAction.SetFilter -> browser.update { it.copy(filter = action.text) }
            is SftpAction.Sort -> sort(action.column)
            is SftpAction.Click -> click(action.file, action.mode)
            is SftpAction.MoveSelection -> moveSelection(action.delta, action.extend)
            SftpAction.SelectAll -> browser.update { state ->
                state.copy(selection = state.visibleEntries().mapTo(mutableSetOf()) { it.path })
            }
            SftpAction.ClearSelection -> browser.update {
                it.copy(selection = emptySet(), selectionAnchor = null, selectionCursor = null)
            }
            SftpAction.DismissNotice -> browser.update { it.copy(notice = null) }
            else -> executeFileAction(action)
        }
    }

    private fun executeFileAction(action: SftpAction) {
        when (action) {
            SftpAction.RequestNewFolder -> showDialog(SftpDialog.NewFolder())
            is SftpAction.CreateFolder -> createFolder(action.name)
            SftpAction.RequestRename -> browser.value.selectedFiles().singleOrNull()
                ?.let { showDialog(SftpDialog.Rename(it)) }
            is SftpAction.Rename -> rename(action.file, action.name)
            SftpAction.RequestDelete -> browser.value.selectedFiles().takeIf { it.isNotEmpty() }
                ?.let { showDialog(SftpDialog.ConfirmDelete(it)) }
            is SftpAction.Delete -> delete(action.files)
            SftpAction.DismissDialog -> {
                previewJob?.cancel()
                showDialog(null)
            }
            is SftpAction.OpenPreview -> openPreview(action.file)
            is SftpAction.MovePreview -> movePreview(action.delta)
            is SftpAction.Download -> requestTransfer(PendingTransfer.Download(action.files, action.directory))
            is SftpAction.Upload -> requestTransfer(PendingTransfer.Upload(action.sources, currentFolder()))
            is SftpAction.ResolveConflict -> resolveConflict(action.choice)
            is SftpAction.CancelTransfer -> cancelTransfer(action.id)
            SftpAction.ClearFinishedTransfers -> transfers.update { list -> list.filterNot { it.status.isFinished } }
            SftpAction.ToggleTransfers -> browser.update { it.copy(transfersCollapsed = !it.transfersCollapsed) }
            else -> Unit
        }
    }

    override suspend fun confirmHostKey(prompt: HostKeyPrompt): Boolean =
        ask(SftpConnectionState.AwaitingHostKey(prompt)) as? Boolean ?: false

    override suspend fun askSecret(prompt: SecretPrompt): String? =
        ask(SftpConnectionState.AwaitingSecret(prompt)) as? String

    @Suppress("UNCHECKED_CAST")
    override suspend fun answerChallenge(challenge: AuthChallenge): List<String>? =
        ask(SftpConnectionState.AwaitingChallenge(challenge)) as? List<String>

    private suspend fun ask(prompt: SftpConnectionState): Any? {
        val deferred = CompletableDeferred<Any?>()
        pendingAnswer = deferred
        connection.value = prompt
        return try {
            deferred.await()
        } finally {
            pendingAnswer = null
            connection.compareAndSet(prompt, SftpConnectionState.Connecting(params.title))
        }
    }

    private fun answer(value: Any?) {
        pendingAnswer?.complete(value)
    }

    private fun connect() {
        if (connectJob?.isActive == true) return
        closeSession()
        connection.value = SftpConnectionState.Connecting(params.title)
        connectJob = viewModelScope.launch {
            val connectionId = params.connectionId
            connection.value = if (connectionId == null) openThisDevice() else connectToServer(connectionId)
        }
    }

    private suspend fun openThisDevice(): SftpConnectionState {
        val opened = localFiles.open()
        return try {
            home = opened.home()
            sftp = opened
            load(browser.value.path.ifEmpty { home }, keepSelection = true, fallback = home)
            SftpConnectionState.Connected
        } catch (e: SftpFailure) {
            opened.close()
            SftpConnectionState.Failed(e.message)
        }
    }

    private suspend fun connectToServer(connectionId: Uuid): SftpConnectionState {
        // Connecting needs the saved login from the vault, which can be locked when reconnecting.
        vaultManager.activeVault.first { it != null }
        val result = try {
            connectionService.connect(connectionId, this@SftpBrowserViewModel)
        } catch (e: CancellationException) {
            // Is an IllegalStateException too, and has to carry on cancelling this coroutine.
            throw e
        } catch (_: IllegalStateException) {
            SshConnectResult.Failed("The vault locked while connecting.")
        }
        return when (result) {
            is SshConnectResult.Success -> openSftp(result)
            SshConnectResult.Cancelled -> SftpConnectionState.Cancelled
            is SshConnectResult.Failed -> SftpConnectionState.Failed(result.message)
        }
    }

    private suspend fun openSftp(result: SshConnectResult.Success): SftpConnectionState {
        val session = result.session
        return try {
            val opened = withContext(Dispatchers.IO) { session.openSftp() }
            home = opened.home()
            ssh = session
            sftp = opened
            tabController.setTitle(sftpTabTitle(result.connection.name))
            session.onClose { viewModelScope.launch { onSessionClosed(session) } }
            // A reconnect goes back to the folder it was in, if that's still there.
            load(browser.value.path.ifEmpty { home }, keepSelection = true, fallback = home)
            SftpConnectionState.Connected
        } catch (e: SftpFailure) {
            session.close()
            SftpConnectionState.Failed(e.message)
        } catch (e: CancellationException) {
            session.close()
            throw e
        }
    }

    private fun onSessionClosed(session: ShellSession.Ssh) {
        // Also fires for sessions this class closed itself.
        if (ssh !== session) return
        closeSession()
        browser.update { it.copy(isLoading = false, busyMessage = null, dialog = null) }
        connection.value = SftpConnectionState.Disconnected(params.title)
    }

    private fun cancelConnecting() {
        if (connectJob?.isActive != true) return
        connectJob?.cancel()
        connection.value = SftpConnectionState.Cancelled
    }

    private fun closeSession() {
        val closing = ssh
        loadJob?.cancel()
        previewJob?.cancel()
        ssh = null
        sftp?.close()
        sftp = null
        closing?.close()
    }

    /** @param reveal the row to select and scroll to, when it's in this folder. */
    private fun load(path: String, keepSelection: Boolean = false, fallback: String? = null, reveal: String? = null) {
        val session = sftp ?: return
        loadJob?.cancel()
        browser.update { it.copy(isLoading = true) }
        loadJob = viewModelScope.launch {
            try {
                val listing = session.list(path)
                browser.update { state ->
                    state.showingListing(path, listing, keepSelection, reveal)
                }
            } catch (e: SftpFailure) {
                if (fallback != null && fallback != path) {
                    load(fallback)
                } else {
                    // Stays in the current folder, whose listing is still right.
                    browser.update { it.copy(isLoading = false, notice = e.message) }
                }
            }
        }
    }

    private fun SftpBrowserState.showingListing(
        path: String,
        listing: RemoteListing,
        keepSelection: Boolean,
        reveal: String?,
    ): SftpBrowserState {
        // A server can list a name twice, and the list can't show two rows for one path.
        val files = listing.files.distinctBy { it.path }
        val paths = files.mapTo(mutableSetOf()) { it.path }
        val revealed = reveal?.takeIf { it in paths }
        val kept = { current: String? -> current.takeIf { keepSelection && it in paths } }
        return copy(
            path = path,
            entries = files,
            truncated = listing.truncated,
            isLoading = false,
            filter = if (path == this.path) filter else "",
            selection = when {
                revealed != null -> setOf(revealed)
                keepSelection -> selection.intersect(paths)
                else -> emptySet()
            },
            selectionAnchor = revealed ?: kept(selectionAnchor),
            selectionCursor = revealed ?: kept(selectionCursor),
            revealCursor = revealed != null,
        )
    }

    private fun goToPath(text: String) {
        val session = sftp ?: return
        val trimmed = text.trim().ifEmpty { return }
        val path = when {
            trimmed == "~" -> home
            trimmed.startsWith("~/") -> joinRemotePath(home, trimmed.removePrefix("~/"))
            session.isAbsolutePath(trimmed) -> trimmed
            else -> joinRemotePath(currentFolder(), trimmed)
        }
        launchReporting {
            val resolved = session.resolve(path)
            if (resolved.isDirectory) load(resolved.path) else notice("${resolved.displayName} isn't a folder.")
        }
    }

    private fun sort(column: SftpSortColumn) {
        browser.update { state ->
            if (state.sortColumn == column) {
                state.copy(sortAscending = !state.sortAscending)
            } else {
                state.copy(sortColumn = column, sortAscending = true)
            }
        }
    }

    private fun click(file: RemoteFile, mode: SftpClickMode) {
        browser.update { state ->
            when (mode) {
                SftpClickMode.Single -> state.selectingOnly(file.path)
                SftpClickMode.Toggle -> state.copy(
                    selection = if (file.path in state.selection) state.selection - file.path else state.selection + file.path,
                    selectionAnchor = file.path,
                    selectionCursor = file.path,
                )
                SftpClickMode.Range -> state.selectingRangeTo(file.path, state.visibleEntries().map { it.path })
            }.copy(revealCursor = false)
        }
    }

    private fun moveSelection(delta: Int, extend: Boolean) {
        browser.update { state ->
            val paths = state.visibleEntries().map { it.path }
            if (paths.isEmpty()) return@update state
            val current = paths.indexOf(state.selectionCursor)
            // With no cursor, starts just outside the end of the list that it's moving away from.
            val from = when {
                current >= 0 -> current
                delta >= 0 -> -1
                else -> paths.size
            }
            val target = paths[(from + delta).coerceIn(paths.indices)]
            val moved = if (extend) state.selectingRangeTo(target, paths) else state.selectingOnly(target)
            moved.copy(revealCursor = true)
        }
    }

    private fun openPreview(file: RemoteFile) {
        val session = sftp ?: return
        previewJob?.cancel()
        showDialog(SftpDialog.Preview(file))
        previewJob = viewModelScope.launch {
            val loaded = try {
                val bytes = session.readBytes(file, MAX_PREVIEW_BYTES)
                SftpDialog.Preview(file, image = withContext(Dispatchers.Default) { decodePreview(bytes) })
            } catch (e: SftpFailure) {
                SftpDialog.Preview(file, error = e.message)
            }
            // Moved on from or closed meanwhile, in which case this result isn't wanted any more.
            browser.update { state ->
                if ((state.dialog as? SftpDialog.Preview)?.file == file) state.copy(dialog = loaded) else state
            }
        }
    }

    private fun movePreview(delta: Int) {
        val state = browser.value
        val current = (state.dialog as? SftpDialog.Preview)?.file ?: return
        val pictures = state.visibleEntries().filter { it.canPreview }
        val next = pictures.getOrNull(pictures.indexOfFirst { it.path == current.path } + delta) ?: return
        // The list follows along, so closing the preview leaves the last picture selected and in view.
        browser.update { it.selectingOnly(next.path).copy(revealCursor = true) }
        openPreview(next)
    }

    private fun SftpBrowserState.selectingOnly(path: String) =
        copy(selection = setOf(path), selectionAnchor = path, selectionCursor = path)

    private fun SftpBrowserState.selectingRangeTo(path: String, paths: List<String>): SftpBrowserState {
        val to = paths.indexOf(path)
        val from = paths.indexOf(selectionAnchor).takeIf { it >= 0 } ?: to
        return copy(
            selection = paths.subList(minOf(from, to), maxOf(from, to) + 1).toSet(),
            selectionAnchor = paths[from],
            selectionCursor = path,
        )
    }

    /** The folder on show, or home if the first listing failed and nothing is on show yet. */
    private fun currentFolder() = browser.value.path.ifEmpty { home }

    private fun showDialog(dialog: SftpDialog?) {
        browser.update { it.copy(dialog = dialog) }
    }

    private fun notice(message: String) {
        browser.update { it.copy(notice = message) }
    }

    private fun invalidNameReason(name: String): String? = when {
        name.isBlank() -> "Enter a name."
        !isSafeRemoteName(name) -> "A name can't contain / or be . or .."
        else -> null
    }

    private fun createFolder(rawName: String) {
        val session = sftp ?: return
        val name = rawName.trim()
        invalidNameReason(name)?.let { return showDialog(SftpDialog.NewFolder(it)) }
        val path = joinRemotePath(currentFolder(), name)
        showDialog(null)
        launchOperation("Creating $name…") {
            if (session.exists(path)) throw SftpFailure("${printableRemoteText(name)} already exists.")
            session.createDirectory(path)
        }
    }

    private fun rename(file: RemoteFile, rawName: String) {
        val session = sftp ?: return
        val name = rawName.trim()
        invalidNameReason(name)?.let { return showDialog(SftpDialog.Rename(file, it)) }
        if (name == file.name) return showDialog(null)
        val parent = parentRemotePath(file.path) ?: return showDialog(null)
        val target = joinRemotePath(parent, name)
        showDialog(null)
        launchOperation("Renaming ${file.displayName}…") {
            // Some servers would replace what's there, without asking.
            if (session.exists(target)) throw SftpFailure("${printableRemoteText(name)} already exists.")
            session.rename(file.path, target)
        }
    }

    private fun delete(files: List<RemoteFile>) {
        val session = sftp ?: return
        showDialog(null)
        launchOperation(if (files.size == 1) "Deleting ${files.single().displayName}…" else "Deleting ${files.size} items…") {
            files.forEach { session.delete(it) }
        }
    }

    /** Runs [block], then reloads the folder either way, since a failure can stop partway through. */
    private fun launchOperation(message: String, block: suspend () -> Unit) {
        browser.update { it.copy(busyMessage = message) }
        launchReporting {
            try {
                block()
            } finally {
                browser.update { it.copy(busyMessage = null) }
                load(currentFolder(), keepSelection = true)
            }
        }
    }

    private fun launchReporting(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: SftpFailure) {
                notice(e.message)
            }
        }
    }

    private fun requestTransfer(pending: PendingTransfer) {
        val session = sftp ?: return
        launchReporting {
            val safe = withoutUnsafeNames(pending)
            val conflicts = withContext(Dispatchers.IO) { conflictsOf(session, safe) }
            if (conflicts.isEmpty()) {
                startTransfers(session, safe, null)
            } else {
                showDialog(SftpDialog.Conflict(conflicts.map(::printableRemoteText), safe))
            }
        }
    }

    /** Files with names that aren't safe here can't be downloaded at all. A server chooses those names. */
    private fun withoutUnsafeNames(pending: PendingTransfer): PendingTransfer {
        // Nothing to fear from a name that's already on this computer.
        if (pending !is PendingTransfer.Download || params.connectionId == null) return pending
        val (safe, unsafe) = pending.files.partition { isSafeLocalName(it.name) }
        if (unsafe.isNotEmpty()) {
            notice("Skipped because the name isn't safe on this computer: " + unsafe.joinToString { it.displayName })
        }
        return pending.copy(files = safe)
    }

    private suspend fun conflictsOf(session: SftpSession, pending: PendingTransfer): List<String> = when (pending) {
        is PendingTransfer.Download -> pending.files.map { it.name }
            .filter { Files.exists(pending.directory.resolve(it), LinkOption.NOFOLLOW_LINKS) }
        is PendingTransfer.Upload -> pending.sources.mapNotNull { it.fileName?.toString() }
            .filter { session.exists(joinRemotePath(pending.directory, it)) }
    }

    private fun resolveConflict(choice: SftpConflictChoice) {
        val session = sftp ?: return
        val dialog = browser.value.dialog as? SftpDialog.Conflict ?: return
        showDialog(null)
        launchReporting { startTransfers(session, dialog.pending, choice) }
    }

    private suspend fun startTransfers(session: SftpSession, pending: PendingTransfer, choice: SftpConflictChoice?) {
        val replace = choice == SftpConflictChoice.Replace
        val keepBoth = choice == SftpConflictChoice.KeepBoth
        when (pending) {
            is PendingTransfer.Download -> pending.files.forEach { file ->
                val name = if (keepBoth) {
                    withContext(Dispatchers.IO) {
                        uniqueFileName(file.name) { Files.exists(pending.directory.resolve(it), LinkOption.NOFOLLOW_LINKS) }
                    }
                } else {
                    file.name
                }
                val target = pending.directory.resolve(name)
                enqueue(SftpDirection.Download, file.displayName) { progress ->
                    session.download(file, target, replace, progress)
                        // On this computer the copy can have landed in the folder on show.
                        .also { if (params.connectionId == null) load(currentFolder(), keepSelection = true) }
                }
            }
            is PendingTransfer.Upload -> pending.sources.forEach { source ->
                val sourceName = source.fileName?.toString() ?: return@forEach
                val name = if (keepBoth) {
                    uniqueFileName(sourceName) { session.exists(joinRemotePath(pending.directory, it)) }
                } else {
                    sourceName
                }
                enqueue(SftpDirection.Upload, printableRemoteText(sourceName)) { progress ->
                    session.upload(source, joinRemotePath(pending.directory, name), replace, progress)
                        .also { refreshIfShowing(pending.directory) }
                }
            }
        }
    }

    private fun refreshIfShowing(directory: String) {
        if (browser.value.path == directory) load(directory, keepSelection = true)
    }

    private fun enqueue(
        direction: SftpDirection,
        name: String,
        transfer: suspend ((TransferProgress) -> Unit) -> TransferSummary,
    ) {
        val id = nextTransferId++
        transfers.update { it + SftpTransfer(id, direction, name) }
        val job = viewModelScope.launch {
            try {
                transferSlots.withPermit {
                    updateTransfer(id, onlyIfActive = true) { copy(status = SftpTransferStatus.Running) }
                    val summary = transfer { progress -> updateTransfer(id, onlyIfActive = true) { copy(progress = progress) } }
                    updateTransfer(id, onlyIfActive = true) {
                        copy(status = SftpTransferStatus.Done, skipped = summary.skipped)
                    }
                }
            } catch (e: SftpFailure) {
                updateTransfer(id, onlyIfActive = true) { copy(status = SftpTransferStatus.Failed(e.message)) }
            } finally {
                transferJobs.remove(id)
            }
        }
        transferJobs[id] = job
    }

    /** Cancelled shows straight away, even though a read already under way only ends when the server answers it. */
    private fun cancelTransfer(id: Long) {
        updateTransfer(id, onlyIfActive = true) { copy(status = SftpTransferStatus.Cancelled) }
        transferJobs[id]?.cancel()
    }

    private fun updateTransfer(id: Long, onlyIfActive: Boolean, change: SftpTransfer.() -> SftpTransfer) {
        transfers.update { list ->
            list.map { transfer ->
                if (transfer.id == id && !(onlyIfActive && transfer.status.isFinished)) transfer.change() else transfer
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Unblocks a MINA thread that may be waiting on the user.
        answer(null)
        connectJob?.cancel()
        closeSession()
    }
}
