package com.sshellspace.feature.terminal.domain.models

import kotlin.uuid.Uuid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The tabs open in a vault, to bring back the next time it unlocks. */
@Serializable
data class SavedSession(
    val tabs: List<SavedTab> = emptyList(),
    /** Position in [tabs] of the tab in front, or null when it was the vault tab. */
    val currentIndex: Int? = null,
)

@Serializable
sealed interface SavedTab {
    @Serializable
    @SerialName("ssh")
    data class Ssh(val connectionId: Uuid, val title: String) : SavedTab

    @Serializable
    @SerialName("local")
    data class Local(val shell: Shell) : SavedTab
}
