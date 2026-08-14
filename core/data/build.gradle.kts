plugins {
    alias(libs.plugins.project.kotlin.library)
}

dependencies {
    implementation(projects.core.database)
    api(projects.core.model)
}
