package com.github.arshia_rahimi.sshellspace.tabs

import com.github.arshia_rahimi.sshellspace.Tab
import com.github.arshia_rahimi.sshellspace.feature.newtab.NewTabComponent
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class NewTab(
    override var component: NewTabComponent,
    override val id: String,
): Tab<NewTabComponent>

class NewTabFactory(
    private val createComponent: () -> NewTabComponent,
) {
    fun create() = NewTab(createComponent(), generateId())

    @OptIn(ExperimentalUuidApi::class)
    private fun generateId() = Uuid.generateV7().toString()
}
