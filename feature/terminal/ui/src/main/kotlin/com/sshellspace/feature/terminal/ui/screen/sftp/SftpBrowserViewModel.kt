package com.sshellspace.feature.terminal.ui.screen.sftp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.tabs.TabController
import com.sshellspace.feature.terminal.domain.AuthChallenge
import com.sshellspace.feature.terminal.domain.HostKeyPrompt
import com.sshellspace.feature.terminal.domain.RemoteFile
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
 */
class SftpBrowserViewModel(
    private val tabController: TabController,
    private val params: SftpParams,
    private val connectionService: SshConnectionService,
    private val vaultManager: VaultManager,
) : ViewModel(), SshPrompter {

    private val connection = MutableStateFlow<SftpConnectionState>(SftpConnectionState.Connecting(params.title))
    private val browser = MutableStateFlow(SftpBrowserState())
    private val transfers = MutableStateFlow(emptyList<SftpTransfer>())

    val uiState = combine(connection, browser, transfers, ::SftpUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SftpUiState(connection.value))

    @Volatile
    private var pendingAnswer: CompletableDeferred<Any?>? = null
    private var connectJob: Job? = null
    private var loadJob: Job? = null
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
            SftpAction.GoUp -> parentRemotePath(browser.value.path)?.let { load(it) }
            SftpAction.GoHome -> load(home)
            SftpAction.Refresh -> load(currentFolder(), keepSelection = true)
            SftpAction.ToggleHidden -> browser.update { it.copy(showHidden = !it.showHidden) }
            is SftpAction.Sort -> sort(action.column)
            is SftpAction.Click -> click(action.file, action.mode)
            SftpAction.SelectAll -> browser.update { state ->
                state.copy(selection = state.visibleEntries().mapTo(mutableSetOf()) { it.path })
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
            SftpAction.DismissDialog -> showDialog(null)
            is SftpAction.Download -> requestTransfer(PendingTransfer.Download(action.files, action.directory))
            is SftpAction.Upload -> requestTransfer(PendingTransfer.Upload(action.sources, currentFolder()))
            is SftpAction.ResolveConflict -> resolveConflict(action.choice)
            is SftpAction.CancelTransfer -> cancelTransfer(action.id)
            SftpAction.ClearFinishedTransfers -> transfers.update { list -> list.filterNot { it.status.isFinished } }
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
            // Connecting needs the saved login from the vault, which can be locked when reconnecting.
            vaultManager.activeVault.first { it != null }
            val result = try {
                connectionService.connect(params.connectionId, this@SftpBrowserViewModel)
            } catch (e: CancellationException) {
                // Is an IllegalStateException too, and has to carry on cancelling this coroutine.
                throw e
            } catch (_: IllegalStateException) {
                SshConnectResult.Failed("The vault locked while connecting.")
            }
            connection.value = when (result) {
                is SshConnectResult.Success -> openSftp(result)
                SshConnectResult.Cancelled -> SftpConnectionState.Cancelled
                is SshConnectResult.Failed -> SftpConnectionState.Failed(result.message)
            }
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
        ssh = null
        sftp?.close()
        sftp = null
        closing?.close()
    }

    private fun load(path: String, keepSelection: Boolean = false, fallback: String? = null) {
        val session = sftp ?: return
        loadJob?.cancel()
        browser.update { it.copy(isLoading = true) }
        loadJob = viewModelScope.launch {
            try {
                val listing = session.list(path)
                browser.update { state ->
                    val paths = listing.files.mapTo(mutableSetOf()) { it.path }
                    state.copy(
                        path = path,
                        entries = listing.files,
                        truncated = listing.truncated,
                        isLoading = false,
                        selection = if (keepSelection) state.selection.intersect(paths) else emptySet(),
                        selectionAnchor = state.selectionAnchor.takeIf { keepSelection && it in paths },
                    )
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

    private fun goToPath(text: String) {
        val session = sftp ?: return
        val trimmed = text.trim().ifEmpty { return }
        val path = when {
            trimmed == "~" -> home
            trimmed.startsWith("~/") -> joinRemotePath(home, trimmed.removePrefix("~/"))
            trimmed.startsWith("/") -> trimmed
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
                SftpClickMode.Single -> state.copy(selection = setOf(file.path), selectionAnchor = file.path)
                SftpClickMode.Toggle -> state.copy(
                    selection = if (file.path in state.selection) state.selection - file.path else state.selection + file.path,
                    selectionAnchor = file.path,
                )
                SftpClickMode.Range -> {
                    val paths = state.visibleEntries().map { it.path }
                    val from = paths.indexOf(state.selectionAnchor).takeIf { it >= 0 } ?: paths.indexOf(file.path)
                    val to = paths.indexOf(file.path)
                    state.copy(selection = paths.subList(minOf(from, to), maxOf(from, to) + 1).toSet())
                }
            }
        }
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
        if (pending !is PendingTransfer.Download) return pending
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
