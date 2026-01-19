rootProject.name = "cloak"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// settings.gradle.kts (Main Project)
rootProject.name = "cloak"

includeBuild("mpv-kt") {
    // 1. Give it a unique build name to avoid the "same name" error
    name = "mpv-kt"

    // 2. Map the coordinate "dev.zt64.mpvkt:mpvkt" to the local ":mpv" project
    dependencySubstitution {
        substitute(module("dev.zt64.mpvkt:mpvkt")).using(project(":mpv"))
    }
}

include(":composeApp")
include(":composeApp")
