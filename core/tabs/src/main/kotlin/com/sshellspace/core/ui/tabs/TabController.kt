package com.sshellspace.core.ui.tabs

import androidx.compose.ui.graphics.vector.ImageVector

interface TabController {
    fun setTitle(title: String)
    fun setIcon(icon: ImageVector?)
}
