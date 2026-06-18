import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Read secrets from local.properties (gitignored) first, then a Gradle property,
// then an env var — so nothing sensitive lives in tracked files.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String =
    localProps.getProperty(key)
        ?: providers.gradleProperty(key).orNull
        ?: System.getenv(key.uppercase())
        ?: ""

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)

    // MainActivity drives the Activity-bound registration/permission flow, so it
    // needs the real SDK directly (Wearables + RequestPermissionContract).
    implementation(libs.mwdat.core)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.umain.spectra"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.umain.spectra"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        // Injected into AndroidManifest.xml; real values live in local.properties.
        manifestPlaceholders["mwdat_application_id"] = secret("mwdat_application_id")
        manifestPlaceholders["mwdat_client_token"] = secret("mwdat_client_token")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}