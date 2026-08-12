import org.gradle.api.Plugin
import org.gradle.api.Project
import utils.configureGraphTasks

// from nowinandroid
abstract class RootPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target.path == ":")
        target.subprojects { configureGraphTasks() }
    }
}
