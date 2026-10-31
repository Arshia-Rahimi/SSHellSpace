package com.sshellspace.core.ui.shortcuts

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import com.sshellspace.core.domain.models.KeyChord
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Window-level shortcut dispatcher. Feed it every key event through [onKeyEvent] (from the window's
 * `onPreviewKeyEvent`) so shortcuts fire before focused content such as the terminal sees them.
 */
class ShortcutHandler(
    private val preferencesRepository: CorePreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val actions = ConcurrentHashMap<AppShortcut, () -> Unit>()

    /** The effective chord of every shortcut: the user's override, or the default. */
    val bindings: StateFlow<Map<AppShortcut, KeyChord>> = preferencesRepository.getShortcuts()
        .map(::resolveBindings)
        .stateIn(scope, SharingStarted.Eagerly, resolveBindings(emptyMap()))

    /** While true, no shortcut fires. Set while recording a new chord in Settings. */
    @Volatile
    var isPaused: Boolean = false

    fun register(shortcut: AppShortcut, action: () -> Unit) {
        actions[shortcut] = action
    }

    fun unregister(shortcut: AppShortcut) {
        actions.remove(shortcut)
    }

    fun onKeyEvent(event: KeyEvent): Boolean {
        if (isPaused || event.type != KeyEventType.KeyDown) return false
        val chord = event.toKeyChord() ?: return false
        val shortcut = bindings.value.entries.firstOrNull { it.value == chord }?.key ?: return false
        val action = actions[shortcut] ?: return false
        action()
        return true
    }

    suspend fun setChord(shortcut: AppShortcut, chord: KeyChord) {
        preferencesRepository.setShortcut(shortcut.id, chord.takeIf { it != shortcut.defaultChord })
    }

    suspend fun resetChord(shortcut: AppShortcut) {
        preferencesRepository.setShortcut(shortcut.id, null)
    }

    private fun resolveBindings(overrides: Map<String, KeyChord>) =
        AppShortcut.entries.associateWith { overrides[it.id] ?: it.defaultChord }
}
