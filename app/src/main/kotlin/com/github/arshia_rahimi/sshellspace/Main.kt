package com.github.arshia_rahimi.sshellspace

import com.github.arshia_rahimi.core.compose_utils.asString
import com.github.arshia_rahimi.sshellspace.di.initKoin
import com.github.arshia_rahimi.sshellspace.ui.AppTitleBar
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import sshellspace.app.generated.resources.Res
import sshellspace.app.generated.resources.title

fun main() {
    initKoin()

    nucleusApplication(backend = NucleusBackend.Tao) {
        DecoratedWindow(
            onCloseRequest = ::exitApplication,
            title = Res.string.title.asString(),
        ) {
            AppTitleBar()

        }
    }
}
