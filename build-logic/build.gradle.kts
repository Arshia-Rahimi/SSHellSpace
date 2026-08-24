plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.kotlin.plugin)
    implementation(libs.kotlin.serialization.plugin)
    implementation(libs.kotlin.compose.plugin)
    implementation(libs.compose.multiplatform.plugin)
    implementation(libs.detekt.plugin)
    implementation(libs.koin.plugin)
    implementation(libs.sqldelight.plugin)
}

gradlePlugin {
    plugins {
        register("Root") {
            id = libs.plugins.project.root.get().pluginId
            implementationClass = "RootPlugin"
        }
        register("Kotlin") {
            id = libs.plugins.project.kotlin.library.get().pluginId
            implementationClass = "KotlinLibraryPlugin"
        }
        register("Compose") {
            id = libs.plugins.project.compose.library.get().pluginId
            implementationClass = "ComposeLibraryPlugin"
        }
        register("Sqldelight") {
            id = libs.plugins.project.sqldelight.get().pluginId
            implementationClass = "SqldelightPlugin"
        }
    }
}
