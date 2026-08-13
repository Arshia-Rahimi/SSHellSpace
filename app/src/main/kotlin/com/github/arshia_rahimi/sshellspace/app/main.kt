package com.github.arshia_rahimi.sshellspace.app

import androidx.compose.material3.Text
import com.github.arshia_rahimi.core.compose_utils.asString
import com.github.arshia_rahimi.sshellspace.app.di.initKoin
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.TitleBar
import sshellspace.app.generated.resources.Res
import sshellspace.app.generated.resources.title

fun main() {
    initKoin()

    nucleusApplication(backend = NucleusBackend.Tao) {
        DecoratedWindow(
            onCloseRequest = ::exitApplication,
            title = Res.string.title.asString(),
        ) {
            TitleBar {
                Text(Res.string.title.asString())
            }

        }
    }
}
