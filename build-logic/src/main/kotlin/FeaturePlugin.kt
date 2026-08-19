import org.gradle.api.Plugin
import org.gradle.api.Project
import utils.versionCatalog

class FeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        versionCatalog.applyProjectPlugin("project-compose-library")

        @Suppress("UnstableApiUsage")
        versionCatalog.addProjectDependency(
            "implementation" to dependencies.project(":core:tabs"),
            "implementation" to dependencies.project(":core:common-compose"),
        )
    }
}
