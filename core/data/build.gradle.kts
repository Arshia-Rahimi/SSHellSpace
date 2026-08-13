plugins {
    alias(libs.plugins.project.kotlin.library)
}

dependencies {
    implementation(projects.core.database)
    implementation(projects.core.model)
}
