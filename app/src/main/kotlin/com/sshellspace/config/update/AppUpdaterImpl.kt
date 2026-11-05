package com.sshellspace.config.update

import com.sshellspace.core.domain.models.AppUpdateState
import com.sshellspace.core.domain.repositories.AppUpdater
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.tabs.TabManager
import com.sshellspace.feature.terminal.domain.PortForwardState
import com.sshellspace.feature.terminal.domain.PortForwardingService
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.exception.UpdateException
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

private const val TICK_MILLIS = 60_000L

/**
 * Soon enough for a new version to show on the unlock screen before the user has moved on. Not straight away, because the
 * settings start out as their defaults until the database has been read, and those say updates are on.
 */
private const val FIRST_CHECK_DELAY_MILLIS = 10_000L
private const val MILLIS_PER_HOUR = 3_600_000L

/** How soon to try again after a check that failed, such as while offline, whatever the chosen interval. */
private const val RETRY_MILLIS = MILLIS_PER_HOUR
private const val MAX_ARTIFACT_BYTES = 1024L * 1024 * 1024

// The manifest comes from an untrusted host and is read before anything can be verified. The updater uses this name as
// a path while downloading, so one like ../../.ssh/authorized_keys would otherwise write there.
private val SAFE_ARTIFACT_NAME = Regex("""[A-Za-z0-9][A-Za-z0-9._-]*""")
private val SAFE_VERSION = Regex("""\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?""")

private const val CHECK_FAILED = "Couldn't check for updates."
private const val DOWNLOAD_FAILED = "Couldn't download the update."
private const val NOT_GENUINE = "The update wasn't installed because it isn't signed by this app's developer."
private const val BAD_MANIFEST = "The update server sent a release this app can't accept."
private const val INSTALL_FAILED = "Couldn't start installing the update."

/**
 * Decides when to check for a new version, and holds a verified download until it can be installed. Installing ends the process,
 * which closes every terminal and port forward, so it only happens when the user restarts or is already quitting.
 */
internal class AppUpdaterImpl(
    private val engine: UpdateEngine,
    private val verifier: UpdateVerifier,
    private val preferences: CorePreferencesRepository,
    private val vaultManager: VaultManager,
    private val tabManager: TabManager,
    private val portForwarding: () -> PortForwardingService,
    private val now: () -> Long = System::currentTimeMillis,
) : AppUpdater {
    private class Downloaded(val version: String, val file: File)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val busy = Mutex()
    private val mutableState = MutableStateFlow(if (engine.isSupported) AppUpdateState.Idle else AppUpdateState.Unsupported)

    @Volatile
    private var autoUpdate = false

    @Volatile
    private var downloaded: Downloaded? = null

    @Volatile
    private var available: UpdateInfo? = null
    private var lastAttempt = 0L

    override val currentVersion get() = engine.currentVersion
    override val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    /** Checks whenever one is due, for as long as updates are on. Time is measured by the clock, so sleep doesn't stretch it. */
    suspend fun run() {
        if (!engine.isSupported) return
        combine(preferences.getAutoUpdate(), preferences.getUpdateCheckIntervalHours(), ::Pair)
            .collectLatest { (enabled, hours) ->
                autoUpdate = enabled
                if (!enabled) return@collectLatest
                delay(FIRST_CHECK_DELAY_MILLIS)
                while (true) {
                    tick(hours)
                    delay(TICK_MILLIS)
                }
            }
    }

    internal suspend fun tick(intervalHours: Int) {
        val time = now()
        val sinceCheck = time - (preferences.getLastUpdateCheck().first() ?: 0L)
        // Negative when the clock was moved back, which shouldn't stop checks until it catches up.
        val notDue = sinceCheck in 0 until intervalHours.coerceAtLeast(1) * MILLIS_PER_HOUR
        if (notDue || time - lastAttempt < RETRY_MILLIS) return
        lastAttempt = time
        checkNow(download = true)
    }

    override fun check() {
        scope.launch { checkNow(download = preferences.getAutoUpdate().first()) }
    }

    override fun download() {
        val info = available ?: return
        scope.launch { exclusively { downloadAndVerify(info) } }
    }

    override fun openSessionCount(): Int =
        tabManager.openTabs().size + portForwarding().states.value.values.count { it !is PortForwardState.Failed }

    override fun restartToInstall() {
        val update = downloaded ?: return
        scope.launch {
            try {
                // Not cancelled by the lock, which clears the ViewModel that asked for this.
                try {
                    vaultManager.lock()
                } finally {
                    install(update, restart = true)
                }
            } catch (_: IOException) {
                mutableState.value = AppUpdateState.Failed(INSTALL_FAILED)
            } catch (_: IllegalStateException) {
                mutableState.value = AppUpdateState.Failed(INSTALL_FAILED)
            }
        }
    }

    override fun installOnQuit() {
        val update = downloaded ?: return
        if (!autoUpdate) return
        try {
            install(update, restart = false)
        } catch (_: IOException) {
            // It didn't start, so the app quits as normal and downloads the update again next time.
        } catch (_: IllegalStateException) {
            // As above.
        }
    }

    private fun install(update: Downloaded, restart: Boolean) {
        // Temp folders get cleaned, and this can run days after the download.
        if (!update.file.isFile) {
            forgetDownload()
            mutableState.value = AppUpdateState.Idle
            return
        }
        engine.install(update.file, restart)
    }

    internal suspend fun checkNow(download: Boolean) = exclusively {
        mutableState.value = AppUpdateState.Checking
        when (val result = engine.check()) {
            UpdateResult.NotAvailable -> {
                // Includes a release withdrawn after it was downloaded, which mustn't be installed any more.
                forgetDownload()
                available = null
                mutableState.value = AppUpdateState.UpToDate
                recordCheck()
            }
            is UpdateResult.Available -> onAvailable(result.info, download)
            is UpdateResult.Error -> mutableState.value = settled() ?: AppUpdateState.Failed(CHECK_FAILED)
        }
    }

    private suspend fun onAvailable(info: UpdateInfo, download: Boolean) {
        when {
            !SAFE_VERSION.matches(info.version) || !SAFE_ARTIFACT_NAME.matches(info.currentFile.fileName) -> {
                mutableState.value = settled() ?: AppUpdateState.Failed(BAD_MANIFEST)
                recordCheck()
            }
            downloaded?.version == info.version -> {
                mutableState.value = AppUpdateState.Ready(info.version)
                recordCheck()
            }
            download -> downloadAndVerify(info)
            else -> {
                available = info
                mutableState.value = settled() ?: AppUpdateState.Available(info.version)
                recordCheck()
            }
        }
    }

    private suspend fun downloadAndVerify(info: UpdateInfo) {
        mutableState.value = AppUpdateState.Downloading(info.version, 0)
        // First, so a release without one doesn't cost a whole download every time.
        val signature = engine.signatureFor(info)
        if (signature == null) {
            rejectRelease()
            return
        }
        var file: File? = null
        engine.download(info).collect { progress ->
            if (progress.bytesDownloaded > MAX_ARTIFACT_BYTES) throw IOException("The update is too large")
            mutableState.value = AppUpdateState.Downloading(info.version, progress.percent.toInt())
            progress.file?.let { file = it }
        }
        val artifact = file ?: throw IOException("The download finished without a file")
        if (!withContext(Dispatchers.IO) { verifier.isValid(artifact, info.version, signature) }) {
            artifact.parentFile?.deleteRecursively()
            rejectRelease()
            return
        }
        forgetDownload()
        downloaded = Downloaded(info.version, artifact)
        available = null
        mutableState.value = AppUpdateState.Ready(info.version)
        recordCheck()
    }

    /** It counts as a check, so a bad release is only looked at again next interval and not downloaded again every hour. */
    private suspend fun rejectRelease() {
        mutableState.value = settled() ?: AppUpdateState.Failed(NOT_GENUINE)
        recordCheck()
    }

    private suspend fun recordCheck() = preferences.setLastUpdateCheck(now())

    /** What to show instead of a failure when an earlier update is still waiting to be installed. */
    private fun settled() = downloaded?.let { AppUpdateState.Ready(it.version) }

    private fun forgetDownload() {
        downloaded?.file?.parentFile?.deleteRecursively()
        downloaded = null
    }

    /** One check or download at a time, so a click during a scheduled check does nothing. */
    private suspend fun exclusively(block: suspend () -> Unit) {
        if (!busy.tryLock()) return
        try {
            block()
        } catch (e: CancellationException) {
            // Turning updates off stops this mid-way.
            mutableState.value = settled() ?: AppUpdateState.Idle
            throw e
        } catch (_: UpdateException) {
            mutableState.value = settled() ?: AppUpdateState.Failed(DOWNLOAD_FAILED)
        } catch (_: IOException) {
            mutableState.value = settled() ?: AppUpdateState.Failed(DOWNLOAD_FAILED)
        } finally {
            busy.unlock()
        }
    }
}
