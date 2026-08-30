import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import utils.versionCatalog

class KotlinLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.run {
        versionCatalog.applyPlugin(
            "org.jetbrains.kotlin.jvm",
            "org.jetbrains.kotlin.plugin.serialization",
            "dev.detekt",
            "io.insert-koin.compiler.plugin",
        )

        extensions.configure<KotlinJvmExtension> {
            jvmToolchain(25)
        }

        versionCatalog.addDependency(
            "implementation" to "coroutines-core",
            "implementation" to "koin-core",
            "implementation" to "koin-annotations",
            "implementation" to "kotlinx-serialization-json",
            "implementation" to "kotlinx-datetime",
            "implementation" to "kotlinx-coroutines-swing",
            "testImplementation" to "coroutines-test",
            "testImplementation" to "junit-jupiter",
            "testRuntimeOnly" to "junit-platform-launcher",
            "testImplementation" to "kotlin-test",
            "testImplementation" to "mockk",
        )

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        extensions.configure<DetektExtension> {
            config.setFrom(files("$rootDir/detekt.yml"))
        }
    }
}
