package com.github.arshia_rahimi.sshellspace.tabs

import com.github.arshia_rahimi.core.ui.TabComponent
import com.github.arshia_rahimi.sshellspace.Tab
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class NewTab(
    override var component: TabComponent,
    override val id: String,
): Tab

class NewTabFactory(
    private val createComponent: () -> TabComponent,
) {
    fun create() = NewTab(createComponent(), generateId())

    @OptIn(ExperimentalUuidApi::class)
    private fun generateId() = Uuid.generateV7().toString()
}
