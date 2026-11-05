package com.sshellspace.feature.vault.data

import com.sshellspace.core.data.DefaultKnownHostsPath
import com.sshellspace.core.data.KnownHostDatasource
import com.sshellspace.core.data.hasUsableKey
import com.sshellspace.core.data.parseKnownHostsText
import com.sshellspace.core.data.parseVaultKnownHostLine
import com.sshellspace.core.data.trustsManyHosts
import com.sshellspace.core.domain.models.KnownHost
import com.sshellspace.feature.vault.domain.DeviceKnownHosts
import com.sshellspace.feature.vault.domain.KnownHostExportResult
import com.sshellspace.feature.vault.domain.KnownHostImportResult
import com.sshellspace.feature.vault.domain.KnownHostRepository
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Far more than a real file holds, so that picking the wrong file can't use up this app's memory. */
private const val MAX_FILE_BYTES = 8L * 1024 * 1024

class KnownHostRepositoryImpl(
    private val datasource: KnownHostDatasource,
    private val deviceFile: Path = DefaultKnownHostsPath,
) : KnownHostRepository {

    override fun getVaultHosts(): Flow<List<KnownHost>> =
        datasource.index().map { lines -> lines.mapNotNull(::parseVaultKnownHostLine) }

    override suspend fun getDeviceHosts(): DeviceKnownHosts = withContext(Dispatchers.IO) {
        val path = deviceFile.toString()
        try {
            if (Files.exists(deviceFile)) DeviceKnownHosts(path, parseKnownHostsText(readText(deviceFile)).hosts) else DeviceKnownHosts(path)
        } catch (e: IOException) {
            DeviceKnownHosts(path, error = "Couldn't read this file: ${e.message}")
        }
    }

    override suspend fun removeFromVault(host: KnownHost) = datasource.delete(host.line)

    override suspend fun importToVault(path: String): KnownHostImportResult = withContext(Dispatchers.IO) {
        val parsed = try {
            parseKnownHostsText(readText(Path.of(path)))
        } catch (e: IOException) {
            return@withContext KnownHostImportResult.Error("Couldn't read that file: ${e.message}")
        } catch (_: InvalidPathException) {
            return@withContext KnownHostImportResult.Error("Couldn't read that file.")
        }

        val existing = datasource.lines().mapNotNull(::parseVaultKnownHostLine).toMutableList()
        val added = mutableListOf<KnownHost>()
        var alreadyKnown = 0
        var conflicting = 0
        var notAllowed = 0
        var unreadable = parsed.unreadable
        parsed.hosts.forEach { host ->
            when {
                host.trustsManyHosts -> notAllowed++
                !host.hasUsableKey -> unreadable++
                existing.any { it.line == host.line } -> alreadyKnown++
                // Taking it would trust both keys, and a file shouldn't be able to add a second key behind the user's back.
                existing.any { it.contradicts(host) } -> conflicting++
                else -> {
                    added += host
                    existing += host
                }
            }
        }
        datasource.add(added.map { it.line })
        KnownHostImportResult.Success(added.size, alreadyKnown, conflicting, notAllowed, unreadable)
    }

    private fun KnownHost.contradicts(other: KnownHost) = !isRevoked && !other.isRevoked && keyType == other.keyType &&
        fingerprint != other.fingerprint && hosts.any { it in other.hosts }

    override suspend fun exportVault(path: String): KnownHostExportResult = withContext(Dispatchers.IO) {
        val lines = datasource.lines().filter { parseVaultKnownHostLine(it) != null }
        try {
            val target = Path.of(path)
            Files.writeString(target, lines.joinToString(separator = "\n", postfix = "\n"))
            // The mode OpenSSH gives this file. Its host names say what servers there are.
            try {
                Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"))
            } catch (_: UnsupportedOperationException) {
                // Windows has no POSIX modes.
            }
            KnownHostExportResult.Success(lines.size)
        } catch (e: IOException) {
            KnownHostExportResult.Error("Couldn't write that file: ${e.message}")
        } catch (_: InvalidPathException) {
            KnownHostExportResult.Error("Couldn't write that file.")
        }
    }

    private fun readText(file: Path): String {
        if (!Files.isRegularFile(file)) throw IOException("It isn't a regular file.")
        if (Files.size(file) > MAX_FILE_BYTES) throw IOException("It's too large to be a known hosts file.")
        return String(Files.readAllBytes(file), Charsets.UTF_8)
    }
}
