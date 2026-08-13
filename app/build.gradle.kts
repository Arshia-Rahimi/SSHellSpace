import dev.nucleusframework.desktop.application.dsl.NativeImageOptimization
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.project.compose.library)
    alias(libs.plugins.nucleus)
}

dependencies {
    implementation(projects.core.composeUtils)
    implementation(projects.core.ui)
    implementation(projects.feature.vault.impl)
    implementation(projects.feature.settings.impl)
    implementation(projects.feature.terminal.impl)
    //
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.tao)
    implementation(libs.nucleus.material3)
    implementation(libs.jediterm)
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
