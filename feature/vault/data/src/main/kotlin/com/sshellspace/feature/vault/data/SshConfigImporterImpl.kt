package com.sshellspace.feature.vault.data

import com.sshellspace.core.domain.models.SshConnectionAuth
import com.sshellspace.core.domain.models.SshConnectionOptions
import com.sshellspace.feature.vault.domain.SaveSshConnectionResult
import com.sshellspace.feature.vault.domain.SshConfigHost
import com.sshellspace.feature.vault.domain.SshConfigImportResult
import com.sshellspace.feature.vault.domain.SshConfigImporter
import com.sshellspace.feature.vault.domain.SshConnectionDraft
import com.sshellspace.feature.vault.domain.SshConnectionRepository
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val IMPORT_TAG = "ssh-config"

class SshConfigImporterImpl(
    private val connectionRepository: SshConnectionRepository,
    private val sshDirectory: Path = Path.of(System.getProperty("user.home"), ".ssh"),
    private val defaultUser: String = System.getProperty("user.name").orEmpty(),
    private val hasAgent: () -> Boolean = { !System.getenv("SSH_AUTH_SOCK").isNullOrBlank() },
) : SshConfigImporter {

    override suspend fun preview(): List<SshConfigHost> = withContext(Dispatchers.IO) {
        val config = sshDirectory.resolve("config")
        if (!Files.isRegularFile(config)) return@withContext emptyList()
        val saved = connectionRepository.getConnections().first().map { it.name.lowercase() }.toSet()
        parseSshConfig(config.readText(), ::readInclude).map { it.copy(alreadySaved = it.alias.lowercase() in saved) }
    }

    /** Relative `Include` paths are relative to `~/.ssh`, and the last path segment may be a wildcard. */
    private fun readInclude(pattern: String): List<String> = try {
        val expanded = if (pattern.startsWith("~/")) sshDirectory.parent.resolve(pattern.removePrefix("~/")) else sshDirectory.resolve(pattern)
        val matcher = FileSystems.getDefault().getPathMatcher("glob:${expanded.name}")
        expanded.parent.listDirectoryEntries()
            .filter { it.isRegularFile() && matcher.matches(it.fileName) }
            .sortedBy { it.name }
            .map { it.readText() }
    } catch (_: IOException) {
        emptyList()
    }

    override suspend fun import(hosts: List<SshConfigHost>): SshConfigImportResult {
        val ids = connectionRepository.getConnections().first().associate { it.name.lowercase() to it.id }.toMutableMap()
        val byAlias = hosts.associateBy { it.alias.lowercase() }
        val problems = mutableListOf<String>()
        var imported = 0

        // Jump hosts first, since a connection stores the id of the host it goes through.
        val ordered = mutableListOf<SshConfigHost>()
        val visited = mutableSetOf<String>()
        fun visit(host: SshConfigHost) {
            if (!visited.add(host.alias.lowercase())) return
            jumpAlias(host)?.let { byAlias[it.lowercase()] }?.let(::visit)
            ordered += host
        }
        hosts.forEach(::visit)

        ordered.forEach { host ->
            if (host.alias.lowercase() in ids) {
                problems += "${host.alias} already exists, so it was skipped."
                return@forEach
            }
            val jump = jumpAlias(host)
            val jumpId = jump?.let { ids[it.lowercase()] }
            if (jump != null && jumpId == null) {
                problems += "${host.alias} goes through $jump, which wasn't imported, so it will connect directly."
            }
            when (val result = connectionRepository.save(null, host.toDraft(jumpId))) {
                is SaveSshConnectionResult.Success -> {
                    ids[host.alias.lowercase()] = result.connection.id
                    imported++
                }
                is SaveSshConnectionResult.Error -> problems += "${host.alias}: ${result.message}"
            }
        }
        return SshConfigImportResult(imported, problems)
    }

    /** With several hops, `a,b`, the last is the one this host connects through. */
    private fun jumpAlias(host: SshConfigHost) = host.proxyJump?.split(",")?.last()?.trim()
        ?.substringAfterLast("@")?.substringBefore(":")?.takeIf { it.isNotEmpty() }

    private fun SshConfigHost.toDraft(jumpHostId: Uuid?) = SshConnectionDraft(
        name = alias,
        host = hostName,
        port = port,
        username = user?.takeIf { it.isNotBlank() } ?: defaultUser,
        auth = identityFile?.let { SshConnectionAuth.KeyFile(it) }
            ?: if (hasAgent()) SshConnectionAuth.Agent else SshConnectionAuth.Password(null),
        options = SshConnectionOptions().let { defaults ->
            defaults.copy(
                jumpHostId = jumpHostId,
                compression = compression,
                keepAliveIntervalSeconds = keepAliveSeconds ?: defaults.keepAliveIntervalSeconds,
                connectTimeoutSeconds = connectTimeoutSeconds ?: defaults.connectTimeoutSeconds,
                tags = listOf(IMPORT_TAG),
            )
        },
    )
}
