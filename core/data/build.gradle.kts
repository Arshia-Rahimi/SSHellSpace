plugins {
    alias(libs.plugins.project.kotlin.library)
    alias(libs.plugins.sqldelight)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.common)
    //
    implementation(libs.sqldelight.coroutines)
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("com.shellspace.core.data.generated")
        }
    }
}
