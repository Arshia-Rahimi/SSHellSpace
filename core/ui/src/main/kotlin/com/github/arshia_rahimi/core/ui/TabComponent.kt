package com.github.arshia_rahimi.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.StringResource

interface TabComponent {
    val title: StateFlow<StringResource>
    val icon: StateFlow<Painter?>

    @Composable
    fun content(modifier: Modifier)
}
