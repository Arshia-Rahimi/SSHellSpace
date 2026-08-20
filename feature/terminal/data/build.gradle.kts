plugins {
    alias(libs.plugins.project.kotlin.library)
}

dependencies {
    implementation(projects.feature.terminal.domain)
}
