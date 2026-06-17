// Root build script. It does almost nothing, which is exactly what a root build
// script should do. Every plugin is declared here and applied where it's wanted,
// so nobody downloads the Android Gradle Plugin just to read a string.
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}
