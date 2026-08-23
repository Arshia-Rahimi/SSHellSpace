package com.sshellspace.core.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Preferences(
    val theme: Theme = Theme(),
)
