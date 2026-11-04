rootProject.name = "VtuberCamera_KMP_ver"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    // Kotlin 2.4 requires D8/R8 9.1.29; AGP 8.13.2 bundles 8.13.19.
    // Remove this override when AGP supplies a compatible D8/R8 version.
    buildscript {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath("com.android.tools:r8:9.1.29")
        }
    }
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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

include(":composeApp")
