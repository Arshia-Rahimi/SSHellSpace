package com.github.arshia_rahimi.core.ui

import androidx.compose.runtime.Composable

interface Tab {
    val id: TabId
    val title: String @Composable get

    @Composable
    fun content()
}

typealias TabId = String
