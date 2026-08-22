package com.github.arshia_rahimi.sshellspace.core.ui.tabs

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

typealias TabId = String
typealias TabViewModelFactory = (Tab) -> TabViewModel

interface Tab

fun interface TabViewModelFactoryEntry<T : Tab> {
    fun create(tab: Tab): TabViewModel
}

@OptIn(ExperimentalUuidApi::class)
internal fun generateTabId() = Uuid.generateV7().toString()
