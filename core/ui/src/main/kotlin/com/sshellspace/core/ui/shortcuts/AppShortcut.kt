package com.sshellspace.core.ui.shortcuts

import androidx.compose.ui.input.key.Key
import com.sshellspace.core.domain.models.KeyChord

/**
 * Every app-wide shortcut. To add one, add an entry here and register its action with
 * [ShortcutHandler.register] in the app module; it then shows up in Settings automatically.
 */
enum class AppShortcut(
    val id: String,
    val label: String,
    val defaultChord: KeyChord,
) {
    NewTab(
        id = "new_tab",
        label = "New tab with default shell",
        defaultChord = primaryChord(Key.T),
    ),
    QuickConnect(
        id = "quick_connect",
        label = "Quick connect and snippets",
        defaultChord = primaryChord(Key.K, shift = true),
    ),
    NextTab(
        id = "next_tab",
        label = "Next tab",
        defaultChord = KeyChord(keyCode = Key.Tab.keyCode, ctrl = true),
    ),
    PreviousTab(
        id = "previous_tab",
        label = "Previous tab",
        defaultChord = KeyChord(keyCode = Key.Tab.keyCode, ctrl = true, shift = true),
    ),
    LockVault(
        id = "lock_vault",
        label = "Lock vault",
        defaultChord = primaryChord(Key.L, shift = true),
    ),
}
