package com.sshellspace.core.domain.models

import kotlinx.serialization.Serializable

/** The main window as it was last left, so the next start opens the same way. Sizes are in dp. */
@Serializable
data class SavedWindow(
    /** The size when neither maximized nor full screen, which un-maximizing goes back to. */
    val width: Float,
    val height: Float,
    val placement: Placement = Placement.Floating,
) {
    enum class Placement { Floating, Maximized, Fullscreen }
}
