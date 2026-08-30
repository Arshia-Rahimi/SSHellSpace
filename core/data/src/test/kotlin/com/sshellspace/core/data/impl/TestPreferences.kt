package com.sshellspace.core.data.impl

import com.sshellspace.core.domain.models.Preference
import kotlinx.serialization.Serializable

@Serializable
internal data class FooPreference(val id: String) : Preference

@Serializable
internal data class BarPreference(val id: String) : Preference
