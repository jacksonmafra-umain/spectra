rootProject.name = "Spectra"
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

        // Meta ships the Device Access Toolkit through GitHub Packages. Needs a
        // personal access token with read:packages. Put `github_token=ghp_...`
        // in local.properties (gitignored) or export GH_TOKEN before building
        // anything that touches the real Android backend.
        maven {
            url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
            credentials {
                username = ""
                password = providers.gradleProperty("github_token").orNull
                    ?: System.getenv("GH_TOKEN")
                    ?: ""
            }
            content { includeGroup("com.meta.wearable") }
        }
    }
}

include(":androidApp")
include(":shared")