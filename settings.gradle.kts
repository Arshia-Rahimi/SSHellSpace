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
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// feature
include(":feature:vault:ui")
include(":feature:vault:domain")
include(":feature:vault:data")

include(":feature:terminal:domain")
include(":feature:terminal:ui")
include(":feature:terminal:data")

include(":feature:settings:domain")
include(":feature:settings:ui")
include(":feature:settings:data")

// core
include(":core:ui:common-compose")
include(":core:ui:designsystem")
include("core:ui:tabs")

include("core:common")
include(":core:data")
include(":core:database")
include("core:domain")
