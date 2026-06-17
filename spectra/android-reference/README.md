# Android real-SDK backend — reference template

This folder is **not compiled**. It holds the real Android delegation that binds
Spectra to Meta's `mwdat-*` artifacts, corrected against the Device Access
Toolkit **0.7** API reference. It lives outside `src/` on purpose: wiring it
requires a GitHub Packages token to even resolve the SDK, and the exact API has
edges that can't be verified without the SDK on the build machine. So the
shipped library uses a skeleton (`spectra/src/androidMain/.../AndroidSpectraClient.kt`)
and stays green with no token; this is what you drop in when you're ready.

## How to wire it in

1. Re-add the SDK dependencies in `spectra/build.gradle.kts` under `androidMain`:

   ```kotlin
   implementation(libs.mwdat.core)
   implementation(libs.mwdat.camera)
   implementation(libs.mwdat.display)
   ```

2. Provide a GitHub token (`github_token` in `local.properties`, or `GH_TOKEN`
   in the environment) so the `maven.pkg.github.com` repo resolves.

3. Copy the classes from `AndroidBackend.kt.txt` into
   `spectra/src/androidMain/kotlin/com/umain/spectra/android/` (one file per
   class is fine), replacing the skeleton `AndroidSpectraClient`.

4. Build against your installed SDK version and reconcile any remaining
   signature differences — Meta is at developer preview and may move things.

## Known sharp edges (verified against 0.7)

- `Wearables.devices` is a `StateFlow<Set<DeviceIdentifier>>` — bare identifiers,
  not device objects. Names/types live in `Wearables.devicesMetadata`
  (`Map<DeviceIdentifier, StateFlow<Device>>`), and `Device` has
  `name/linkState/deviceType/firmwareInfo/compatibility` — there is no `id`,
  `model`, or `available`; availability is read from `linkState`.
- `checkPermissionStatus` is `suspend` and returns
  `DatResult<PermissionStatus, PermissionError>`. `PermissionStatus` is an
  interface with `data object Granted` / `data object Denied` (PascalCase).
- `DeviceSession.state` is `StateFlow<DeviceSessionState>`
  (`IDLE/STARTING/STARTED/PAUSED/STOPPING/STOPPED`, package `...core.session`).
  `addStream` / `addDisplay` / `removeDisplay` are **extension functions** on
  `DeviceSession`, returning `DatResult<_, DeviceSessionError>`.
- `Stream.capturePhoto()` is `suspend` and returns
  `DatResult<PhotoData, CaptureError>`. `PhotoData` is an interface with
  `HEIC(data: ByteBuffer)` and `Bitmap(bitmap: android.graphics.Bitmap)` — no
  width/height/format. `VideoFrame` carries a `ByteBuffer buffer`, `width`,
  `height`, `presentationTimeUs` (microseconds).
- `DatResult.fold(onSuccess, onFailure)` — `onFailure` takes **two** params:
  `(error: E, cause: Throwable?)`. Errors expose `.description`, not `.message`.
- `Display.sendContent { }` is `suspend`, returns `DatResult<Boolean, DisplayError>`,
  and the lambda receiver is `ContentScope` (the nested `flexBox { }` receiver is
  `FlexBoxScope`). The display DSL and its enums live in
  `com.meta.wearable.dat.display.views`.
