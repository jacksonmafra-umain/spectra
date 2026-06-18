import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    // Makes the module publishable (publishToMavenLocal / a remote Maven), so a
    // developer can depend on `com.umain.spectra:spectra` without this repo's demo.
    `maven-publish`
}

// Coordinates of the published artifact: com.umain.spectra:spectra:<version>.
group = "com.umain.spectra"
version = "0.2.0"

kotlin {
    // Bundles both iOS slices (device + simulator) into a single Spectra.xcframework.
    // Output: build/XCFrameworks/release/Spectra.xcframework — this is what the
    // Swift Package (Package.swift, binaryTarget) ships to SPM consumers.
    val xcf = XCFramework("Spectra")
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Spectra"
            isStatic = true
            // Spectra's iOS code pulls in platform deps, so Kotlin/Native can no
            // longer infer a bundle id for the framework — set it explicitly.
            binaryOption("bundleId", "com.umain.spectra")
            xcf.add(this)
        }
    }

    androidLibrary {
        namespace = "com.umain.spectra.lib"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            // The real Meta SDK — the Android backend delegates to these.
            // Resolves only with a GitHub Packages token (see settings.gradle.kts).
            implementation(libs.mwdat.core)
            implementation(libs.mwdat.camera)
            implementation(libs.mwdat.display)
        }
    }
}

publishing {
    // The KMP + maven-publish plugins auto-create the publications
    // (kotlinMultiplatform metadata, android, iosArm64, iosSimulatorArm64).
    // We only need to declare WHERE they go: this repo's GitHub Packages.
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/jacksonmafra-umain/spectra")
            credentials {
                // GitHub username + a PAT with write:packages. Reuses the same
                // local.properties / env vars as the Meta SDK read token.
                username = providers.gradleProperty("github_user").orNull
                    ?: System.getenv("GITHUB_ACTOR")
                    ?: ""
                password = providers.gradleProperty("github_token").orNull
                    ?: System.getenv("GH_TOKEN")
                    ?: System.getenv("GITHUB_TOKEN")
                    ?: ""
            }
        }
    }
    // Optional but recommended metadata shown on the Packages page.
    publications.withType<MavenPublication> {
        pom {
            name.set("Spectra")
            description.set("KMP wrapper around the Meta Wearables Device Access Toolkit")
            url.set("https://github.com/jacksonmafra-umain/spectra")
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://github.com/jacksonmafra-umain/spectra/blob/main/LICENSE")
                }
            }
        }
    }
}
