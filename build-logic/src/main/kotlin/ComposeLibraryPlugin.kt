import org.gradle.api.Plugin
import org.gradle.api.Project
import utils.versionCatalog

class ComposeLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        versionCatalog.applyProjectPlugin("project-kotlin-library")

        versionCatalog.applyPlugin(
            "org.jetbrains.kotlin.plugin.compose",
            "org.jetbrains.compose",
        )

        versionCatalog.addDependency(
            "implementation" to "koin-compose",
            "implementation" to "koin-compose-navigation3",
            "implementation" to "compose-ui-tooling",
            "implementation" to "compose-runtime",
            "implementation" to "compose-foundation",
            "implementation" to "compose-material3",
            "implementation" to "compose-ui",
            "implementation" to "compose-components-resources",
            "implementation" to "compose-ui-tooling-preview",
            "implementation" to "navigation3-ui",
            "implementation" to "kolor",
        )
    }
}
