plugins {
    alias(libs.plugins.project.kotlin.library)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.feature.vault.domain)
}
