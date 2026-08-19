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
include(":feature:vault:api")
include(":feature:vault:impl")
include(":feature:terminal:api")
include(":feature:terminal:impl")
include(":feature:settings:api")
include(":feature:settings:impl")
include("feature:newtab:impl")

// core
include(":core:common-compose")
include("core:common")
include(":core:data")
include(":core:model")
include(":core:database")
include(":core:designsystem")
include("core:tabs")
