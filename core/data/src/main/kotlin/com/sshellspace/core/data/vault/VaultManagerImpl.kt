package com.sshellspace.core.data.vault

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.core.common.restrictToOwner
import com.sshellspace.core.data.generateUuid
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.domain.models.VAULT_FILE_EXTENSION
import com.sshellspace.core.domain.models.VaultCredential
import com.sshellspace.core.domain.models.VaultException
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.VaultManager
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.sql.SQLException
import java.util.Properties
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.sqlite.SQLiteConnection

private const val SCHEMA_NAME = "main"
private val VAULT_TABLES = listOf("ssh_connections", "device_ssh_keys", "vault_ssh_keys", "port_forwards")

/**
 * Keeps each vault in one encrypted file. An unlocked vault's database exists only in memory and every write re-encrypts
 * it to disk, so plaintext never reaches the file system. That costs a full rewrite per write, which is fine for the few
 * kilobytes a vault holds.
 *
 * @param directory where the vault files live.
 * @param unencryptedDriver the app database, which held vault data before vaults existed.
 * @param vaultDriver the driver the vault datasources read through.
 */
class VaultManagerImpl(
    private val directory: File,
    private val unencryptedDriver: JdbcDriver,
    private val vaultDriver: VaultSqlDriver,
) : VaultManager {
    private val mutex = Mutex()
    private val beforeLockActions = mutableListOf<suspend () -> Unit>()
    private var open: OpenVault? = null

    private val _vaults = MutableStateFlow(readVaults())
    override val vaults: StateFlow<List<VaultInfo>> = _vaults.asStateFlow()

    private val _activeVault = MutableStateFlow<VaultInfo?>(null)
    override val activeVault: StateFlow<VaultInfo?> = _activeVault.asStateFlow()

    override suspend fun unencryptedItemCount(): Long = withContext(Dispatchers.IO) {
        VAULT_TABLES.sumOf { table ->
            unencryptedDriver.executeQuery(
                identifier = null,
                sql = "SELECT COUNT(*) FROM $table",
                mapper = { cursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getLong(0) ?: 0L)
                },
                parameters = 0,
            ).value
        }
    }

    override suspend fun create(
        name: String,
        credential: VaultCredential,
        moveUnencryptedData: Boolean,
    ): VaultInfo = locked {
        closeOpenVault()
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) throw VaultException.Invalid("The vault needs a name.")
        if (_vaults.value.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            throw VaultException.Invalid("A vault named \"$trimmedName\" already exists.")
        }

        val id = generateUuid()
        val key = newVaultKey()
        val header = VaultHeader(id.toString(), trimmedName, listOf(newKeySlot(id.toString(), credential, key)))
        val vault = OpenVault(VaultInfo(id, trimmedName), header, key, fileOf(id), inMemoryDriver())
        if (moveUnencryptedData) {
            vault.load(serializeUnencrypted())
            vault.driver.execute(null, "DELETE FROM preferences_table", 0)
        } else {
            Database.Schema.create(vault.driver).value
            vault.setSchemaVersion(Database.Schema.version)
        }
        vault.persist()

        _vaults.update { (it + vault.info).sortedBy(VaultInfo::name) }
        if (moveUnencryptedData) {
            // Reading the file back proves the data is safe before the only other copy goes.
            VaultFile.parse(Files.readAllBytes(vault.file)).decrypt(key)
            eraseUnencryptedData()
        }
        activate(vault)
        vault.info
    }

    override suspend fun unlock(id: Uuid, credential: VaultCredential) = locked {
        closeOpenVault()
        val parsed = VaultFile.read(fileOf(id))
        // Key slots are tied to the id in the header, so this catches a file swapped for another vault's.
        if (parsed.header.id != id.toString()) throw VaultException.Invalid("The vault file is damaged or was modified.")
        val key = unwrapKey(parsed.header, credential)
        val plaintext = parsed.decrypt(key)
        val vault = OpenVault(VaultInfo(id, parsed.header.name), parsed.header, key, fileOf(id), inMemoryDriver())
        try {
            vault.load(plaintext)
        } catch (e: VaultException) {
            vault.close(save = false)
            throw e
        } finally {
            plaintext.fill(0)
        }
        activate(vault)
    }

    override suspend fun lock() = locked { closeOpenVault() }

    override fun beforeLock(action: suspend () -> Unit) {
        beforeLockActions += action
    }

    override suspend fun export(id: Uuid, destination: String) = locked {
        val target = Path.of(destination)
        Files.copy(fileOf(id), target, StandardCopyOption.REPLACE_EXISTING)
        restrictToOwner(target)
    }

    override suspend fun import(source: String, credential: VaultCredential): VaultInfo = locked {
        val parsed = VaultFile.read(Path.of(source))
        val key = unwrapKey(parsed.header, credential)
        val plaintext = parsed.decrypt(key)
        // The password proves the file opens, not that whoever made it can be trusted, so check its database now.
        checkImage(plaintext)
        val sourceId = runCatching { Uuid.parse(parsed.header.id) }.getOrNull()
            ?: throw VaultException.Invalid("The vault file is damaged.")

        // The id and name are only local identities, so a copy of a vault already here is added next to the original.
        // Looks at the disk too: a damaged vault is left out of the list, and must not be overwritten.
        val idTaken = _vaults.value.any { it.id == sourceId } || Files.exists(fileOf(sourceId))
        val id = if (idTaken) generateUuid() else sourceId
        val name = uniqueName(parsed.header.name)
        val header = parsed.header.copy(
            id = id.toString(),
            name = name,
            slots = if (idTaken) listOf(newKeySlot(id.toString(), credential, key)) else parsed.header.slots,
        )
        writeAtomically(fileOf(id), VaultFile.seal(header, key, plaintext))
        plaintext.fill(0)
        key.fill(0)

        VaultInfo(id, name).also { info -> _vaults.update { (it + info).sortedBy(VaultInfo::name) } }
    }

    override suspend fun changeCredential(id: Uuid, current: VaultCredential, new: VaultCredential) = locked {
        if (open?.info?.id == id) throw VaultException.Invalid("Lock the vault before changing its password.")
        val parsed = VaultFile.read(fileOf(id))
        if (parsed.header.id != id.toString()) throw VaultException.Invalid("The vault file is damaged or was modified.")
        val oldKey = unwrapKey(parsed.header, current)
        val plaintext = parsed.decrypt(oldKey)
        val newKey = newVaultKey()
        val header = parsed.header.copy(slots = listOf(newKeySlot(id.toString(), new, newKey)))
        writeAtomically(fileOf(id), VaultFile.seal(header, newKey, plaintext))
        plaintext.fill(0)
        oldKey.fill(0)
        newKey.fill(0)
    }

    override suspend fun delete(id: Uuid) = locked {
        if (open?.info?.id == id) closeOpenVault()
        Files.deleteIfExists(fileOf(id))
        _vaults.update { vaults -> vaults.filterNot { it.id == id } }
    }

    private suspend fun <T> locked(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { block() } }

    private fun activate(vault: OpenVault) {
        open = vault
        vaultDriver.attach(vault.driver, vault::persist)
        _activeVault.value = vault.info
    }

    private suspend fun closeOpenVault() {
        val vault = open ?: return
        // Not cancellable: the actions clear ViewModels, which can cancel the very coroutine that asked to lock, and a
        // lock stopped halfway would look locked while the key and the data stay in memory.
        withContext(NonCancellable) {
            // Screens leave first, so nothing is left reading the vault while the actions shut down what uses it.
            _activeVault.value = null
            beforeLockActions.forEach { it() }
            vaultDriver.detach()
            vault.close(save = true)
            open = null
        }
    }

    private fun readVaults(): List<VaultInfo> {
        directory.mkdirs()
        restrictToOwner(directory.toPath())
        val files = directory.listFiles { file -> file.extension == VAULT_FILE_EXTENSION }.orEmpty()
        return files.mapNotNull { file ->
            // A file that can't be read is skipped rather than deleted: it may still be recoverable by hand.
            runCatching {
                val header = VaultFile.read(file.toPath()).header
                VaultInfo(Uuid.parse(header.id), header.name)
            }.getOrNull()
        }.sortedBy(VaultInfo::name)
    }

    private fun uniqueName(name: String): String {
        val taken = _vaults.value.map { it.name.lowercase() }.toSet()
        return generateSequence(1) { it + 1 }
            .map { if (it == 1) name else "$name ($it)" }
            .first { it.lowercase() !in taken }
    }

    private fun fileOf(id: Uuid): Path = directory.toPath().resolve("$id.$VAULT_FILE_EXTENSION")

    private fun inMemoryDriver() = JdbcSqliteDriver(
        JdbcSqliteDriver.IN_MEMORY,
        Properties().apply { put("foreign_keys", "true") },
    )

    private fun checkImage(image: ByteArray) {
        val driver = inMemoryDriver()
        try {
            loadImage(driver, driver.getConnection().unwrap(SQLiteConnection::class.java), image)
        } finally {
            driver.close()
        }
    }

    private fun serializeUnencrypted(): ByteArray {
        val connection = unencryptedDriver.getConnection()
        return try {
            connection.unwrap(SQLiteConnection::class.java).serialize(SCHEMA_NAME)
        } finally {
            unencryptedDriver.closeConnection(connection)
        }
    }

    private fun eraseUnencryptedData() {
        try {
            VAULT_TABLES.forEach { unencryptedDriver.execute(null, "DELETE FROM $it", 0) }
            // Deleted rows stay readable in free pages until the file is rebuilt.
            unencryptedDriver.execute(null, "VACUUM", 0)
        } catch (e: SQLException) {
            throw VaultException.Invalid(
                "The vault was created, but the old unencrypted data couldn't be erased: ${e.message}",
            )
        }
    }

    private class OpenVault(
        val info: VaultInfo,
        val header: VaultHeader,
        val key: ByteArray,
        val file: Path,
        val driver: JdbcSqliteDriver,
    ) {
        private val connection = driver.getConnection().unwrap(SQLiteConnection::class.java)

        /** Replaces the in-memory database with [image], bringing it up to the current schema. */
        fun load(image: ByteArray) {
            loadImage(driver, connection, image)
            val version = driver.executeQuery(
                identifier = null,
                sql = "PRAGMA user_version",
                mapper = { cursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getLong(0) ?: 0L)
                },
                parameters = 0,
            ).value
            val current = Database.Schema.version
            if (version > current) throw VaultException.Invalid("This vault was made by a newer version of the app.")
            if (version < current) {
                Database.Schema.migrate(driver, version, current).value
                setSchemaVersion(current)
                persist()
            }
        }

        fun setSchemaVersion(version: Long) {
            driver.execute(null, "PRAGMA user_version = $version", 0)
        }

        private var closed = false

        @Synchronized
        fun persist() {
            // A write can still be finishing as the vault locks, and the key below has been wiped by then.
            if (closed) return
            val plaintext = connection.serialize(SCHEMA_NAME)
            try {
                writeAtomically(file, VaultFile.seal(header, key, plaintext))
            } finally {
                plaintext.fill(0)
            }
        }

        /** Wipes the key. With [save], first writes the database once more, so a write racing the lock isn't lost. */
        @Synchronized
        fun close(save: Boolean) {
            if (closed) return
            if (save) persist()
            closed = true
            driver.close()
            key.fill(0)
        }
    }
}

/**
 * Loads [image] into [driver] and refuses what the app's own schema never contains. A vault someone else exported can
 * hold any SQLite database, and triggers or views would run their own SQL whenever the app touches those tables.
 */
private fun loadImage(driver: JdbcSqliteDriver, connection: SQLiteConnection, image: ByteArray) {
    try {
        connection.deserialize(SCHEMA_NAME, image)
        driver.execute(null, "PRAGMA trusted_schema = OFF", 0)
        val integrity = driver.executeQuery(
            identifier = null,
            sql = "PRAGMA integrity_check(1)",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getString(0))
            },
            parameters = 0,
        ).value
        val unexpected = driver.executeQuery(
            identifier = null,
            sql = "SELECT COUNT(*) FROM sqlite_master WHERE type IN ('trigger', 'view')",
            mapper = { cursor ->
                cursor.next()
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 0,
        ).value
        if (integrity != "ok" || unexpected > 0) {
            throw VaultException.Invalid("This vault's database is damaged or contains something unexpected.")
        }
    } catch (_: SQLException) {
        throw VaultException.Invalid("This vault's database is damaged or contains something unexpected.")
    }
}

/** Replaces [target] only once the new contents are fully on disk, so a crash can't leave a half-written vault. */
private fun writeAtomically(target: Path, bytes: ByteArray) {
    // Temp files are created readable by the owner only, and the move keeps that.
    val temp = Files.createTempFile(target.parent, ".vault", ".tmp")
    try {
        restrictToOwner(temp)
        FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
            channel.write(java.nio.ByteBuffer.wrap(bytes))
            channel.force(true)
        }
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } finally {
        Files.deleteIfExists(temp)
    }
}
