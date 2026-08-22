package com.github.arshia_rahimi.sshellspace.core.ui.tabs

import kotlin.reflect.KClass

data class Tab(
    val qualifier: String,
    val viewModelKClass: KClass<*>,
)

interface TabInstance {
    val tab: Tab
}
