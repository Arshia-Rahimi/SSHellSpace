package com.sshellspace.feature.vault.settings

import com.sshellspace.core.domain.models.VaultCredential
import com.sshellspace.core.domain.models.VaultException
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.domain.repositories.VaultPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.uuid.Uuid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class VaultSettingsViewModelTest {

    private val vaultPreferences = mockk<VaultPreferencesRepository>(relaxed = true) {
        every { getAutoLockMinutes() } returns flowOf(30)
    }
    private val activeVault = MutableStateFlow<VaultInfo?>(null)
    private val vaultManager = mockk<VaultManager>(relaxed = true) {
        every { activeVault } returns this@VaultSettingsViewModelTest.activeVault
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = VaultSettingsViewModel(vaultManager, vaultPreferences)

    private val work = VaultInfo(Uuid.random(), "Work")

    @Test
    fun `the idle timeout shown and changed is the unlocked vault's`() = runTest {
        activeVault.value = work
        val viewModel = viewModel()

        assertEquals(30, viewModel.uiState.first { it.vaultName != null }.autoLockMinutes)

        viewModel.execute(VaultSettingsAction.SetAutoLockMinutes(5))

        coVerify { vaultPreferences.setAutoLockMinutes(5) }
    }

    @Test
    fun `exporting says it worked`() = runTest {
        activeVault.value = work
        val viewModel = viewModel()

        viewModel.execute(VaultSettingsAction.ExportVault("secret") { "/tmp/work.sshv" })

        coVerify { vaultManager.export(work.id, VaultCredential.Password("secret"), "/tmp/work.sshv") }
        assertContains(viewModel.uiState.first { it.vaultForm.notice != null }.vaultForm.notice.orEmpty(), "Exported Work")
    }

    @Test
    fun `exporting a vault with no password warns about the file`() = runTest {
        activeVault.value = work.copy(hasPassword = false)
        val viewModel = viewModel()

        viewModel.execute(VaultSettingsAction.ExportVault("") { "/tmp/work.sshv" })

        assertContains(viewModel.uiState.first { it.vaultForm.notice != null }.vaultForm.notice.orEmpty(), "anyone")
    }

    @Test
    fun `an empty new password removes it`() = runTest {
        activeVault.value = work
        viewModel().execute(VaultSettingsAction.ChangeVaultPassword("old", "", ""))

        coVerify { vaultManager.changeCredential(work.id, VaultCredential.Password("old"), VaultCredential.None) }
    }

    @Test
    fun `a mistyped new password stops the change`() = runTest {
        activeVault.value = work
        val viewModel = viewModel()

        viewModel.execute(VaultSettingsAction.ChangeVaultPassword("old", "new", "typo"))

        assertContains(viewModel.uiState.first { it.vaultForm.error != null }.vaultForm.error.orEmpty(), "match")
        coVerify(exactly = 0) { vaultManager.changeCredential(any(), any(), any()) }
    }

    @Test
    fun `a wrong current password is shown`() = runTest {
        activeVault.value = work
        coEvery { vaultManager.changeCredential(any(), any(), any()) } throws VaultException.WrongCredential()
        val viewModel = viewModel()

        viewModel.execute(VaultSettingsAction.ChangeVaultPassword("bad", "new", "new"))

        assertEquals("Wrong password.", viewModel.uiState.first { it.vaultForm.error != null }.vaultForm.error)
    }

    @Test
    fun `deleting deletes the unlocked vault`() = runTest {
        activeVault.value = work

        viewModel().execute(VaultSettingsAction.DeleteVault("secret"))

        coVerify { vaultManager.delete(work.id, VaultCredential.Password("secret")) }
    }

    @Test
    fun `a wrong password stops an export before the save dialog`() = runTest {
        activeVault.value = work
        coEvery { vaultManager.verifyCredential(work.id, any()) } throws VaultException.WrongCredential()
        var asked = false
        val viewModel = viewModel()

        viewModel.execute(VaultSettingsAction.ExportVault("bad") { asked = true; "/tmp/work.sshv" })

        assertEquals("Wrong password.", viewModel.uiState.first { it.vaultForm.error != null }.vaultForm.error)
        assertFalse(asked)
        coVerify(exactly = 0) { vaultManager.export(any(), any(), any()) }
    }

    @Test
    fun `cancelling the save dialog exports nothing and keeps the form open`() = runTest {
        activeVault.value = work
        val viewModel = viewModel()
        viewModel.execute(VaultSettingsAction.SetVaultEdit(VaultEdit.Export))

        viewModel.execute(VaultSettingsAction.ExportVault("secret") { null })

        val form = viewModel.uiState.first { it.vaultForm.edit == VaultEdit.Export && !it.vaultForm.isBusy }.vaultForm
        assertNull(form.error)
        assertNull(form.notice)
        coVerify(exactly = 0) { vaultManager.export(any(), any(), any()) }
    }

    @Test
    fun `a finished export closes the password form`() = runTest {
        activeVault.value = work
        val viewModel = viewModel()
        viewModel.execute(VaultSettingsAction.SetVaultEdit(VaultEdit.Export))

        viewModel.execute(VaultSettingsAction.ExportVault("secret") { "/tmp/work.sshv" })

        assertNull(viewModel.uiState.first { it.vaultForm.notice != null }.vaultForm.edit)
    }

    @Test
    fun `a vault with no password exports and deletes without one`() = runTest {
        activeVault.value = work.copy(hasPassword = false)
        val viewModel = viewModel()

        viewModel.execute(VaultSettingsAction.ExportVault("") { "/tmp/work.sshv" })
        viewModel.uiState.first { it.vaultForm.notice != null }
        coVerify { vaultManager.export(work.id, VaultCredential.None, "/tmp/work.sshv") }

        viewModel.execute(VaultSettingsAction.DeleteVault(""))
        coVerify { vaultManager.delete(work.id, VaultCredential.None) }
    }

    @Test
    fun `a wrong password when deleting is shown`() = runTest {
        activeVault.value = work
        coEvery { vaultManager.delete(work.id, any()) } throws VaultException.WrongCredential()
        val viewModel = viewModel()

        viewModel.execute(VaultSettingsAction.DeleteVault("bad"))

        assertEquals("Wrong password.", viewModel.uiState.first { it.vaultForm.error != null }.vaultForm.error)
    }
}
