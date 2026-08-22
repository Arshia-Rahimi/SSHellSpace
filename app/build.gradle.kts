import dev.nucleusframework.desktop.application.dsl.NativeImageOptimization
import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.project.compose.library)
    alias(libs.plugins.nucleus)
}

dependencies {
    implementation(projects.core.ui.commonCompose)
    implementation(projects.core.ui.designsystem)
    implementation(projects.core.ui.tabs)
    implementation(projects.core.domain)
    implementation(projects.core.database)
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
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.tao)
}

nucleus.application {
    mainClass = "com.github.arshia_rahimi.sshellspace.MainKt"

    graalvm {
        isEnabled = true
        imageName = "SSHellSpace"
        optimization = NativeImageOptimization.SIZE
    }

    nativeDistributions {
        targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.AppImage)
        packageName = "com.github.arshia_rahimi.sshellspace"
        packageVersion = "0.1.0"
    }
}
