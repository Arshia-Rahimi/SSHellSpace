package com.sshellspace.core.domain.models

import kotlinx.serialization.Serializable

/** Settings that belong to one vault and are stored inside it. */
@Serializable
data class VaultPreference(
    /** Idle minutes before this vault locks itself. 0 turns that off, including locking on sleep. */
    val autoLockMinutes: Int? = null,
)
