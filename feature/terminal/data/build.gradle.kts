plugins {
    alias(libs.plugins.project.kotlin.library)
}

dependencies {
    implementation(projects.feature.terminal.domain)
    implementation(projects.core.domain)
    //
    implementation(libs.mina.common)
    implementation(libs.mina.sftp)
    implementation(libs.mina.scp)
}
