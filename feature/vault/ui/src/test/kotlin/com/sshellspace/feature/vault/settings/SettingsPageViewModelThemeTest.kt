package com.sshellspace.feature.vault.settings

import com.sshellspace.core.domain.models.DEFAULT_COLOR_SCHEME_ID
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.domain.models.VaultCredential
import com.sshellspace.core.domain.models.VaultException
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.TerminalPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
class SettingsPageViewModelThemeTest {

    private val corePreferences = mockk<CorePreferencesRepository>(relaxed = true) {
        every { getTheme() } returns flowOf(ThemePreference.DARK)
        every { getColorScheme() } returns flowOf(DEFAULT_COLOR_SCHEME_ID)
        every { getUserThemes() } returns flowOf(emptyList())
        every { getActiveUserTheme() } returns flowOf(null)
        every { getAutoLockMinutes() } returns flowOf(15)
    }
    private val terminalPreferences = mockk<TerminalPreferencesRepository>(relaxed = true) {
        every { get() } returns MutableStateFlow(TerminalPreferences(terminalThemeName = "nord"))
    }
    private val activeVault = MutableStateFlow<VaultInfo?>(null)
    private val vaultManager = mockk<VaultManager>(relaxed = true) {
        every { activeVault } returns this@SettingsPageViewModelThemeTest.activeVault
    }
    private val shortcutHandler = mockk<ShortcutHandler>(relaxed = true) {
        every { bindings } returns MutableStateFlow(emptyMap())
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsPageViewModel(
        corePreferences,
        terminalPreferences,
        mockk<ShellDiscoveryService>(relaxed = true),
        shortcutHandler,
        vaultManager,
    )

    private val work = VaultInfo(Uuid.random(), "Work")

    @Test
    fun `exporting says it worked`() = runTest {
        activeVault.value = work
        val viewModel = viewModel()

        viewModel.execute(SettingsPageAction.ExportVault("/tmp/work.sshv"))

        coVerify { vaultManager.export(work.id, "/tmp/work.sshv") }
        assertContains(viewModel.uiState.first { it.vaultForm.notice != null }.vaultForm.notice.orEmpty(), "Exported Work")
    }

    @Test
    fun `exporting a vault with no password warns about the file`() = runTest {
        activeVault.value = work.copy(hasPassword = false)
        val viewModel = viewModel()

        viewModel.execute(SettingsPageAction.ExportVault("/tmp/work.sshv"))

        assertContains(viewModel.uiState.first { it.vaultForm.notice != null }.vaultForm.notice.orEmpty(), "anyone")
    }

    @Test
    fun `an empty new password removes it`() = runTest {
        activeVault.value = work
        viewModel().execute(SettingsPageAction.ChangeVaultPassword("old", "", ""))

        coVerify { vaultManager.changeCredential(work.id, VaultCredential.Password("old"), VaultCredential.None) }
    }

    @Test
    fun `a mistyped new password stops the change`() = runTest {
        activeVault.value = work
        val viewModel = viewModel()

        viewModel.execute(SettingsPageAction.ChangeVaultPassword("old", "new", "typo"))

        assertContains(viewModel.uiState.first { it.vaultForm.error != null }.vaultForm.error.orEmpty(), "match")
        coVerify(exactly = 0) { vaultManager.changeCredential(any(), any(), any()) }
    }

    @Test
    fun `a wrong current password is shown`() = runTest {
        activeVault.value = work
        coEvery { vaultManager.changeCredential(any(), any(), any()) } throws VaultException.WrongCredential()
        val viewModel = viewModel()

        viewModel.execute(SettingsPageAction.ChangeVaultPassword("bad", "new", "new"))

        assertEquals("Wrong password.", viewModel.uiState.first { it.vaultForm.error != null }.vaultForm.error)
    }

    @Test
    fun `deleting deletes the unlocked vault`() = runTest {
        activeVault.value = work

        viewModel().execute(SettingsPageAction.DeleteVault)

        coVerify { vaultManager.delete(work.id) }
    }

    @Test
    fun `an unreadable file is reported`() = runTest {
        val viewModel = viewModel()

        viewModel.execute(SettingsPageAction.ImportTheme("/does/not/exist.json"))

        val error = viewModel.uiState.first { it.themeImportError != null }.themeImportError
        assertContains(error.orEmpty(), "Couldn't read the file")
    }

    @Test
    fun `an invalid theme is reported`() = runTest {
        val file = File.createTempFile("theme", ".json").apply { deleteOnExit() }
        coEvery { corePreferences.importUserTheme(any()) } throws IllegalArgumentException("dark.primary is bad")
        val viewModel = viewModel()

        viewModel.execute(SettingsPageAction.ImportTheme(file.path))

        val error = viewModel.uiState.first { it.themeImportError != null }.themeImportError
        assertContains(error.orEmpty(), "dark.primary is bad")
    }

    @Test
    fun `removing the theme the terminal uses makes it follow the app again`() = runTest {
        viewModel().execute(SettingsPageAction.RemoveUserTheme("nord"))

        coVerify { corePreferences.deleteUserTheme("nord") }
        coVerify { terminalPreferences.setTerminalThemeName(null) }
    }
}
