package com.sshellspace.core.domain.models

import kotlinx.serialization.Serializable

/**
 * A key combination. [keyCode] is the Compose `Key.keyCode` of the non-modifier key.
 */
@Serializable
data class KeyChord(
    val keyCode: Long,
    val meta: Boolean = false,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
)
