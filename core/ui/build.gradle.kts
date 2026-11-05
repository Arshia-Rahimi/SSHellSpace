plugins {
    alias(libs.plugins.project.compose.library)
}

dependencies {
    implementation(projects.core.tabs)
    implementation(projects.core.domain)
    //
    implementation(libs.compose.icons)
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.tao)
    implementation(libs.nucleus.darkmode.detector)
}
