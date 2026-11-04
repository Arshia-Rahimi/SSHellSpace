package com.sshellspace.feature.vault.gate

import com.sshellspace.core.domain.models.VaultCredential
import com.sshellspace.core.domain.models.VaultException
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.VaultManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid
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

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `with no vaults the gate asks to create one`() = runTest {
        vaults.value = emptyList()

        val state = VaultGateViewModel(manager).uiState.first { it.mode == VaultGateMode.Create }

        assertEquals(3, state.unencryptedItemCount)
    }

    @Test
    fun `with vaults the gate shows the list`() = runTest {
        val state = VaultGateViewModel(manager).uiState.first { it.vaults.isNotEmpty() }

        assertIs<VaultGateMode.Select>(state.mode)
    }

    @Test
    fun `a wrong password is shown and the form can be used again`() = runTest {
        coEvery { manager.unlock(work.id, any()) } throws VaultException.WrongCredential()
        val viewModel = VaultGateViewModel(manager)

        viewModel.execute(VaultGateAction.Unlock(work.id, "nope"))

        val state = viewModel.uiState.first { it.error != null }
        assertEquals("Wrong password.", state.error)
        assertFalse(state.isBusy)
    }

    @Test
    fun `unlock passes the password on`() = runTest {
        VaultGateViewModel(manager).execute(VaultGateAction.Unlock(work.id, "correct horse"))

        coVerify { manager.unlock(work.id, VaultCredential.Password("correct horse")) }
    }

    @Test
    fun `create refuses a short password or a mismatch without calling the manager`() = runTest {
        val viewModel = VaultGateViewModel(manager)

        viewModel.execute(VaultGateAction.Create("New", "short", "short", moveUnencryptedData = false))
        assertEquals("The password needs at least 12 characters.", viewModel.uiState.first { it.error != null }.error)

        viewModel.execute(VaultGateAction.Create("New", "long enough here", "different", moveUnencryptedData = false))
        assertEquals("The passwords don't match.", viewModel.uiState.first { it.error != null }.error)

        coVerify(exactly = 0) { manager.create(any(), any(), any()) }
    }

    @Test
    fun `create passes the choice to move existing data`() = runTest {
        VaultGateViewModel(manager).execute(
            VaultGateAction.Create("New", "long enough here", "long enough here", moveUnencryptedData = true),
        )

        coVerify { manager.create("New", VaultCredential.Password("long enough here"), true) }
    }

    @Test
    fun `a successful import returns to the list`() = runTest {
        val viewModel = VaultGateViewModel(manager)
        viewModel.execute(VaultGateAction.ShowImport("/tmp/a.sshv"))
        assertEquals(VaultGateMode.Import("/tmp/a.sshv"), viewModel.uiState.first { it.mode is VaultGateMode.Import }.mode)

        viewModel.execute(VaultGateAction.Import("/tmp/a.sshv", "correct horse"))

        val state = viewModel.uiState.first { it.mode is VaultGateMode.Select }
        assertNull(state.error)
    }

    @Test
    fun `changing screens clears the error`() = runTest {
        coEvery { manager.unlock(work.id, any()) } throws VaultException.WrongCredential()
        val viewModel = VaultGateViewModel(manager)
        viewModel.execute(VaultGateAction.Unlock(work.id, "nope"))
        viewModel.uiState.first { it.error != null }

        viewModel.execute(VaultGateAction.ShowCreate)

        assertNull(viewModel.uiState.first { it.mode == VaultGateMode.Create }.error)
    }
}
