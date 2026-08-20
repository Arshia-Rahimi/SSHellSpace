package com.github.arshia_rahimi.sshellspace.core.ui.tabs

import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

typealias TabId = String
typealias TabViewModelFactory = (TabType) -> TabViewModel

interface Tab {
    val id: TabId
    var title: String
    var icon: ImageVector?
}

interface TabType

data class TabFactoryEntry(
    val type: KClass<out TabType>,
    val create: (TabType) -> TabViewModel
)

@OptIn(ExperimentalUuidApi::class)
internal fun generateTabId() = Uuid.generateV7().toString()
