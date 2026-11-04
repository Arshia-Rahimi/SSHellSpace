package com.sshellspace.core.data.vault

import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.domain.repositories.VaultPreferencesRepository
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest

class VaultAutoLockTest {

    private var time = 0L
    private val activeVault = MutableStateFlow<VaultInfo?>(VaultInfo(Uuid.random(), "Work"))
    private val manager = mockk<VaultManager>(relaxed = true) {
        every { activeVault } returns this@VaultAutoLockTest.activeVault
    }
    private val autoLock = VaultAutoLock(manager, mockk<VaultPreferencesRepository>(), now = { time })

    /** Moves the clock in small steps, as the running timer would. */
    private suspend fun passTime(millis: Long, minutes: Int) {
        val end = time + millis
        while (time < end) {
            time += STEP
            autoLock.tick(minutes)
        }
    }

    @Test
    fun `locks once idle for the configured time`() = runTest {
        passTime(14 * MINUTE, minutes = 15)
        coVerify(exactly = 0) { manager.lock() }

        passTime(MINUTE, minutes = 15)
        coVerify { manager.lock() }
    }

    @Test
    fun `activity restarts the idle time`() = runTest {
        passTime(14 * MINUTE, minutes = 15)
        autoLock.recordActivity()
        passTime(14 * MINUTE, minutes = 15)

        coVerify(exactly = 0) { manager.lock() }
    }

    @Test
    fun `waking from sleep locks at once, even before the idle time`() = runTest {
        autoLock.tick(15)
        time += 2 * MINUTE
        autoLock.recordActivity()
        autoLock.tick(15)

        coVerify { manager.lock() }
    }

    @Test
    fun `never means no idle or sleep lock`() = runTest {
        time += 60 * MINUTE
        autoLock.tick(0)

        coVerify(exactly = 0) { manager.lock() }
    }

    @Test
    fun `idle time starts when a vault unlocks`() = runTest {
        activeVault.value = null
        passTime(60 * MINUTE, minutes = 15)
        activeVault.value = VaultInfo(Uuid.random(), "Work")

        passTime(STEP, minutes = 15)

        coVerify(exactly = 0) { manager.lock() }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val STEP = 5_000L
    }
}
