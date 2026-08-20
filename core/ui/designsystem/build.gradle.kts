plugins {
    alias(libs.plugins.project.compose.library)
}

dependencies {
    implementation(projects.core.ui.tabs)
    implementation(projects.core.ui.commonCompose)
    implementation(projects.core.domain)
    //
    implementation(libs.compose.icons)
    implementation(libs.reorderable)
    implementation(libs.nucleus.application)
    implementation(libs.nucleus.tao)
}
