import dev.nucleusframework.desktop.application.dsl.NativeImageOptimization
import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.project.compose.library)
    alias(libs.plugins.nucleus)
    alias(libs.plugins.sqldelight)
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.tabs)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    //
    implementation(projects.feature.vault.ui)
    implementation(projects.feature.vault.data)
    //
    implementation(projects.feature.settings.ui)
    implementation(projects.feature.settings.data)
    //
    implementation(projects.feature.terminal.ui)
    implementation(projects.feature.terminal.data)
    //
    implementation(compose.desktop.currentOs)
    implementation(libs.sqldelight.desktop)
    implementation(libs.appdirs)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.tao)
}

nucleus.application {
    mainClass = "com.sshellspace.MainKt"

    graalvm {
        isEnabled = true
        imageName = "SSHellSpace"
        optimization = NativeImageOptimization.SIZE
    }

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.AppImage)
        packageName = "com.sshellspace"
        packageVersion = "0.1.0"
    }
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("com.shellspace.generated")
        }
    }
}
