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

include("feature:newtab:data")
include("feature:newtab:domain")
include("feature:newtab:ui")

// core
include(":core:ui")
include("core:tabs")

include("core:common")
include(":core:data")
include("core:domain")
