import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    // No framework block here: this is a library consumed by other modules (and
    // published), not an app. The final app frameworks live in :shared.
    iosArm64()
    iosSimulatorArm64()

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
