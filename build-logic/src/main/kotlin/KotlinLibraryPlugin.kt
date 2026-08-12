import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
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
            "implementation" to "kotlinx-serialization-json",
            "implementation" to "kotlinx-datetime",
            "implementation" to "kotlinx-coroutines-swing",
            "testImplementation" to "coroutines-test",
            "testImplementation" to "junit",
            "testImplementation" to "kotlin-test",
            "testImplementation" to "mockk",
        )
    }
}
