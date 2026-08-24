plugins {
    alias(libs.plugins.project.kotlin.library)
    alias(libs.plugins.project.sqldelight)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    //
    implementation(libs.datastore.core)
}
