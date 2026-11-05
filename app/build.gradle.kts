import dev.nucleusframework.desktop.application.dsl.NativeImageOptimization
import dev.nucleusframework.desktop.application.dsl.TargetFormat
import utils.GenerateUpdateKeyTask
import utils.SignUpdatesTask

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
    implementation(libs.nucleus.updater)
    implementation(libs.bouncy.castle)
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
        // A macOS update replaces the app from the ZIP. The DMG is only for the first install.
        targetFormats(TargetFormat.Dmg, TargetFormat.Zip, TargetFormat.Nsis, TargetFormat.AppImage)
        packageName = "com.sshellspace"
        packageVersion = "0.1.0"
        // From suggestRuntimeModules. A packaged app only gets the JDK modules listed, and a missing one only fails when reached.
        modules("java.instrument", "java.prefs", "java.rmi", "java.security.jgss", "java.sql", "jdk.javadoc", "jdk.management", "jdk.security.auth")

        // Publishing stays manual, so a release's manifests and signatures go up together. See CLAUDE.md.
        publish {
            github {
                enabled = true
                owner = "Arshia-Rahimi"
                repo = "SSHellSpace"
            }
        }
    }
}

// Bouncy Castle's jar is signed. Its signature doesn't hold for a merged jar, and native-image refuses to load one that fails.
tasks.withType<org.gradle.jvm.tasks.Jar>().matching { it.name.endsWith("UberJarForCurrentOS") }.configureEach {
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}

val updateSigningKey = providers.gradleProperty("updateSigningKey")
    .orElse(providers.environmentVariable("UPDATE_SIGNING_KEY"))
    .orElse(providers.systemProperty("user.home").map { "$it/.sshellspace/update-signing.key" })

tasks.register<GenerateUpdateKeyTask>("generateUpdateSigningKey") {
    group = "distribution"
    description = "Creates the key that signs releases. Only needed once."
    keyFile = updateSigningKey
}

tasks.register<SignUpdatesTask>("signUpdates") {
    group = "distribution"
    description = "Signs the packaged artifacts listed in this OS's update manifests."
    artifactsDir = layout.buildDirectory.dir("compose/binaries")
    keyFile = updateSigningKey
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("com.sshellspace.generated")
        }
    }
}
