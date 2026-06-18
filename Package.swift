// swift-tools-version:5.9
import PackageDescription

// SPM distribution for the Spectra KMP library.
//
// This points at a prebuilt Spectra.xcframework.zip attached to a GitHub
// Release. To cut a new version:
//   1. ./gradlew :spectra:assembleSpectraReleaseXCFramework   (in demo/)
//   2. zip the framework, run `swift package compute-checksum`
//   3. paste the new checksum + version-tagged URL below
//   4. commit, tag (e.g. 0.2.0), push, and upload the zip to that release
let package = Package(
    name: "Spectra",
    platforms: [
        .iOS(.v13)
    ],
    products: [
        .library(name: "Spectra", targets: ["Spectra"])
    ],
    targets: [
        .binaryTarget(
            name: "Spectra",
            url: "https://github.com/jacksonmafra-umain/spectra/releases/download/0.2.0/Spectra.xcframework.zip",
            checksum: "REPLACE_WITH_CHECKSUM_FROM_STEP_2"
        )
    ]
)
