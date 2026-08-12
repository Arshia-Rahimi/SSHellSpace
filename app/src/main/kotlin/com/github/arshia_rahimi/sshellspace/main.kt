package com.github.arshia_rahimi.sshellspace

import androidx.compose.material3.Text
import dev.nucleusframework.application.NucleusBackend
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.TitleBar
import dev.nucleusframework.window.material.MaterialDecoratedWindow

fun main() {
    initKoin()

    nucleusApplication(backend = NucleusBackend.Tao) {
        MaterialDecoratedWindow(
            onCloseRequest = ::exitApplication,
            title = "SSHellSpace",
        ) {
            TitleBar {
                Text("SSHellSpace")
            }

        }
    }
}
