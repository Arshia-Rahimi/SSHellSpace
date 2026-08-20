plugins {
    alias(libs.plugins.project.compose.library)
}

dependencies {
    implementation(projects.core.ui.tabs)
    implementation(projects.feature.terminal.domain)
}
