package com.sshellspace.feature.vault.settings

import com.sshellspace.core.domain.models.AppUpdateState
import com.sshellspace.core.domain.models.DEFAULT_COLOR_SCHEME_ID
import com.sshellspace.core.domain.models.TerminalColors
import com.sshellspace.core.domain.models.ThemePreference
import com.sshellspace.core.domain.repositories.AppUpdater
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.ui.shortcuts.ShortcutHandler
import com.sshellspace.feature.terminal.domain.ShellDiscoveryService
import com.sshellspace.feature.terminal.domain.TerminalPreferencesRepository
import com.sshellspace.feature.terminal.domain.models.TerminalPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import com.sshellspace.feature.terminal.ui.screen.emulators.bossterm.BuiltInTerminalThemes
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
class GeneralSettingsViewModelTest {

    private val terminalThemes = MutableStateFlow(emptyList<TerminalColors>())
    private val corePreferences = mockk<CorePreferencesRepository>(relaxed = true) {
        every { getTheme() } returns flowOf(ThemePreference.DARK)
        every { getColorScheme() } returns flowOf(DEFAULT_COLOR_SCHEME_ID)
        every { getUserThemes() } returns flowOf(emptyList())
        every { getActiveUserTheme() } returns flowOf(null)
        every { getTerminalThemes() } returns terminalThemes
        every { getAutoUpdate() } returns flowOf(true)
        every { getUpdateCheckIntervalHours() } returns flowOf(24)
        every { getLastUpdateCheck() } returns flowOf(null)
    }
    private val appUpdater = mockk<AppUpdater>(relaxed = true) {
        every { currentVersion } returns "1.0.0"
        every { state } returns MutableStateFlow(AppUpdateState.Ready("1.1.0"))
    }
    private val terminalPreferences = mockk<TerminalPreferencesRepository>(relaxed = true) {
        every { get() } returns MutableStateFlow(TerminalPreferences(terminalThemeName = "nord"))
    }
    private val shortcutHandler = mockk<ShortcutHandler>(relaxed = true) {
        every { bindings } returns MutableStateFlow(emptyMap())
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = GeneralSettingsViewModel(
        corePreferences,
        terminalPreferences,
        mockk<ShellDiscoveryService>(relaxed = true),
        shortcutHandler,
        appUpdater,
    )

    @Test
    fun `update settings reach the page and are saved`() = runTest {
        val viewModel = viewModel()

        val updates = viewModel.uiState.first { it.updates.currentVersion.isNotEmpty() }.updates
        viewModel.execute(GeneralSettingsAction.Update.SetAutomatic(false))
        viewModel.execute(GeneralSettingsAction.Update.SetCheckInterval(6))
        viewModel.execute(GeneralSettingsAction.Update.Check)

        assertEquals(AppUpdateState.Ready("1.1.0"), updates.status)
        coVerify { corePreferences.setAutoUpdate(false) }
        coVerify { corePreferences.setUpdateCheckIntervalHours(6) }
        verify { appUpdater.check() }
    }

    @Test
    fun `restarting asks first when it would close sessions`() = runTest {
        every { appUpdater.openSessionCount() } returns 2
        val viewModel = viewModel()

        viewModel.execute(GeneralSettingsAction.Update.Restart)

        assertEquals(2, viewModel.uiState.first { it.updates.confirmingRestart != null }.updates.confirmingRestart)
        verify(exactly = 0) { appUpdater.restartToInstall() }

        viewModel.execute(GeneralSettingsAction.Update.ConfirmRestart)

        verify { appUpdater.restartToInstall() }
    }

    @Test
    fun `restarting doesn't ask when nothing is open`() = runTest {
        every { appUpdater.openSessionCount() } returns 0

        viewModel().execute(GeneralSettingsAction.Update.Restart)

        verify { appUpdater.restartToInstall() }
    }

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

        viewModel().execute(GeneralSettingsAction.ImportTerminalTheme(themeFile("mine.json", "{}")))

        // The file is read on another dispatcher, so these have to wait for it.
        coVerify(timeout = 5_000) { corePreferences.importTerminalTheme("{}", "mine") }
        coVerify(timeout = 5_000) { terminalPreferences.setTerminalThemeName("Mine") }
        coVerify(exactly = 0) { corePreferences.importUserTheme(any()) }
    }

    @Test
    fun `a terminal theme that won't import shows why and keeps the current one`() = runTest {
        coEvery { corePreferences.importTerminalTheme(any(), any()) } throws IllegalArgumentException("The file has no red color.")
        val viewModel = viewModel()

        viewModel.execute(GeneralSettingsAction.ImportTerminalTheme(themeFile("bad.json", "{}")))

        val error = viewModel.uiState.first { it.terminalThemeImportError != null }.terminalThemeImportError.orEmpty()
        assertContains(error, "no red color")
        coVerify(exactly = 0) { terminalPreferences.setTerminalThemeName(any()) }
    }

    @Test
    fun `removing the terminal theme in use goes back to following the app`() = runTest {
        viewModel().execute(GeneralSettingsAction.RemoveTerminalTheme("nord"))

        coVerify { corePreferences.deleteTerminalTheme("nord") }
        coVerify { terminalPreferences.setTerminalThemeName(null) }
    }

    @Test
    fun `removing another terminal theme leaves the one in use alone`() = runTest {
        viewModel().execute(GeneralSettingsAction.RemoveTerminalTheme("other"))

        coVerify(exactly = 0) { terminalPreferences.setTerminalThemeName(any()) }
    }

    @Test
    fun `an unreadable file is reported`() = runTest {
        val viewModel = viewModel()

        viewModel.execute(GeneralSettingsAction.ImportTheme("/does/not/exist.json"))

        val error = viewModel.uiState.first { it.themeImportError != null }.themeImportError
        assertContains(error.orEmpty(), "Couldn't read the file")
    }

    @Test
    fun `an invalid theme is reported`() = runTest {
        val file = File.createTempFile("theme", ".json").apply { deleteOnExit() }
        coEvery { corePreferences.importUserTheme(any()) } throws IllegalArgumentException("dark.primary is bad")
        val viewModel = viewModel()

        viewModel.execute(GeneralSettingsAction.ImportTheme(file.path))

        val error = viewModel.uiState.first { it.themeImportError != null }.themeImportError
        assertContains(error.orEmpty(), "dark.primary is bad")
    }

    @Test
    fun `removing the theme the terminal uses makes it follow the app again`() = runTest {
        viewModel().execute(GeneralSettingsAction.RemoveUserTheme("nord"))

        coVerify { corePreferences.deleteUserTheme("nord") }
        coVerify { terminalPreferences.setTerminalThemeName(null) }
    }
}
