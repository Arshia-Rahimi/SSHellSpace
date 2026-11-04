package com.sshellspace.feature.vault.settings

import com.sshellspace.core.domain.models.DEFAULT_COLOR_SCHEME_ID
import com.sshellspace.core.domain.models.ThemePreference
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
        mockk<VaultManager> { every { activeVault } returns MutableStateFlow(null) },
    )

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
