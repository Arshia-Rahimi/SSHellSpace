package com.sshellspace.core.ui.tabs

interface Tab {
    val qualifier: String
        get() = this::class.qualifiedName!!
}

interface TabInstance {
    val tab: Tab
}
