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
  src/androidMain/                real backend delegating to mwdat-core/-camera/-display
  src/iosMain/                    documented Swift-bridge skeleton
  src/commonTest/                 mock-driven tests doubling as executable docs

demo/                            Spectra Playground (Compose Multiplatform)
  src/commonMain/                shared UI + state, runs on Spectra.mock()
  src/androidMain/               MainActivity + manifest
  src/iosMain/                   MainViewController

docs/                            static site for Vercel (index.html, llms.txt, AGENTS.md)
```

## Building

Requires JDK 17, Android SDK, and (for the iOS targets) Xcode. The Gradle wrapper jar isn't committed; generate it once with a local Gradle: `gradle wrapper --gradle-version 8.10.2`.

```bash
./gradlew :spectra:compileKotlinMetadata   # common code + mock, no token needed
./gradlew :spectra:allTests                 # run the mock-driven tests
./gradlew :demo:assembleDebug               # the Android Playground apk
```

The **real Android backend** resolves from GitHub Packages, which needs a personal access token with `read:packages`. Put `github_token=ghp_...` in `local.properties` (gitignored) or export `GITHUB_TOKEN`. The mock and all common code build and test without it.

For iOS, finish the `@objc` Swift shim described in `spectra/src/iosMain/.../IosSpectraClient.kt` and wire it via `cinterop`. Until then, `Spectra.mock()` runs on iOS, including in previews.

## Design notes

Built DRY and SOLID: the public surface is split into small capability interfaces (`Registrar`, `PermissionController`, `DeviceRegistry`, `SessionFactory`) that `SpectraClient` aggregates, so a screen depends only on the slice it uses. Every backend (mock, Android, iOS) implements the same contract, so swapping is a one-line change and the type mappers are the only place that knows Meta's vocabulary.

The KDoc has a sense of humour. The code does not — it behaves exactly like the contracts say.

## Documentation

The site under `docs/` deploys to Vercel with the root directory set to `docs` and no build step. It ships `llms.txt` and `AGENTS.md` so coding agents can write correct integrations without guessing. See `docs/README.md` for deployment.

## License

MIT. See [LICENSE](LICENSE).
