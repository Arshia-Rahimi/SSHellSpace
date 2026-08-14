package com.github.arshia_rahimi.sshellspace.tabs

import com.github.arshia_rahimi.core.ui.TabComponent
import com.github.arshia_rahimi.sshellspace.Tab
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import org.koin.core.annotation.Named

class NewTab(
    override var component: TabComponent,
): Tab {
    @OptIn(ExperimentalUuidApi::class)
    override val id = Uuid.generateV7().toString()
}

class NewTabFactory(
    @Named("New") private val component: TabComponent,
) {
    fun create() = NewTab(component)
}
