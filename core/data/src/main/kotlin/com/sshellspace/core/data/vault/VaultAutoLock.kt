package com.sshellspace.core.data.vault

import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.VaultManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private const val TICK_MILLIS = 5_000L
private const val MILLIS_PER_MINUTE = 60_000L

/** A gap this far beyond [TICK_MILLIS] means the process wasn't running, which in practice means the machine slept. */
private const val SLEEP_GAP_MILLIS = 60_000L

/**
 * Locks the unlocked vault after the configured idle time, or as soon as the machine wakes from sleep. Only user input
 * counts as activity, so output from a running terminal doesn't keep a vault open at an unattended machine.
 */
class VaultAutoLock(
    private val vaultManager: VaultManager,
    private val preferences: CorePreferencesRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var lastActivity = now()
    private var lastTick = now()

    fun recordActivity() {
        lastActivity = now()
    }

    suspend fun run() {
        preferences.getAutoLockMinutes().collectLatest { minutes ->
            while (true) {
                tick(minutes)
                delay(TICK_MILLIS)
            }
        }
    }

    internal suspend fun tick(timeoutMinutes: Int) {
        val time = now()
        val slept = time - lastTick > SLEEP_GAP_MILLIS
        lastTick = time
        if (timeoutMinutes <= 0 || vaultManager.activeVault.value == null) {
            // The idle time starts counting when a vault unlocks, not from before it.
            lastActivity = time
            return
        }
        if (slept || time - lastActivity >= timeoutMinutes * MILLIS_PER_MINUTE) vaultManager.lock()
    }
}
