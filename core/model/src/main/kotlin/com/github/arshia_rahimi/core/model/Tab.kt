package com.github.arshia_rahimi.core.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface Tab {
    val id: TabId
    val name: String
}

typealias TabId = String

@OptIn(ExperimentalUuidApi::class)
fun generateTabId(): TabId = Uuid.generateV7().toString()
