# Spectra Playground (Android + iOS demo)

A runnable Compose Multiplatform demo of **Spectra** — the Kotlin Multiplatform
wrapper around Meta's Wearables Device Access Toolkit. It walks the whole
integration flow (initialize → register → permit → session → camera stream →
photo → display) against the in-memory `Spectra.mock()`, so it runs on Android
and iOS with **no glasses, no GitHub token, and no Bluetooth**.

## Layout

- `shared/` — the shared module. Holds the Spectra library source
  (`com.umain.spectra.{core,camera,display,mock}` + `Spectra`/`SpectraClient`)
  and the Compose UI (`App`, `PlaygroundState`, `CameraView`, `GlassesPreview`).
  Both apps render `com.umain.spectra.App()`.
- `androidApp/` — the Android entry point (`MainActivity` calls `App()`).
- `iosApp/` — the iOS Xcode project; `ContentView` hosts `MainViewController()`
  from the `Shared` framework.

## Run it

**Android** — install on a device or emulator:

```bash
./gradlew :androidApp:installDebug      # then launch "Spectra"
# or just build the apk:
./gradlew :androidApp:assembleDebug
```

**iOS** — open `iosApp/iosApp.xcodeproj` in Xcode, pick a simulator, and Run.
The framework is built automatically by the bundled
`./gradlew :shared:embedAndSignAppleFrameworkForXcode` build phase. (Set your
Team in `iosApp/Configuration/Config.xcconfig` if you run on a physical device.)

## Using the demo

Tap the buttons top to bottom. Each maps to exactly one Spectra call, and the
status card plus the "glasses display" preview update live. Buttons on the
preview fire the same `onClick` handlers the real glasses would.

## Toolchain

Gradle 9.1, AGP 9.0.1, Kotlin 2.4.0, Compose Multiplatform 1.11.1, compileSdk 36,
minSdk 24. This is the demo; the publishable library and the full documentation
live in the parent project (see `../README.md` and `../docs/`).
