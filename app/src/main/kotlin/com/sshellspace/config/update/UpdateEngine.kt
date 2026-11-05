package com.sshellspace.config.update

import dev.nucleusframework.updater.DownloadProgress
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateResult
import dev.nucleusframework.updater.UpdaterConfig
import dev.nucleusframework.updater.provider.GitHubProvider
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val RELEASES_OWNER = "Arshia-Rahimi"
private const val RELEASES_REPO = "SSHellSpace"
private const val HTTP_OK = 200
private const val MAX_SIGNATURE_BYTES = 1024
private val PRERELEASE_CHANNEL = Regex("""^[^-]+-(alpha|beta)""", RegexOption.IGNORE_CASE)

/** A beta build keeps following beta, and so on. Everything else is on the stable channel. */
internal fun updateChannelFor(version: String): String =
    PRERELEASE_CHANNEL.find(version)?.groupValues?.get(1)?.lowercase() ?: "latest"

/** The part of updating that talks to Nucleus and the network. */
internal interface UpdateEngine {
    val currentVersion: String
    val isSupported: Boolean

    suspend fun check(): UpdateResult
    fun download(info: UpdateInfo): Flow<DownloadProgress>

    /** The `.sig` file beside the release's artifact, or null if it has none. */
    suspend fun signatureFor(info: UpdateInfo): ByteArray?

    /** Starts installing [file], then exits the process. */
    fun install(file: File, restart: Boolean)
}

internal class NucleusUpdateEngine(
    configure: UpdaterConfig.() -> Unit = { provider = GitHubProvider(RELEASES_OWNER, RELEASES_REPO) },
) : UpdateEngine {
    private val updater = NucleusUpdater {
        configure()
        channel = updateChannelFor(currentVersion)
    }
    private val http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    override val currentVersion get() = updater.currentVersion
    override val isSupported get() = updater.isUpdateSupported()

    override suspend fun check() = updater.checkForUpdates()

    override fun download(info: UpdateInfo) = updater.downloadUpdate(info)

    override suspend fun signatureFor(info: UpdateInfo): ByteArray? = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI.create("${info.currentFile.url}.sig"))
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        // The host is untrusted, so only as much is read as a signature could need.
        response.body().use { body -> if (response.statusCode() == HTTP_OK) body.readNBytes(MAX_SIGNATURE_BYTES) else null }
    }

    override fun install(file: File, restart: Boolean) {
        if (restart) updater.installAndRestart(file) else updater.installAndQuit(file)
    }
}
