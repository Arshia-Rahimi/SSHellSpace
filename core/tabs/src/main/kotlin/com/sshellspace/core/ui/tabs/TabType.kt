package com.sshellspace.core.ui.tabs

interface TabType {
    val isSingleInstance: Boolean
        get() = false

    val qualifier: String
        get() = this::class.qualifiedName!!
}
