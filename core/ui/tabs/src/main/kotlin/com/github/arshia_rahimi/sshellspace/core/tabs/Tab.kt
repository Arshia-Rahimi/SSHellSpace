package com.github.arshia_rahimi.sshellspace.core.tabs

import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface Tab {
    val id: TabId
    var title: String
    var icon: ImageVector?
}

typealias TabId = String

@OptIn(ExperimentalUuidApi::class)
fun generateTabId() = Uuid.generateV7().toString()
