plugins {
    alias(libs.plugins.project.compose.library)
}

dependencies {
    implementation(projects.core.tabs)
    implementation(projects.core.ui)
    implementation(projects.feature.settings.domain)
}
