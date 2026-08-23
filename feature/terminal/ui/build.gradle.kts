plugins {
    alias(libs.plugins.project.compose.library)
}

dependencies {
    implementation(projects.core.ui.tabs)
    implementation(projects.feature.terminal.domain)
    //
    implementation(libs.bossterm.compose)
    implementation(libs.bossterm.core)
    implementation(libs.pty4j)
}
