plugins {
    alias(libs.plugins.project.kotlin.library)
    alias(libs.plugins.sqldelight)
}

dependencies {
    implementation(projects.core.domain.model)
    implementation(projects.core.common)
    //
    implementation(libs.sqldelight.coroutines)
    implementation(libs.sqldelight.desktop)
    implementation(libs.appdirs)
}

sqldelight {
    databases {
        create("SSHellSpaceDatabase") {
            packageName.set("com.github.arshia_rahimi.core.data.database.generated")
        }
    }
}
