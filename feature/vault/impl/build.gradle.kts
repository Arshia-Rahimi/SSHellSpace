plugins {
    alias(libs.plugins.project.compose.library)
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.composeutils)
}
