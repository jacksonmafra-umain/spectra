rootProject.name = "spectra"

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()

        // Meta ships the Device Access Toolkit through GitHub Packages, which
        // means you need a personal access token with `read:packages`. Yes, a
        // token to download a public-preview SDK. No, we didn't design that.
        // Drop `github_token` into local.properties (it's gitignored) or export
        // GITHUB_TOKEN before building anything that touches the Android target.
        maven {
            url = uri("https://maven.pkg.github.com/facebook/meta-wearables-dat-android")
            credentials {
                username = "" // GitHub doesn't care about the username here.
                password = providers.gradleProperty("github_token").orNull
                    ?: System.getenv("GITHUB_TOKEN")
                    ?: "" // Empty token = the Android target won't resolve. Mock target still will.
            }
        }
    }
}

include(":spectra")
include(":demo")
