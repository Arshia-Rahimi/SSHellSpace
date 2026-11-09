package com.sshellspace.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.sshellspace.core.domain.models.SavedWindow
import com.sshellspace.core.domain.repositories.CorePreferencesRepository
import com.sshellspace.core.ui.designsystem.adaptive.DefaultWindowSize
import com.sshellspace.core.ui.designsystem.adaptive.MinWindowSize
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.koin.core.Koin

/** Resizing sends a stream of sizes, and only where it stops matters. */
private const val SAVE_DELAY_MILLIS = 500L

/** How the window was last left. Read before the window opens, since its size and placement can't wait for it. */
fun Koin.savedWindow(): SavedWindow? = runBlocking { get<CorePreferencesRepository>().getWindow().first() }

/** Opens the window as it was last left, or at the default size the first time. */
@Composable
fun rememberMainWindowState(saved: SavedWindow?): WindowState = rememberWindowState(
    placement = when (saved?.placement) {
        SavedWindow.Placement.Maximized -> WindowPlacement.Maximized
        SavedWindow.Placement.Fullscreen -> WindowPlacement.Fullscreen
        SavedWindow.Placement.Floating, null -> WindowPlacement.Floating
    },
    size = saved?.floatingSize() ?: DefaultWindowSize,
)

/**
 * Saves the window's size and placement as they change. A maximized window's size is the screen's, so the size kept is
 * the last one it had while floating, which is what un-maximizing goes back to.
 */
@OptIn(FlowPreview::class)
@Composable
fun SaveMainWindow(state: WindowState, saved: SavedWindow?, preferences: CorePreferencesRepository) {
    LaunchedEffect(state) {
        var floating = saved?.floatingSize() ?: DefaultWindowSize
        snapshotFlow { state.placement to state.size }
            .onEach { (placement, size) ->
                if (placement == WindowPlacement.Floating && size.isSpecified) floating = size
            }
            .debounce(SAVE_DELAY_MILLIS)
            .collect { (placement, _) ->
                preferences.setWindow(
                    SavedWindow(
                        width = floating.width.value,
                        height = floating.height.value,
                        placement = when (placement) {
                            WindowPlacement.Floating -> SavedWindow.Placement.Floating
                            WindowPlacement.Maximized -> SavedWindow.Placement.Maximized
                            WindowPlacement.Fullscreen -> SavedWindow.Placement.Fullscreen
                        },
                    ),
                )
            }
    }
}

/** Never below the window's minimum, in case the saved row was edited or damaged. */
private fun SavedWindow.floatingSize(): DpSize? =
    DpSize(width.dp, height.dp)
        .takeIf { width.isFinite() && height.isFinite() }
        ?.let { DpSize(maxOf(it.width, MinWindowSize.width), maxOf(it.height, MinWindowSize.height)) }
