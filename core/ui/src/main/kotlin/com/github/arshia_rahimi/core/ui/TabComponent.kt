package com.github.arshia_rahimi.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.github.arshia_rahimi.core.model.Tab

interface TabComponent: Tab {
    val title: String @Composable get
    val icon: Painter? @Composable get

    @Composable
    fun content(modifier: Modifier)
}
