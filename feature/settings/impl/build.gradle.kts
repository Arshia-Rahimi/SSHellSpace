plugins {
    alias(libs.plugins.project.compose.library)
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.composeUtils)
}
