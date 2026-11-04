package com.sshellspace.core.domain.repositories

import com.sshellspace.core.domain.models.VaultCredential
import com.sshellspace.core.domain.models.VaultInfo
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.StateFlow

/**
 * The encrypted vaults on this device. Connections, keys and port forwards belong to a vault and can only be read while
 * it is unlocked, so anything using them must only run while [activeVault] is set.
 *
 * Failures are reported as `VaultException`, or `IOException` when a file can't be read or written.
 */
interface VaultManager {
    val vaults: StateFlow<List<VaultInfo>>

    /** The unlocked vault, or null while locked. */
    val activeVault: StateFlow<VaultInfo?>

    /** How many connections, keys and forwards still sit unencrypted from before vaults existed. */
    suspend fun unencryptedItemCount(): Long

    /**
     * Creates a vault and unlocks it. With [moveUnencryptedData], the data counted by [unencryptedItemCount] moves into
     * it and is erased from the unencrypted database once the vault is safely on disk.
     */
    suspend fun create(name: String, credential: VaultCredential, moveUnencryptedData: Boolean): VaultInfo

    /** Unlocks vault [id], locking any other vault first. */
    suspend fun unlock(id: Uuid, credential: VaultCredential)

    suspend fun lock()

    /** Registers [action] to run as a vault locks, while its data can still be written. */
    fun beforeLock(action: suspend () -> Unit)

    /** Writes vault [id] to [destination] as an encrypted file. Its password is needed to import it again. */
    suspend fun export(id: Uuid, destination: String)

    /** Adds the vault file at [source] after checking that [credential] opens it. Doesn't unlock it. */
    suspend fun import(source: String, credential: VaultCredential): VaultInfo

    /**
     * Replaces the password of locked vault [id] and re-encrypts it under a new vault key, so nothing known about the old
     * key opens the new file. Copies made earlier, such as exports, still open with the old password.
     */
    suspend fun changeCredential(id: Uuid, current: VaultCredential, new: VaultCredential)

    /** Permanently deletes vault [id], locking it first if it is unlocked. */
    suspend fun delete(id: Uuid)
}
