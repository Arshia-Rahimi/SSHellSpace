package com.sshellspace.feature.vault.gate

import com.sshellspace.core.domain.models.AppUpdateState
import com.sshellspace.core.domain.models.VaultCredential
import com.sshellspace.core.domain.models.VaultException
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.AppUpdater
import com.sshellspace.core.domain.repositories.VaultManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class VaultGateViewModelTest {

    private val work = VaultInfo(Uuid.random(), "Work")
    private val vaults = MutableStateFlow(listOf(work))
    private val manager = mockk<VaultManager>(relaxed = true) {
        every { vaults } returns this@VaultGateViewModelTest.vaults
        coEvery { unencryptedItemCount() } returns 3
    }
    private val updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    private val updater = mockk<AppUpdater>(relaxed = true) {
        every { state } returns updateState
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `with no vaults the gate asks to create one`() = runTest {
        vaults.value = emptyList()

        val state = VaultGateViewModel(manager, updater).uiState.first { it.mode == VaultGateMode.Create }

        assertEquals(3, state.unencryptedItemCount)
    }

    @Test
    fun `with vaults the gate shows the list`() = runTest {
        val state = VaultGateViewModel(manager, updater).uiState.first { it.vaults.isNotEmpty() }

        assertIs<VaultGateMode.Select>(state.mode)
    }

    @Test
    fun `a single vault is selected without a click`() = runTest {
        val state = VaultGateViewModel(manager, updater).uiState.first { it.vaults.isNotEmpty() }

        assertEquals(VaultGateMode.Select(work.id), state.mode)
    }

    @Test
    fun `with several vaults none is selected until one is chosen`() = runTest {
        val home = VaultInfo(Uuid.random(), "Home")
        vaults.value = listOf(work, home)
        val viewModel = VaultGateViewModel(manager, updater)

        assertEquals(VaultGateMode.Select(null), viewModel.uiState.first { it.vaults.size == 2 }.mode)

        viewModel.execute(VaultGateAction.SelectVault(home.id))
        assertEquals(VaultGateMode.Select(home.id), viewModel.uiState.first { it.mode == VaultGateMode.Select(home.id) }.mode)
    }

    @Test
    fun `an empty password isn't tried`() = runTest {
        val viewModel = VaultGateViewModel(manager, updater)

        viewModel.execute(VaultGateAction.Unlock(work.id, ""))

        assertEquals("Enter the vault's password.", viewModel.uiState.first { it.error != null }.error)
        coVerify(exactly = 0) { manager.unlock(any(), any()) }
    }

    @Test
    fun `a vault with no password opens without one`() = runTest {
        val open = VaultInfo(Uuid.random(), "Open", hasPassword = false)
        vaults.value = listOf(open)

        VaultGateViewModel(manager, updater).execute(VaultGateAction.Unlock(open.id, ""))

        coVerify { manager.unlock(open.id, VaultCredential.None) }
    }

    @Test
    fun `a weak password gets through`() = runTest {
        VaultGateViewModel(manager, updater).execute(VaultGateAction.Create("New", "abc", "abc", moveUnencryptedData = false))

        coVerify { manager.create("New", VaultCredential.Password("abc"), false) }
    }

    @Test
    fun `no password creates a vault without one`() = runTest {
        VaultGateViewModel(manager, updater).execute(VaultGateAction.Create("New", "", "", moveUnencryptedData = false))

        coVerify { manager.create("New", VaultCredential.None, false) }
    }

    @Test
    fun `a mistyped confirmation stops the form, since it would lock the user out`() = runTest {
        val viewModel = VaultGateViewModel(manager, updater)

        viewModel.execute(VaultGateAction.Create("New", "abc", "abd", moveUnencryptedData = false))

        assertEquals("The passwords don't match.", viewModel.uiState.first { it.error != null }.error)
        coVerify(exactly = 0) { manager.create(any(), any(), any()) }
    }

    @Test
    fun `typing clears the last error`() = runTest {
        val viewModel = VaultGateViewModel(manager, updater)
        viewModel.execute(VaultGateAction.Unlock(work.id, ""))
        viewModel.uiState.first { it.error != null }

        viewModel.execute(VaultGateAction.ClearError)

        assertNull(viewModel.uiState.first { it.error == null }.error)
    }

    @Test
    fun `importing selects the new vault ready to unlock`() = runTest {
        val imported = VaultInfo(Uuid.random(), "Imported")
        coEvery { manager.import(any(), any()) } answers {
            vaults.value = vaults.value + imported
            imported
        }
        val viewModel = VaultGateViewModel(manager, updater)

        viewModel.execute(VaultGateAction.Import("/tmp/a.sshv", "correct horse"))

        val state = viewModel.uiState.first { it.notice != null }
        assertEquals(VaultGateMode.Select(imported.id), state.mode)
    }

    @Test
    fun `a wrong password is shown and the form can be used again`() = runTest {
        coEvery { manager.unlock(work.id, any()) } throws VaultException.WrongCredential()
        val viewModel = VaultGateViewModel(manager, updater)

        viewModel.execute(VaultGateAction.Unlock(work.id, "nope"))

        val state = viewModel.uiState.first { it.error != null }
        assertEquals("Wrong password.", state.error)
        assertFalse(state.isBusy)
    }

    @Test
    fun `unlock passes the password on`() = runTest {
        VaultGateViewModel(manager, updater).execute(VaultGateAction.Unlock(work.id, "correct horse"))

        coVerify { manager.unlock(work.id, VaultCredential.Password("correct horse")) }
    }

    @Test
    fun `create passes the choice to move existing data`() = runTest {
        VaultGateViewModel(manager, updater).execute(
            VaultGateAction.Create("New", "long enough here", "long enough here", moveUnencryptedData = true),
        )

        coVerify { manager.create("New", VaultCredential.Password("long enough here"), true) }
    }

    @Test
    fun `a successful import returns to the list`() = runTest {
        val viewModel = VaultGateViewModel(manager, updater)
        viewModel.execute(VaultGateAction.ShowImport("/tmp/a.sshv"))
        assertEquals(VaultGateMode.Import("/tmp/a.sshv"), viewModel.uiState.first { it.mode is VaultGateMode.Import }.mode)

        viewModel.execute(VaultGateAction.Import("/tmp/a.sshv", "correct horse"))

        val state = viewModel.uiState.first { it.mode is VaultGateMode.Select }
        assertNull(state.error)
    }

    @Test
    fun `a new version shows on the gate at every stage`() = runTest {
        val viewModel = VaultGateViewModel(manager, updater)

        listOf(
            AppUpdateState.Available("1.1.0"),
            AppUpdateState.Downloading("1.1.0", 40),
            AppUpdateState.Ready("1.1.0"),
        ).forEach { state ->
            updateState.value = state
            assertEquals(state, viewModel.uiState.first { it.update == state }.update)
        }
    }

    @Test
    fun `nothing about updates shows when there's nothing to act on`() = runTest {
        val viewModel = VaultGateViewModel(manager, updater)

        listOf(
            AppUpdateState.Idle,
            AppUpdateState.Unsupported,
            AppUpdateState.Checking,
            AppUpdateState.UpToDate,
        ).forEach { state ->
            updateState.value = state
            assertNull(viewModel.uiState.first { it.vaults.isNotEmpty() }.update)
        }
    }

    @Test
    fun `a background failure stays off the gate`() = runTest {
        val viewModel = VaultGateViewModel(manager, updater)

        updateState.value = AppUpdateState.Failed("Couldn't check for updates.")

        assertNull(viewModel.uiState.first { it.vaults.isNotEmpty() }.update)
    }

    @Test
    fun `a failure shows once the user has asked for the download`() = runTest {
        updateState.value = AppUpdateState.Available("1.1.0")
        val viewModel = VaultGateViewModel(manager, updater)

        viewModel.execute(VaultGateAction.Update.Download)
        val failed = AppUpdateState.Failed("Couldn't download the update.")
        updateState.value = failed

        verify { updater.download() }
        assertEquals(failed, viewModel.uiState.first { it.update != null }.update)
    }

    @Test
    fun `restarting from the gate warns about sessions still running behind the lock`() = runTest {
        every { updater.openSessionCount() } returns 2
        updateState.value = AppUpdateState.Ready("1.1.0")
        val viewModel = VaultGateViewModel(manager, updater)

        viewModel.execute(VaultGateAction.Update.Restart)

        assertEquals(2, viewModel.uiState.first { it.confirmingRestart != null }.confirmingRestart)
        verify(exactly = 0) { updater.restartToInstall() }

        viewModel.execute(VaultGateAction.Update.ConfirmRestart)

        assertNull(viewModel.uiState.first { it.confirmingRestart == null }.confirmingRestart)
        verify { updater.restartToInstall() }
    }

    @Test
    fun `cancelling the warning restarts nothing`() = runTest {
        every { updater.openSessionCount() } returns 1
        updateState.value = AppUpdateState.Ready("1.1.0")
        val viewModel = VaultGateViewModel(manager, updater)
        viewModel.execute(VaultGateAction.Update.Restart)

        viewModel.execute(VaultGateAction.Update.CancelRestart)

        assertNull(viewModel.uiState.first { it.confirmingRestart == null }.confirmingRestart)
        verify(exactly = 0) { updater.restartToInstall() }
    }

    @Test
    fun `restarting doesn't ask when nothing is running`() = runTest {
        every { updater.openSessionCount() } returns 0
        updateState.value = AppUpdateState.Ready("1.1.0")

        VaultGateViewModel(manager, updater).execute(VaultGateAction.Update.Restart)

        verify { updater.restartToInstall() }
    }

    @Test
    fun `a restart waits for a vault operation to finish`() = runTest {
        every { updater.openSessionCount() } returns 0
        val unlocking = CompletableDeferred<Unit>()
        coEvery { manager.unlock(work.id, any()) } coAnswers { unlocking.await() }
        val viewModel = VaultGateViewModel(manager, updater)
        viewModel.execute(VaultGateAction.Unlock(work.id, "password"))

        viewModel.execute(VaultGateAction.Update.Restart)

        verify(exactly = 0) { updater.restartToInstall() }
        unlocking.complete(Unit)
    }

    @Test
    fun `changing screens clears the error`() = runTest {
        coEvery { manager.unlock(work.id, any()) } throws VaultException.WrongCredential()
        val viewModel = VaultGateViewModel(manager, updater)
        viewModel.execute(VaultGateAction.Unlock(work.id, "nope"))
        viewModel.uiState.first { it.error != null }

        viewModel.execute(VaultGateAction.ShowCreate)

        assertNull(viewModel.uiState.first { it.mode == VaultGateMode.Create }.error)
    }
}
