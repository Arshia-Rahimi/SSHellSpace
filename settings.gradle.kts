rootProject.name = "SSHellSpace"

pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()

        exclusiveContent {
            forRepository {
                maven {
                    url = uri("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
                }
            }

            filter {
                includeGroup("org.jetbrains.jediterm")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// feature
include(":feature:vault")
include(":feature:terminal")
include(":feature:settings")

// core
include(":core:compose-utils")
include(":core:data")
include(":core:model")
