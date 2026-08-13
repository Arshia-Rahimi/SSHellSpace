package com.github.arshia_rahimi.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface Tab {
    val title: String @Composable get

    @Composable
    fun content(modifier: Modifier)
}
