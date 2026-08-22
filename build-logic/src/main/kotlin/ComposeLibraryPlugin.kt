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
            "implementation" to "compose-ui-tooling",
            "implementation" to "compose-runtime",
            "implementation" to "compose-foundation",
            "implementation" to "compose-ui",
            "implementation" to "compose-components-resources",
            "implementation" to "compose-ui-tooling-preview",
            "implementation" to "composeunstyled-theming",
            "implementation" to "composeunstyled-button",
            "implementation" to "composeunstyled-text-field",
            "implementation" to "composeunstyled-platformtheme",
            "implementation" to "composeunstyled-dialog",
            "implementation" to "composeunstyled-avatar",
            "implementation" to "composeunstyled-checkbox",
            "implementation" to "composeunstyled-tri-state-checkbox",
            "implementation" to "composeunstyled-disclosure",
            "implementation" to "composeunstyled-dropdown-menu",
            "implementation" to "composeunstyled-icon",
            "implementation" to "composeunstyled-progress",
            "implementation" to "composeunstyled-radio-group",
            "implementation" to "composeunstyled-separators",
            "implementation" to "composeunstyled-slider",
            "implementation" to "composeunstyled-tab-group",
            "implementation" to "composeunstyled-toggle-switch",
            "implementation" to "composeunstyled-tooltip",
            "implementation" to "composeunstyled-escape-handler",
            "implementation" to "composeunstyled-modal",
        )
    }
}
