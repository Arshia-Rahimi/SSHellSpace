import dev.nucleusframework.desktop.application.dsl.NativeImageOptimization
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.project.compose.library)
    alias(libs.plugins.nucleus)
}

dependencies {
    implementation(projects.core.commonCompose)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.data)
    implementation(projects.feature.vault.impl)
    implementation(projects.feature.settings.impl)
    implementation(projects.feature.terminal.impl)
    implementation(projects.feature.newtab.impl)
    implementation(projects.core.tabs)
    //
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.tao)
    implementation(libs.nucleus.material3)
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
