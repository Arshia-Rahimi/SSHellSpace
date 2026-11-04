package com.sshellspace.core.ui.tabs

import androidx.compose.ui.graphics.vector.ImageVector

interface TabController {
    fun setTitle(title: String)
    fun setIcon(icon: ImageVector?)

    /**
     * Sets what receives text sent to this tab, such as a snippet. Null when the tab can't take any right now.
     * The Boolean says whether to press Enter after the text.
     */
    fun setTextInput(input: ((String, Boolean) -> Unit)?)
}
