package com.github.arshia_rahimi.core.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Preferences(
    val theme: Theme = Theme(),
)
