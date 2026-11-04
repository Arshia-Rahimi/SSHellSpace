package com.sshellspace.core.domain.repositories

import kotlinx.coroutines.flow.Flow

/** Settings of the unlocked vault. */
interface VaultPreferencesRepository {
    /**
     * The unlocked vault's idle timeout in minutes, 0 meaning never. A vault that hasn't chosen one uses
     * [CorePreferencesRepository.getAutoLockMinutes], and so does the time when no vault is unlocked.
     */
    fun getAutoLockMinutes(): Flow<Int>

    /** Only use while a vault is unlocked. */
    suspend fun setAutoLockMinutes(minutes: Int)
}
