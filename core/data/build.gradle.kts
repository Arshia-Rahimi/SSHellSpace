plugins {
    alias(libs.plugins.project.kotlin.library)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
}
