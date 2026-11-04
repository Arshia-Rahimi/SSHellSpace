import dev.nucleusframework.desktop.application.dsl.NativeImageOptimization
import dev.nucleusframework.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.project.compose.library)
    alias(libs.plugins.nucleus)
    alias(libs.plugins.sqldelight)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.ui)
    implementation(projects.core.tabs)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    //
    implementation(projects.feature.vault.ui)
    implementation(projects.feature.vault.data)
    implementation(projects.feature.vault.domain)
    //
    implementation(projects.feature.settings.ui)
    implementation(projects.feature.settings.data)
    implementation(projects.feature.settings.domain)
    //
    implementation(projects.feature.terminal.ui)
    implementation(projects.feature.terminal.data)
    implementation(projects.feature.terminal.domain)
    //
    implementation(projects.feature.newtab.ui)
    implementation(projects.feature.newtab.data)
    implementation(projects.feature.newtab.domain)
    //
    implementation(compose.desktop.currentOs)
    implementation(libs.sqldelight.desktop)
    implementation(libs.appdirs)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.tao)
}

// What ships is pinned exactly, so a dependency can't change version without showing up in a diff.
configurations.runtimeClasspath {
    resolutionStrategy.activateDependencyLocking()
}

nucleus.application {
    mainClass = "com.sshellspace.MainKt"

    // Stops other programs attaching to the JVM to read decrypted secrets, and crashes leaving them in a core dump.
    jvmArgs += listOf("-XX:+DisableAttachMechanism", "-XX:-CreateCoredumpOnCrash")

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
            packageName.set("com.sshellspace.generated")
        }
    }
}
