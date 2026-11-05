package com.sshellspace.feature.vault.settings

import com.sshellspace.core.domain.models.DEFAULT_COLOR_SCHEME_ID
import com.sshellspace.core.domain.models.TerminalColors
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.domain.models.VaultCredential
import com.sshellspace.core.domain.models.VaultException
import com.sshellspace.core.domain.models.VaultInfo
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.domain.repositories.VaultManager
import com.sshellspace.core.domain.repositories.VaultPreferencesRepository
import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.TerminalPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import com.sshellspace.feature.terminal.ui.screen.emulators.bossterm.BuiltInTerminalThemes
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
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

    private val terminalThemes = MutableStateFlow(emptyList<TerminalColors>())
    private val corePreferences = mockk<CorePreferencesRepository>(relaxed = true) {
        every { getTheme() } returns flowOf(ThemePreference.DARK)
        every { getColorScheme() } returns flowOf(DEFAULT_COLOR_SCHEME_ID)
        every { getUserThemes() } returns flowOf(emptyList())
        every { getActiveUserTheme() } returns flowOf(null)
        every { getTerminalThemes() } returns terminalThemes
    }
    private val vaultPreferences = mockk<VaultPreferencesRepository>(relaxed = true) {
        every { getAutoLockMinutes() } returns flowOf(30)
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
        vaultPreferences,
    )

    private val work = VaultInfo(Uuid.random(), "Work")

    private fun themeFile(name: String, content: String) =
        Files.createTempDirectory("theme-test").resolve(name).also { Files.writeString(it, content) }.toString()

    @Test
    fun `built-in terminal themes are on offer without importing anything`() = runTest {
        val names = viewModel().uiState.first { it.terminalThemes.isNotEmpty() }.terminalThemes.map { it.name }

        assertContains(names, "Dracula")
        assertContains(names, "Solarized Light")
    }

    @Test
    fun `importing a terminal theme switches the terminal to it, and doesn't change the app theme`() = runTest {
        val imported = BuiltInTerminalThemes.first().copy(name = "Mine")
        coEvery { corePreferences.importTerminalTheme(any(), any()) } returns imported

        viewModel().execute(SettingsPageAction.ImportTerminalTheme(themeFile("mine.json", "{}")))

        coVerify { corePreferences.importTerminalTheme("{}", "mine") }
        coVerify { terminalPreferences.setTerminalThemeName("Mine") }
        coVerify(exactly = 0) { corePreferences.importUserTheme(any()) }
    }

    @Test
    fun `a terminal theme that won't import shows why and keeps the current one`() = runTest {
        coEvery { corePreferences.importTerminalTheme(any(), any()) } throws IllegalArgumentException("The file has no red color.")
        val viewModel = viewModel()

        viewModel.execute(SettingsPageAction.ImportTerminalTheme(themeFile("bad.json", "{}")))

        val error = viewModel.uiState.first { it.terminalThemeImportError != null }.terminalThemeImportError.orEmpty()
        assertContains(error, "no red color")
        coVerify(exactly = 0) { terminalPreferences.setTerminalThemeName(any()) }
    }

    @Test
    fun `removing the terminal theme in use goes back to following the app`() = runTest {
        viewModel().execute(SettingsPageAction.RemoveTerminalTheme("nord"))

        coVerify { corePreferences.deleteTerminalTheme("nord") }
        coVerify { terminalPreferences.setTerminalThemeName(null) }
    }

    @Test
    fun `removing another terminal theme leaves the one in use alone`() = runTest {
        viewModel().execute(SettingsPageAction.RemoveTerminalTheme("other"))

        coVerify(exactly = 0) { terminalPreferences.setTerminalThemeName(any()) }
    }

    @Test
    fun `the idle timeout shown and changed is the unlocked vault's`() = runTest {
        activeVault.value = work
        val viewModel = viewModel()

        assertEquals(30, viewModel.uiState.first { it.vaultName != null }.autoLockMinutes)

        viewModel.execute(SettingsPageAction.SetAutoLockMinutes(5))

        coVerify { vaultPreferences.setAutoLockMinutes(5) }
    }

    @Test
    fun `exporting says it worked`() = runTest {
        activeVault.value = work
        val viewModel = viewModel()

        viewModel.execute(SettingsPageAction.ExportVault("secret") { "/tmp/work.sshv" })

        coVerify { vaultManager.export(work.id, VaultCredential.Password("secret"), "/tmp/work.sshv") }
        assertContains(viewModel.uiState.first { it.vaultForm.notice != null }.vaultForm.notice.orEmpty(), "Exported Work")
    }

    @Test
    fun `exporting a vault with no password warns about the file`() = runTest {
        activeVault.value = work.copy(hasPassword = false)
        val viewModel = viewModel()

        viewModel.execute(SettingsPageAction.ExportVault("") { "/tmp/work.sshv" })

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

        viewModel().execute(SettingsPageAction.DeleteVault("secret"))

        coVerify { vaultManager.delete(work.id, VaultCredential.Password("secret")) }
    }

    @Test
    fun `a wrong password stops an export before the save dialog`() = runTest {
        activeVault.value = work
        coEvery { vaultManager.verifyCredential(work.id, any()) } throws VaultException.WrongCredential()
        var asked = false
        val viewModel = viewModel()

        viewModel.execute(SettingsPageAction.ExportVault("bad") { asked = true; "/tmp/work.sshv" })

        assertEquals("Wrong password.", viewModel.uiState.first { it.vaultForm.error != null }.vaultForm.error)
        assertFalse(asked)
        coVerify(exactly = 0) { vaultManager.export(any(), any(), any()) }
    }

    @Test
    fun `cancelling the save dialog exports nothing and keeps the form open`() = runTest {
        activeVault.value = work
        val viewModel = viewModel()
        viewModel.execute(SettingsPageAction.SetVaultEdit(VaultEdit.Export))

        viewModel.execute(SettingsPageAction.ExportVault("secret") { null })

        val form = viewModel.uiState.first { it.vaultForm.edit == VaultEdit.Export && !it.vaultForm.isBusy }.vaultForm
        assertNull(form.error)
        assertNull(form.notice)
        coVerify(exactly = 0) { vaultManager.export(any(), any(), any()) }
    }

    @Test
    fun `a finished export closes the password form`() = runTest {
        activeVault.value = work
        val viewModel = viewModel()
        viewModel.execute(SettingsPageAction.SetVaultEdit(VaultEdit.Export))

        viewModel.execute(SettingsPageAction.ExportVault("secret") { "/tmp/work.sshv" })

        assertNull(viewModel.uiState.first { it.vaultForm.notice != null }.vaultForm.edit)
    }

    @Test
    fun `a vault with no password exports and deletes without one`() = runTest {
        activeVault.value = work.copy(hasPassword = false)
        val viewModel = viewModel()

        viewModel.execute(SettingsPageAction.ExportVault("") { "/tmp/work.sshv" })
        viewModel.uiState.first { it.vaultForm.notice != null }
        coVerify { vaultManager.export(work.id, VaultCredential.None, "/tmp/work.sshv") }

        viewModel.execute(SettingsPageAction.DeleteVault(""))
        coVerify { vaultManager.delete(work.id, VaultCredential.None) }
    }

    @Test
    fun `a wrong password when deleting is shown`() = runTest {
        activeVault.value = work
        coEvery { vaultManager.delete(work.id, any()) } throws VaultException.WrongCredential()
        val viewModel = viewModel()

        viewModel.execute(SettingsPageAction.DeleteVault("bad"))

        assertEquals("Wrong password.", viewModel.uiState.first { it.vaultForm.error != null }.vaultForm.error)
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
