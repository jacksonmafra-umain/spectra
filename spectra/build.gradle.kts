import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    // Turns the module into something you can `publishToMavenLocal` and then
    // consume from another project via mavenLocal(). The Kotlin Multiplatform
    // plugin wires up the actual publications for us once this is applied.
    `maven-publish`
}

// Coordinates for the published artifact: com.umain.spectra:spectra:<version>.
// Version is single-sourced from the catalog so there's exactly one place to
// bump it, and no chance of the docs and the build disagreeing.
group = "com.umain.spectra"
version = libs.versions.spectra.get()

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
        }
        publishLibraryVariants("release")
    }

    // The three iOS targets you actually need. If you were hoping for watchOS,
    // the glasses go on your face, not your wrist. Different product.
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            // NOTE: the real Meta SDK (mwdat-core/-camera/-display) is intentionally
            // NOT a dependency here. The Android backend ships as a documented
            // skeleton so the whole library builds and publishes with no GitHub
            // token. To wire the real thing, follow spectra/android-reference/ and
            // re-add the mwdat artifacts (see that folder's README).
        }
    }
}

android {
    namespace = "com.umain.spectra"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
