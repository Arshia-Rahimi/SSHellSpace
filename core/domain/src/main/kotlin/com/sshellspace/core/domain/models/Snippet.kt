package com.sshellspace.core.domain.models

import kotlin.uuid.Uuid

/** A saved command that can be sent to a terminal from the quick connect palette. */
data class Snippet(
    val id: Uuid,
    val name: String,
    val command: String,
    /** Press Enter after sending. Otherwise the command waits on the prompt for the user to review. */
    val runImmediately: Boolean = false,
)
