package com.sshellspace.core.ui.tabs

interface Tab {
    val isSingleInstance: Boolean
        get() = false

    val qualifier: String
        get() = this::class.qualifiedName!!
}

interface TabInstance {
    val tab: Tab
}
