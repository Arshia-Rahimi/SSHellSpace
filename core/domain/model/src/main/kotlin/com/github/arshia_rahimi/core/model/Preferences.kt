package com.github.arshia_rahimi.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Preferences(
    val theme: Theme = Theme(),
)
