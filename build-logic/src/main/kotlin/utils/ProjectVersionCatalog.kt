package utils

import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.getByType

class ProjectVersionCatalog(private val project: Project) {
    val libs = project.extensions.getByType<VersionCatalogsExtension>().named("libs")

    fun getVersion(name: String) = libs.findVersion(name).get().requiredVersion.toInt()

    fun applyPlugin(vararg pluginName: String) = pluginName.toSet().forEach {
        project.apply(plugin = it)
    }

    fun applyProjectPlugin(vararg pluginName: String) = pluginName.toSet().forEach {
        val pluginId = libs.findPlugin(it).get().get().pluginId
        project.pluginManager.apply(pluginId)
    }

    fun addDependency(vararg dependency: Pair<String, String>) = dependency.toSet().forEach {
        project.dependencies.add(it.first, libs.findLibrary(it.second).get())
    }

    fun addBom(vararg bomName: String) {
        with(project.dependencies) {
            bomName.forEach {
                add("implementation", platform(libs.findLibrary(it).get().get()))
            }
        }
    }
}

val Project.versionCatalog
    get() = ProjectVersionCatalog(this)
