# AGENTS.md — Spectra

Instructions for AI coding agents writing integrations with **Spectra**, a Kotlin Multiplatform wrapper around Meta's Wearables Device Access Toolkit. If you follow this file you will write working code on the first try. If you improvise, you will rediscover, slowly, every rule below.

## What Spectra is

- One `SpectraClient` interface in `commonMain`, coroutine- and `Flow`-based.
- Backends: `Spectra.mock()` (in-memory, everywhere), `Spectra.create(context, bridge)` (Android, real SDK), `Spectra.create()` (iOS, Swift-bridge skeleton).
- Package root: `com.umain.spectra`. Errors are a sealed `SpectraError`; results are `Result<T>` (alias `SpectraResult<T>`).

## The non-negotiable call sequence

```kotlin
val spectra = Spectra.mock() // or Spectra.create(...)
spectra.initialize()                                  // 1. once per process
spectra.startRegistration()                           // 2. links to Meta AI app
spectra.requestPermission(Permission.CAMERA)          // 3. REQUIRED before a device exists
val device = spectra.devices.first { it.isNotEmpty() }.first() // 4. wait for it
val session = spectra.createSession(DeviceSelector.Auto).getOrThrow() // 5.
session.start()
session.state.first { it == SessionState.RUNNING }    // 6. wait for RUNNING
val stream = session.openCameraStream(StreamConfiguration(quality = VideoQuality.LOW, frameRate = FrameRate.FPS_15)).getOrThrow()
stream.start()
```

## Rules

1. **Always `initialize()` first.** Anything else first returns `SpectraError.NotInitialized`.
2. **No device appears until registered AND a permission is granted.** If `devices` is empty after registration, you skipped `requestPermission`. This is the single most common mistake.
3. **Wait for `SessionState.RUNNING`** before `openCameraStream`/`attachDisplay`. Opening early returns `SpectraError.InvalidSessionState`.
4. **Never assume why a session changed state.** Observe `session.state`. On `PAUSED`, hold — do not restart; it may resume or stop on its own.
5. **Frame/quality settings are requests.** Valid frame rates are 2, 7, 15, 24, 30 only. Don't pass arbitrary integers. Lower settings can look better over Bluetooth.
6. **Don't reconfigure a stream in place.** Stop the session, start a new one.
7. **Display: send whole screens.** Each `display.sendContent { }` replaces everything; no partial updates; keep a root view because the back gesture from root ends the session.
8. **Convert frames at the edge.** `VideoFrame.bytes` is RGBA; turn it into a platform bitmap once, on the UI thread, not in `commonMain`.
9. **Collect `Flow`s on a background scope**, update UI on main.

## Errors

Branch on the sealed type, don't parse strings:

```kotlin
result.onFailure { t ->
    val error = (t as? SpectraException)?.error
    when (error) {
        SpectraError.NotInitialized -> // call initialize()
        SpectraError.NotRegistered -> // run registration
        SpectraError.PermissionDenied -> // ask, or guide the user to Meta AI app
        SpectraError.NoDeviceAvailable -> // wait for a device / check hinges
        SpectraError.InvalidSessionState -> // wait for RUNNING / STARTED
        is SpectraError.Backend -> // surface error.message
        else -> Unit
    }
}
```

## Testing

- Default to `Spectra.mock()`. It enforces the real preconditions, so a passing test means a correct sequence.
- Failure rehearsal: `MockConfig(autoGrantPermissions = false)`, `MockConfig(failRegistration = true)`.
- The mock needs no GitHub token and runs on the JVM/CI and in previews.

## Platform notes

- **Android:** real backend needs a GitHub Packages token (`github_token` in `local.properties` or `GH_TOKEN` env). Registration + camera permission go through an `ActivityBridge` you implement against an Activity. Build/test with `./gradlew :spectra:allTests` (no token); publish locally with `./gradlew :spectra:publishToMavenLocal` (coordinates `com.umain.spectra:spectra:0.1.0`).
- **iOS:** finish the Swift `@objc` shim and wire it via `cinterop`; until then use the mock. The `IosSpectraClient` error messages name each Swift symbol you still need to expose.

## Upstream truth

When in doubt about SDK behaviour, the authority is Meta's own docs and full reference: https://wearables.developer.meta.com/llms.txt?full=true
