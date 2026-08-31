import app.cash.sqldelight.gradle.SqlDelightExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.invoke
import utils.versionCatalog

class SqldelightPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.run {
        versionCatalog.applyPlugin("app.cash.sqldelight")
        versionCatalog.addDependency("implementation" to "sqldelight-coroutines")

        val modulePath = project.path.removePrefix(":").replace(":", ".")
        extensions.configure<SqlDelightExtension> {
            databases {
                create("Database") {
                    packageName.set("com.sshellspace.$modulePath.generated")
                }
            }
        }
    }
}
