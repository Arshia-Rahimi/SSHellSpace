plugins {
    alias(libs.plugins.project.compose.library)
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.tabs)
    implementation(projects.core.domain)
    implementation(projects.feature.terminal.domain)
    //
    implementation(libs.bossterm.compose)
    implementation(libs.bossterm.core)
    implementation(libs.pty4j)
    implementation(libs.filekit.core)
    implementation(libs.filekit.dialogs)
}
