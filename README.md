# Spectra

**Meta's glasses SDK, minus the boilerplate and most of the suffering.**

Spectra is a Kotlin Multiplatform wrapper around [Meta's Wearables Device Access Toolkit](https://wearables.developer.meta.com/docs/develop/dat/) (DAT). It collapses two native SDKs (Swift and Kotlin), a pile of callbacks, result builders, and activity-result contracts into a single coroutine-and-`Flow` API you call from `commonMain`.

This repository is three things:

| Name | What it is |
|------|------------|
| **Spectra** | The Kotlin Multiplatform library (`:spectra`). |
| **Spectra Playground** | A Compose Multiplatform demo app (`:demo`) that runs the whole integration on a mock device. |
| **Spectra Docs** | A static, Vercel-ready documentation site (`docs/`) with an `llms.txt` and `AGENTS.md` for AI agents. |

> Independent wrapper. Not affiliated with, sponsored by, or endorsed by Meta. Developer preview — the API will move.

## Why

The toolkit is capable: the wearer's-eye camera, open-ear audio, and a declarative display on Ray-Ban Display glasses. It's also distributed through GitHub Packages behind a token, split across platforms, and full of ways to hold it wrong. Spectra gives you one `SpectraClient`, typed `SpectraError`s instead of inscrutable ones, and a mock that runs everywhere with no hardware.

## Quickstart

```kotlin
val spectra = Spectra.mock()                 // or Spectra.create(context, bridge) on Android

spectra.initialize()
spectra.startRegistration()
spectra.requestPermission(Permission.CAMERA) // a device only appears after this

val device  = spectra.devices.first { it.isNotEmpty() }.first()
val session = spectra.createSession(DeviceSelector.Auto).getOrThrow()
session.start()
session.state.first { it == SessionState.RUNNING }

val stream = session.openCameraStream(
    StreamConfiguration(quality = VideoQuality.LOW, frameRate = FrameRate.FPS_15)
).getOrThrow()
stream.frames.onEach(::render).launchIn(scope)
stream.start()

val photo = stream.capturePhoto().getOrThrow()
```

The one gotcha worth tattooing: **a device will not appear until the user is both registered and has granted a permission.** Everything else follows the fixed sequence above; skip a step and you get a typed error explaining which one.

## Module layout

```
spectra/                         the library
  src/commonMain/com/umain/spectra/
    Spectra.kt, SpectraClient.kt  facade + segregated capability interfaces
    core/                         registration, permissions, devices, session, errors
    camera/                       stream, configuration, frames, photos
    display/                      FlexBox component model + content DSL + display contract
    mock/                         in-memory backend that simulates glasses end to end
  src/androidMain/                skeleton client (builds token-free)
  src/iosMain/                    documented Swift-bridge skeleton
  src/commonTest/                 mock-driven tests doubling as executable docs
  android-reference/              real mwdat delegation (template, not compiled)

demo/                            Spectra Playground (Compose Multiplatform)
  src/commonMain/                shared UI + state, runs on Spectra.mock()
  src/androidMain/               MainActivity + manifest
  src/iosMain/                   MainViewController

docs/                            static site for Vercel (index.html, llms.txt, AGENTS.md)
```

## Build & test

Requires JDK 17, the Android SDK, and (for the iOS targets) Xcode. Use the Gradle **wrapper** — the project is pinned to Gradle 8.10.2, which is what AGP 8.5.2 / Kotlin 2.1.0 / Compose 1.7.1 expect. Gradle 9.x will not work (it removed an internal API that Kotlin Multiplatform still calls). If the wrapper is missing, bootstrap it once with a Gradle 8.x on your PATH: `gradle wrapper --gradle-version 8.10.2`.

```bash
./gradlew :spectra:allTests               # run the mock-driven tests (no token needed)
./gradlew :spectra:compileKotlinMetadata  # compile common code + mock
./gradlew :demo:assembleDebug             # build the Android Playground apk
./gradlew check                           # everything: compile + tests + lint
```

**The entire library builds and publishes with no GitHub token.** Android and iOS both ship as documented skeletons: `Spectra.create(...)` returns a clearly-marked not-yet-wired client, and `Spectra.mock()` runs the full flow on every platform. The Android demo uses the mock, so it builds and runs token-free too:

```bash
./gradlew :demo:installDebug              # then launch "Spectra Playground"
```

To wire the **real Android backend**, follow `spectra/android-reference/` — it contains the delegation to Meta's `mwdat-*` artifacts, corrected against the 0.7 API. You re-add the `mwdat-*` dependencies and supply a GitHub Packages token (`github_token` in `local.properties`, or the `GH_TOKEN` environment variable, read by `settings.gradle.kts`). For **iOS**, finish the `@objc` Swift shim described in `spectra/src/iosMain/.../IosSpectraClient.kt` and wire it via `cinterop`.

## Publish locally

The library is set up for Maven Local, so you can consume Spectra from another project on your machine without a remote repository. Publish it under `com.umain.spectra:spectra:0.1.0`:

```bash
./gradlew :spectra:publishToMavenLocal
```

This publishes the multiplatform metadata plus the Android (`release`) and iOS variants to `~/.m2/repository`. Verify with:

```bash
ls ~/.m2/repository/com/umain/spectra
```

Then, in the consuming project, add `mavenLocal()` (first, so it wins) and depend on Spectra in your shared `commonMain`:

```kotlin
// settings.gradle.kts of the OTHER project
dependencyResolutionManagement {
    repositories {
        mavenLocal()      // resolves the locally published Spectra
        google()
        mavenCentral()
    }
}
```

```kotlin
// shared/build.gradle.kts of the OTHER project
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.umain.spectra:spectra:0.1.0")
        }
    }
}
```

Bump the version once, in `gradle/libs.versions.toml` (`spectra = "..."`); the build coordinates and these docs read from there. Maven Local does not delete old versions, so clear stale ones from `~/.m2/repository/com/umain/spectra` if they accumulate.

## Design notes

Built DRY and SOLID: the public surface is split into small capability interfaces (`Registrar`, `PermissionController`, `DeviceRegistry`, `SessionFactory`) that `SpectraClient` aggregates, so a screen depends only on the slice it uses. Every backend (mock, Android, iOS) implements the same contract, so swapping is a one-line change and the type mappers are the only place that knows Meta's vocabulary.

The KDoc has a sense of humour. The code does not — it behaves exactly like the contracts say.

## Documentation

The site under `docs/` deploys to Vercel with the root directory set to `docs` and no build step. It ships `llms.txt` and `AGENTS.md` so coding agents can write correct integrations without guessing. See `docs/README.md` for deployment.

## License

MIT. See [LICENSE](LICENSE).
