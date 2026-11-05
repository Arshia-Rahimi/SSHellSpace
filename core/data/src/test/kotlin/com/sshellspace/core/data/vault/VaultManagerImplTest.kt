package com.sshellspace.core.data.vault

import app.cash.sqldelight.EnumColumnAdapter
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.sshellspace.core.data.generated.Database
import com.sshellspace.core.data.generated.Device_ssh_keys
import com.sshellspace.core.data.generated.Port_forwards
import com.sshellspace.core.data.generated.Vault_ssh_keys
import com.sshellspace.core.data.impl.SqlDelightPreferencesDatasource
import com.sshellspace.core.data.impl.VaultPreferencesRepositoryImpl
import com.sshellspace.core.domain.models.DEFAULT_AUTO_LOCK_MINUTES
import com.sshellspace.core.domain.models.PasswordStrength
import com.sshellspace.core.domain.models.VAULT_FILE_EXTENSION
import com.sshellspace.core.domain.models.VaultCredential
import com.sshellspace.core.domain.models.VaultException
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.models.vaultPasswordStrength
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.PreferencesDatasource
import com.sshellspace.core.domain.repositories.VaultPreferencesDatasource
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.sqlite.SQLiteConnection

private const val CRAFTED_VAULT_ID = "018f0000-0000-7000-8000-000000000000"

class VaultManagerImplTest {

    private val root = Files.createTempDirectory("vaults-test").toFile()
    private val appDatabaseFile = File(root, "app.db")
    private val vaultDirectory = File(root, "vaults")
    private val appDriver = JdbcSqliteDriver(
        url = "jdbc:sqlite:${appDatabaseFile.absolutePath}",
        schema = Database.Schema,
        properties = Properties(),
    )
    private val vaultDriver = VaultSqlDriver()
    private val appDatabase = database(appDriver)
    private val vaultDatabase = database(vaultDriver)
    private val manager = manager()

    private val password = VaultCredential.Password("correct horse")
    private val wrongPassword = VaultCredential.Password("wrong password")

    @AfterTest
    fun tearDown() {
        appDriver.close()
        root.deleteRecursively()
    }

    private fun manager() = VaultManagerImpl(vaultDirectory, appDriver, vaultDriver)

    private fun database(driver: app.cash.sqldelight.db.SqlDriver) = Database(
        driver = driver,
        device_ssh_keysAdapter = Device_ssh_keys.Adapter(EnumColumnAdapter()),
        vault_ssh_keysAdapter = Vault_ssh_keys.Adapter(EnumColumnAdapter()),
        port_forwardsAdapter = Port_forwards.Adapter(EnumColumnAdapter()),
    )

    private fun insertConnection(database: Database, id: String, name: String) {
        database.ssh_connectionQueries.upsert(
            com.sshellspace.core.data.generated.Ssh_connections(
                id = id,
                name = name,
                host = "example.com",
                port = 22,
                username = "root",
                auth = "SECRET-AUTH-VALUE",
                options = "{}",
            ),
        )
    }

    private fun connectionNames(database: Database) =
        database.ssh_connectionQueries.index().executeAsList().map { it.name }

    private fun vaultFile(info: com.sshellspace.core.domain.models.VaultInfo) =
        File(vaultDirectory, "${info.id}.sshv")

    @Test
    fun `a new vault is unlocked and its writes survive locking`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        assertEquals(vault, manager.activeVault.value)

        insertConnection(vaultDatabase, "1", "server")
        manager.lock()
        assertNull(manager.activeVault.value)

        manager.unlock(vault.id, password)
        assertEquals(listOf("server"), connectionNames(vaultDatabase))
    }

    @Test
    fun `vault data can't be read while locked`() = runTest {
        manager.create("Work", password, moveUnencryptedData = false)
        manager.lock()

        assertFailsWith<IllegalStateException> { connectionNames(vaultDatabase) }
    }

    @Test
    fun `a saved vault is found by a new manager after a restart`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "server")
        manager.lock()

        val restarted = manager()

        assertEquals(listOf(vault), restarted.vaults.value)
        restarted.unlock(vault.id, password)
        assertEquals(listOf("server"), connectionNames(vaultDatabase))
    }

    @Test
    fun `the wrong password doesn't unlock`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        manager.lock()

        assertFailsWith<VaultException.WrongCredential> { manager.unlock(vault.id, wrongPassword) }
        assertNull(manager.activeVault.value)
    }

    @Test
    fun `nothing readable reaches the vault file`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "server")
        manager.lock()

        val contents = vaultFile(vault).readBytes().toString(Charsets.ISO_8859_1)

        assertFalse("SECRET-AUTH-VALUE" in contents)
        assertFalse("ssh_connections" in contents)
        assertFalse("SQLite format" in contents)
    }

    @Test
    fun `vaults are isolated from each other`() = runTest {
        val work = manager.create("Work", password, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "work-server")
        val home = manager.create("Home", password, moveUnencryptedData = false)

        assertEquals(emptyList(), connectionNames(vaultDatabase))
        insertConnection(vaultDatabase, "2", "home-server")

        manager.unlock(work.id, password)
        assertEquals(listOf("work-server"), connectionNames(vaultDatabase))
        manager.unlock(home.id, password)
        assertEquals(listOf("home-server"), connectionNames(vaultDatabase))
    }

    @Test
    fun `a flipped byte in the contents is detected`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        manager.lock()
        val file = vaultFile(vault)
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        file.writeBytes(bytes)

        assertFailsWith<VaultException.Invalid> { manager.unlock(vault.id, password) }
    }

    @Test
    fun `an edited header is detected`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        manager.lock()
        val file = vaultFile(vault)
        val edited = file.readBytes().toString(Charsets.ISO_8859_1).replace("Work", "Evil")
        file.writeBytes(edited.toByteArray(Charsets.ISO_8859_1))

        assertFailsWith<VaultException.Invalid> { manager.unlock(vault.id, password) }
    }

    @Test
    fun `a slot from another vault doesn't open this one`() = runTest {
        val first = manager.create("First", password, moveUnencryptedData = false)
        val second = manager.create("Second", password, moveUnencryptedData = false)
        manager.lock()
        val slots = VaultFile.parse(vaultFile(first).readBytes()).header.slots
        val secondFile = VaultFile.parse(vaultFile(second).readBytes())

        val forged = secondFile.header.copy(slots = slots)

        assertFailsWith<VaultException.WrongCredential> { unwrapKey(forged, password) }
    }

    @Test
    fun `absurd key costs in a file are rejected before deriving anything`() {
        val header = VaultHeader(
            id = "id",
            name = "x",
            slots = listOf(
                KeySlot.Password(
                    salt = "AAAA",
                    memoryKib = Int.MAX_VALUE,
                    iterations = 1,
                    parallelism = 1,
                    nonce = "AAAA",
                    wrappedKey = "AAAA",
                ),
            ),
        )

        assertFailsWith<VaultException.Invalid> { unwrapKey(header, password) }
    }

    @Test
    fun `an exported vault imports on another device with its data`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "server")
        val exported = File(root, "export.sshv")
        manager.export(vault.id, password, exported.path)
        manager.lock()

        val otherRoot = File(root, "other").apply { mkdirs() }
        val otherDriver = JdbcSqliteDriver(
            url = "jdbc:sqlite:${File(otherRoot, "app.db").path}",
            schema = Database.Schema,
            properties = Properties(),
        )
        val otherVaultDriver = VaultSqlDriver()
        val other = VaultManagerImpl(File(otherRoot, "vaults"), otherDriver, otherVaultDriver)

        val imported = other.import(exported.path, password)
        assertEquals(vault, imported)
        assertNull(other.activeVault.value)

        other.unlock(imported.id, password)
        assertEquals(listOf("server"), connectionNames(database(otherVaultDriver)))
        otherDriver.close()
    }

    @Test
    fun `import needs the right password`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        val exported = File(root, "export.sshv")
        manager.export(vault.id, password, exported.path)

        assertFailsWith<VaultException.WrongCredential> { manager.import(exported.path, wrongPassword) }
        assertEquals(1, manager.vaults.value.size)
    }

    @Test
    fun `importing a vault already here adds an independent copy`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "server")
        val exported = File(root, "export.sshv")
        manager.export(vault.id, password, exported.path)
        manager.lock()

        val copy = manager.import(exported.path, password)

        assertNotEquals(vault.id, copy.id)
        assertEquals("Work (2)", copy.name)
        manager.unlock(copy.id, password)
        assertEquals(listOf("server"), connectionNames(vaultDatabase))
        insertConnection(vaultDatabase, "2", "only-in-copy")
        manager.unlock(vault.id, password)
        assertEquals(listOf("server"), connectionNames(vaultDatabase))
    }

    @Test
    fun `importing something that isn't a vault fails cleanly`() = runTest {
        val file = File(root, "fake.sshv").apply { writeText("not a vault") }

        assertFailsWith<VaultException.Invalid> { manager.import(file.path, password) }
    }

    @Test
    fun `deleting removes the file and locks the vault`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        val file = vaultFile(vault)
        assertTrue(file.exists())

        manager.delete(vault.id, password)

        assertFalse(file.exists())
        assertNull(manager.activeVault.value)
        assertEquals(emptyList(), manager.vaults.value)
    }

    @Test
    fun `moving existing data empties the old database and keeps its preferences`() = runTest {
        insertConnection(appDatabase, "1", "old-server")
        appDatabase.preferencesQueries.upsert(name = "theme", value_ = "dark")
        assertEquals(1, manager.unencryptedItemCount())

        manager.create("Work", password, moveUnencryptedData = true)

        assertEquals(listOf("old-server"), connectionNames(vaultDatabase))
        assertEquals(emptyList(), vaultDatabase.preferencesQueries.index().executeAsList())
        assertEquals(0, manager.unencryptedItemCount())
        assertEquals(emptyList(), connectionNames(appDatabase))
        assertEquals("dark", appDatabase.preferencesQueries.index().executeAsList().single().value_)
        assertFalse("SECRET-AUTH-VALUE" in appDatabaseFile.readBytes().toString(Charsets.ISO_8859_1))
    }

    @Test
    fun `existing data stays where it is unless moving is chosen`() = runTest {
        insertConnection(appDatabase, "1", "old-server")

        manager.create("Work", password, moveUnencryptedData = false)

        assertEquals(emptyList(), connectionNames(vaultDatabase))
        assertEquals(1, manager.unencryptedItemCount())
    }

    @Test
    fun `before-lock actions run while the vault is still readable`() = runTest {
        manager.create("Work", password, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "server")
        var seen: List<String>? = null
        manager.beforeLock { seen = connectionNames(vaultDatabase) }

        manager.lock()

        assertEquals(listOf("server"), seen)
    }

    @Test
    fun `a lock finishes even when its action cancels the coroutine that asked for it`() = runTest {
        manager.create("Work", password, moveUnencryptedData = false)
        var locker: Job? = null
        manager.beforeLock {
            locker?.cancel()
            yield()
        }

        locker = launch(Dispatchers.Default, start = CoroutineStart.LAZY) { manager.lock() }
        locker.start()
        locker.join()

        assertNull(manager.activeVault.value)
        assertFailsWith<IllegalStateException> { connectionNames(vaultDatabase) }
    }

    @Test
    fun `writes racing a lock never leave the vault under a wiped key`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        val writing = AtomicBoolean(true)
        val writer = thread {
            var row = 0
            while (writing.get()) {
                runCatching { insertConnection(vaultDatabase, "row-${row++}", "server") }
            }
        }

        Thread.sleep(50)
        manager.lock()
        writing.set(false)
        writer.join()

        manager.unlock(vault.id, password)
        val parsed = VaultFile.parse(vaultFile(vault).readBytes())
        assertFailsWith<VaultException> { parsed.decrypt(ByteArray(32)) }
    }

    @Test
    fun `changing the password re-encrypts under a new key`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "server")
        manager.lock()
        val before = vaultFile(vault).readBytes()
        val oldKey = unwrapKey(VaultFile.parse(before).header, password)
        val newPassword = VaultCredential.Password("a different passphrase")

        manager.changeCredential(vault.id, password, newPassword)

        assertFailsWith<VaultException.WrongCredential> { manager.unlock(vault.id, password) }
        val after = VaultFile.parse(vaultFile(vault).readBytes())
        assertFailsWith<VaultException> { after.decrypt(oldKey) }
        manager.unlock(vault.id, newPassword)
        assertEquals(listOf("server"), connectionNames(vaultDatabase))
    }

    @Test
    fun `unlocking refuses a file swapped for another vault's`() = runTest {
        val first = manager.create("First", password, moveUnencryptedData = false)
        val second = manager.create("Second", password, moveUnencryptedData = false)
        manager.lock()
        vaultFile(second).writeBytes(vaultFile(first).readBytes())

        assertFailsWith<VaultException.Invalid> { manager.unlock(second.id, password) }
    }

    @Test
    fun `too many key slots are refused`() {
        val slot = KeySlot.Password("AAAA", 1, 1, 1, "AAAA", "AAAA")
        val header = VaultHeader("id", "x", List(9) { slot })

        assertFailsWith<VaultException.Invalid> { VaultFile.parse(VaultFile.seal(header, newVaultKey(), ByteArray(0))) }
    }

    @Test
    fun `a malformed slot is reported rather than crashing`() {
        val slot = KeySlot.Password("AAAA", 8, 1, 1, "", "AAAA")

        assertFailsWith<VaultException.Invalid> { unwrapKey(VaultHeader("id", "x", listOf(slot)), password) }
    }

    @Test
    fun `importing a vault whose database has a trigger is refused`() = runTest {
        val file = craftedVault { driver ->
            driver.execute(
                null,
                "CREATE TRIGGER evil AFTER INSERT ON ssh_connections BEGIN DELETE FROM ssh_connections; END",
                0,
            )
        }

        assertFailsWith<VaultException.Invalid> { manager.import(file.path, password) }
        assertEquals(emptyList(), manager.vaults.value)
    }

    @Test
    fun `importing refuses a trigger or view however its schema row spells the type`() = runTest {
        val objects = mapOf(
            "CREATE TRIGGER evil BEFORE DELETE ON preferences_table BEGIN SELECT RAISE(IGNORE); END" to
                listOf("'TRIGGER'", "'Trigger'", "CAST('trigger' AS BLOB)"),
            "CREATE VIEW evil AS SELECT 1" to listOf("'VIEW'", "CAST('view' AS BLOB)"),
        )
        objects.forEach { (sql, types) ->
            types.forEach { type ->
                val file = craftedVault { driver -> respellSchemaType(driver, sql, type) }

                assertFailsWith<VaultException.Invalid>("$sql as $type") { manager.import(file.path, password) }
            }
        }
        assertEquals(emptyList(), manager.vaults.value)
    }

    @Test
    fun `unlocking refuses a vault already here whose trigger hides behind its type's spelling`() = runTest {
        val file = craftedVault { driver ->
            respellSchemaType(
                driver,
                "CREATE TRIGGER evil AFTER INSERT ON ssh_connections BEGIN DELETE FROM ssh_connections; END",
                "'TRIGGER'",
            )
        }
        vaultDirectory.mkdirs()
        val id = Uuid.parse(CRAFTED_VAULT_ID)
        file.copyTo(File(vaultDirectory, "$id.$VAULT_FILE_EXTENSION"))

        assertFailsWith<VaultException.Invalid> { manager().unlock(id, password) }
    }

    private fun respellSchemaType(driver: JdbcSqliteDriver, sql: String, type: String) {
        driver.execute(null, sql, 0)
        driver.execute(null, "PRAGMA writable_schema = ON", 0)
        driver.execute(null, "UPDATE sqlite_master SET type = $type WHERE name = 'evil'", 0)
        driver.execute(null, "PRAGMA writable_schema = OFF", 0)
    }

    /** A sealed vault file, holding the app's schema with whatever [edit] does to it. */
    private fun craftedVault(edit: (JdbcSqliteDriver) -> Unit): File {
        val image = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            Database.Schema.create(driver).value
            driver.execute(null, "PRAGMA user_version = ${Database.Schema.version}", 0)
            edit(driver)
            driver.getConnection().unwrap(SQLiteConnection::class.java).serialize("main")
        }
        val key = newVaultKey()
        val slots = listOf(newKeySlot(CRAFTED_VAULT_ID, password, key))
        return File(root, "crafted.sshv").apply {
            writeBytes(VaultFile.seal(VaultHeader(CRAFTED_VAULT_ID, "Crafted", slots), key, image))
        }
    }

    @Test
    fun `importing never overwrites a vault file that isn't listed`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        val exported = File(root, "export.sshv")
        manager.export(vault.id, password, exported.path)
        manager.delete(vault.id, password)
        vaultDirectory.mkdirs()
        val damaged = vaultFile(vault).apply { writeText("damaged but maybe recoverable") }

        val imported = manager.import(exported.path, password)

        assertNotEquals(vault.id, imported.id)
        assertEquals("damaged but maybe recoverable", damaged.readText())
    }

    @Test
    fun `exports and the vault folder are owner-only`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        val exported = File(root, "export.sshv")

        manager.export(vault.id, password, exported.path)

        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(exported.toPath()))
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(vaultDirectory.toPath()))
    }

    @Test
    fun `password strength catches the obvious`() {
        assertEquals(PasswordStrength.Weak, vaultPasswordStrength("password1234"))
        assertEquals(PasswordStrength.Weak, vaultPasswordStrength("aaaaaaaaaaaaaaaa"))
        assertEquals(PasswordStrength.Fair, vaultPasswordStrength("correct horse"))
        assertEquals(PasswordStrength.Strong, vaultPasswordStrength("Tr0ub4dor&3-horse-staple"))
    }

    @Test
    fun `secrets don't appear in toString`() {
        assertFalse("correct" in password.toString())
    }

    private fun autoLockSettings() = VaultPreferencesRepositoryImpl(
        object : VaultPreferencesDatasource, PreferencesDatasource by SqlDelightPreferencesDatasource(vaultDatabase) {},
        manager,
        mockk<CorePreferencesRepository> { every { getAutoLockMinutes() } returns flowOf(DEFAULT_AUTO_LOCK_MINUTES) },
    )

    private suspend fun VaultPreferencesRepositoryImpl.awaitMinutes(minutes: Int) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { getAutoLockMinutes().first { it == minutes } }
    }

    @Test
    fun `each vault keeps its own idle timeout across locks`() = runTest {
        val settings = autoLockSettings()
        val work = manager.create("Work", password, moveUnencryptedData = false)
        settings.awaitMinutes(DEFAULT_AUTO_LOCK_MINUTES)
        settings.setAutoLockMinutes(5)
        settings.awaitMinutes(5)

        val home = manager.create("Home", password, moveUnencryptedData = false)
        settings.awaitMinutes(DEFAULT_AUTO_LOCK_MINUTES)
        settings.setAutoLockMinutes(0)
        settings.awaitMinutes(0)

        manager.lock()
        manager.unlock(work.id, password)
        settings.awaitMinutes(5)

        manager.unlock(home.id, password)
        settings.awaitMinutes(0)
    }

    @Test
    fun `an imported vault can't turn its own auto-lock off`() = runTest {
        val settings = autoLockSettings()
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        settings.setAutoLockMinutes(0)
        settings.awaitMinutes(0)
        val exported = File(root, "export.sshv").path
        manager.lock()
        manager.export(vault.id, password, exported)
        manager.delete(vault.id, password)

        val imported = manager.import(exported, password)
        manager.unlock(imported.id, password)

        settings.awaitMinutes(DEFAULT_AUTO_LOCK_MINUTES)
    }

    @Test
    fun `importing drops saved state, so a file can't choose what opens on unlock`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "server")
        vaultDatabase.preferencesQueries.upsert(name = "session", value_ = "{\"tabs\":[]}")
        val exported = File(root, "export.sshv")
        manager.export(vault.id, password, exported.path)
        manager.lock()

        val copy = manager.import(exported.path, password)
        manager.unlock(copy.id, password)

        assertEquals(emptyList(), vaultDatabase.preferencesQueries.index().executeAsList())
        assertEquals(listOf("server"), connectionNames(vaultDatabase))
    }

    @Test
    fun `a vault made before snippets existed migrates when it unlocks`() = runTest {
        val vault = importOldVault(version = 3, missingTables = listOf("snippets", "known_hosts"))
        manager.unlock(vault.id, password)

        assertEquals(listOf("old"), connectionNames(vaultDatabase))
        vaultDatabase.snippetQueries.upsert(com.sshellspace.core.data.generated.Snippets("s", "n", "ls", 0))
        assertEquals("n", vaultDatabase.snippetQueries.index().executeAsList().single().name)
        manager.lock()
        manager.unlock(vault.id, password)
        assertEquals(1, vaultDatabase.snippetQueries.index().executeAsList().size)
    }

    @Test
    fun `a vault made before its known hosts list existed migrates when it unlocks`() = runTest {
        val vault = importOldVault(version = 4, missingTables = listOf("known_hosts"))
        manager.unlock(vault.id, password)

        assertEquals(listOf("old"), connectionNames(vaultDatabase))
        vaultDatabase.known_hostQueries.insert("example.com ssh-ed25519 AAAA")
        assertEquals(1, vaultDatabase.known_hostQueries.index().executeAsList().size)
        manager.lock()
        manager.unlock(vault.id, password)
        assertEquals(1, vaultDatabase.known_hostQueries.index().executeAsList().size)
    }

    @Test
    fun `a vault's known hosts list comes along when it's exported and imported`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        vaultDatabase.known_hostQueries.insert("example.com ssh-ed25519 AAAA")
        val exported = File(root, "exported.sshv").path
        manager.export(vault.id, password, exported)
        manager.lock()

        val imported = manager.import(exported, password)
        manager.unlock(imported.id, password)

        assertEquals(listOf("example.com ssh-ed25519 AAAA"), vaultDatabase.known_hostQueries.index().executeAsList())
    }

    /** A vault file as an older version of the app would have left it, holding a connection called `old`. */
    private suspend fun importOldVault(version: Long, missingTables: List<String>): VaultInfo {
        val image = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            Database.Schema.create(driver).value
            missingTables.forEach { driver.execute(null, "DROP TABLE $it", 0) }
            driver.execute(null, "PRAGMA user_version = $version", 0)
            database(driver).ssh_connectionQueries.upsert(
                com.sshellspace.core.data.generated.Ssh_connections("1", "old", "h", 22, "u", "{}", "{}"),
            )
            driver.getConnection().unwrap(SQLiteConnection::class.java).serialize("main")
        }
        val id = "018f0000-0000-7000-8000-000000000001"
        val key = newVaultKey()
        val file = File(root, "old.sshv")
        file.writeBytes(VaultFile.seal(VaultHeader(id, "Old", listOf(newKeySlot(id, password, key))), key, image))
        return manager.import(file.path, password)
    }

    @Test
    fun `before-unlock actions run with the data readable but the vault not yet shown`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "server")
        manager.lock()
        var names: List<String>? = null
        var shownAsActive: VaultInfo? = vault
        manager.beforeUnlock {
            names = connectionNames(vaultDatabase)
            shownAsActive = manager.activeVault.value
        }

        manager.unlock(vault.id, password)

        assertEquals(listOf("server"), names)
        assertNull(shownAsActive)
    }

    @Test
    fun `a wrong password deletes nothing and leaves the vault unlocked`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        var deleted = false
        manager.afterDelete { deleted = true }

        assertFailsWith<VaultException.WrongCredential> { manager.delete(vault.id, wrongPassword) }
        assertFailsWith<VaultException.WrongCredential> { manager.delete(vault.id, VaultCredential.None) }

        assertTrue(vaultFile(vault).exists())
        assertEquals(vault.id, manager.activeVault.value?.id)
        assertEquals(listOf(vault.id), manager.vaults.value.map { it.id })
        assertFalse(deleted)
        insertConnection(vaultDatabase, "1", "still usable")
    }

    @Test
    fun `a wrong password exports nothing`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        val exported = File(root, "export.sshv")

        assertFailsWith<VaultException.WrongCredential> { manager.export(vault.id, wrongPassword, exported.path) }
        assertFailsWith<VaultException.WrongCredential> { manager.export(vault.id, VaultCredential.None, exported.path) }

        assertFalse(exported.exists())
    }

    @Test
    fun `a wrong password doesn't overwrite an existing file when exporting`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        val existing = File(root, "precious.txt").apply { writeText("keep me") }

        assertFailsWith<VaultException.WrongCredential> { manager.export(vault.id, wrongPassword, existing.path) }

        assertEquals("keep me", existing.readText())
    }

    @Test
    fun `export and delete want the current password after it changes`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        val changed = VaultCredential.Password("a new password")
        manager.changeCredential(vault.id, password, changed)

        assertFailsWith<VaultException.WrongCredential> { manager.delete(vault.id, password) }
        manager.verifyCredential(vault.id, changed)
        manager.delete(vault.id, changed)

        assertFalse(vaultFile(vault).exists())
    }

    @Test
    fun `verifying checks the password, and changes nothing`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)

        manager.verifyCredential(vault.id, password)
        assertFailsWith<VaultException.WrongCredential> { manager.verifyCredential(vault.id, wrongPassword) }

        assertEquals(vault.id, manager.activeVault.value?.id)
    }

    @Test
    fun `after-delete actions get the deleted vault`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        var deleted: Uuid? = null
        manager.afterDelete { deleted = it }

        manager.delete(vault.id, password)

        assertEquals(vault.id, deleted)
    }

    @Test
    fun `any password is allowed, but names stay unique`() = runTest {
        val vault = manager.create("Work", VaultCredential.Password("x"), moveUnencryptedData = false)
        manager.lock()
        manager.unlock(vault.id, VaultCredential.Password("x"))

        assertFailsWith<VaultException.Invalid> { manager.create("work", password, moveUnencryptedData = false) }
    }

    @Test
    fun `a vault with no password opens without one and survives a restart`() = runTest {
        val vault = manager.create("Open", VaultCredential.None, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "server")
        assertFalse(vault.hasPassword)
        manager.lock()

        val restarted = manager()
        assertFalse(restarted.vaults.value.single().hasPassword)
        restarted.unlock(vault.id, VaultCredential.None)

        assertEquals(listOf("server"), connectionNames(vaultDatabase))
    }

    @Test
    fun `a vault with a password can't be opened without it`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        manager.lock()

        assertFailsWith<VaultException.WrongCredential> { manager.unlock(vault.id, VaultCredential.None) }
    }

    @Test
    fun `an unlocked vault's password can change, and later writes use the new key`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "before")
        val newPassword = VaultCredential.Password("something else")

        manager.changeCredential(vault.id, password, newPassword)
        assertEquals(vault, manager.activeVault.value)
        insertConnection(vaultDatabase, "2", "after")
        manager.lock()

        assertFailsWith<VaultException.WrongCredential> { manager.unlock(vault.id, password) }
        manager.unlock(vault.id, newPassword)
        assertEquals(listOf("after", "before"), connectionNames(vaultDatabase))
    }

    @Test
    fun `changing the password needs the current one`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)

        assertFailsWith<VaultException.WrongCredential> {
            manager.changeCredential(vault.id, wrongPassword, VaultCredential.None)
        }
    }

    @Test
    fun `a password can be removed and added back`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "server")

        manager.changeCredential(vault.id, password, VaultCredential.None)
        assertEquals(false, manager.activeVault.value?.hasPassword)
        assertEquals(false, manager.vaults.value.single().hasPassword)

        manager.changeCredential(vault.id, VaultCredential.None, password)
        assertEquals(true, manager.activeVault.value?.hasPassword)
        manager.lock()
        assertFailsWith<VaultException.WrongCredential> { manager.unlock(vault.id, VaultCredential.None) }
        manager.unlock(vault.id, password)
        assertEquals(listOf("server"), connectionNames(vaultDatabase))
    }

    @Test
    fun `a vault with no password exports and imports`() = runTest {
        val vault = manager.create("Open", VaultCredential.None, moveUnencryptedData = false)
        val exported = File(root, "open.sshv")
        manager.export(vault.id, VaultCredential.None, exported.path)
        manager.delete(vault.id, VaultCredential.None)

        val imported = manager.import(exported.path, VaultCredential.None)

        assertFalse(imported.hasPassword)
        manager.unlock(imported.id, VaultCredential.None)
    }

    @Test
    fun `deleting finishes even when locking cancels the coroutine that asked`() = runTest {
        val vault = manager.create("Work", password, moveUnencryptedData = false)
        var deleter: Job? = null
        manager.beforeLock {
            deleter?.cancel()
            yield()
        }

        deleter = launch(Dispatchers.Default, start = CoroutineStart.LAZY) { manager.delete(vault.id, password) }
        deleter.start()
        deleter.join()

        assertFalse(vaultFile(vault).exists())
        assertEquals(emptyList(), manager.vaults.value)
    }

    @Test
    fun `a long-lived query refreshes when another vault unlocks`() = runTest {
        val work = manager.create("Work", password, moveUnencryptedData = false)
        insertConnection(vaultDatabase, "1", "work-server")
        val query = vaultDatabase.ssh_connectionQueries.index()
        var notified = 0
        val listener = app.cash.sqldelight.Query.Listener { notified++ }
        query.addListener(listener)

        manager.lock()
        manager.unlock(work.id, password)

        assertEquals(1, notified)
        query.removeListener(listener)
        assertEquals(work, manager.activeVault.first { it != null })
    }
}
