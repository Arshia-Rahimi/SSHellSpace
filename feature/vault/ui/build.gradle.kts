plugins {
    alias(libs.plugins.project.compose.library)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.tabs)
    implementation(projects.core.ui)
    implementation(projects.feature.vault.domain)
    implementation(projects.feature.terminal.domain)
    //
    implementation(libs.filekit.core)
    implementation(libs.filekit.dialogs)
}
