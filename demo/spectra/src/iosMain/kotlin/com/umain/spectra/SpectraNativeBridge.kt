package com.umain.spectra

import platform.Foundation.NSData

/**
 * A frame handed across the bridge. It's a class (not loose primitives) on
 * purpose: Kotlin/Native boxes primitive parameters inside function types when
 * exporting to Swift (`(Int) -> Unit` becomes `(KotlinInt) -> Void`), which
 * makes Swift conformance miserable. Wrapping the values in an object sidesteps
 * all of that — constructor parameters export cleanly as `Int32`/`Int64`.
 */
public class NativeFrame(
    public val data: NSData,
    public val width: Int,
    public val height: Int,
    public val timestampMillis: Long,
)

/** Result of a photo capture, same object-not-primitives reasoning as [NativeFrame]. */
public class NativePhotoResult(
    public val data: NSData?,
    public val width: Int,
    public val height: Int,
    public val error: String?,
)

/**
 * Snapshot of the Swift-side MockDeviceKit, carried as an object so the callback
 * doesn't traffic in bare Bool/Int (same boxing dodge as [NativeFrame]).
 */
public class NativeMockDeviceKitState(
    public val enabled: Boolean,
    public val pairedCount: Int,
)

/** Snapshot of the platform audio route, object-wrapped (same boxing dodge as [NativeFrame]). */
public class NativeAudioState(
    public val profile: String,        // "NONE" | "A2DP_PLAYBACK" | "HFP_MIC"
    public val micLevel: Float,
    public val isPlaying: Boolean,
    public val routedToGlasses: Boolean,
)

/**
 * The seam between Kotlin and Meta's Swift-only iOS SDK. Swift implements this
 * (against `Wearables.shared`); [com.umain.spectra.ios.IosSpectraClient] turns
 * the callbacks back into the Flow/suspend `SpectraClient`.
 *
 * Callbacks deliberately carry only object types (String, List, NSData, the
 * Native* classes) — never bare Int/Long/Boolean — so the Swift side conforms
 * without fighting Kotlin's primitive boxing.
 */
public interface SpectraNativeBridge {

    public fun configure()
    public fun startRegistration()
    public fun startUnregistration()
    public fun openGlassesAppUpdate()

    public fun observeRegistrationState(onState: (String) -> Unit)
    public fun observeDevices(onDevices: (List<String>) -> Unit)

    public fun checkCameraPermission(onResult: (String) -> Unit)
    public fun requestCameraPermission(onResult: (String) -> Unit)

    /** Create a session. `onResult(null)` on success, `onResult(message)` on failure. */
    public fun createSession(onResult: (String?) -> Unit)
    public fun startSession()
    public fun stopSession()
    public fun observeSessionState(onState: (String) -> Unit)

    public fun startStream(quality: String, frameRate: Int, onError: (String) -> Unit)
    public fun stopStream()
    public fun observeStreamState(onState: (String) -> Unit)
    public fun observeFrames(onFrame: (NativeFrame) -> Unit)

    public fun capturePhoto(onResult: (NativePhotoResult) -> Unit)

    // --- MockDeviceKit (developer-only device simulation) --------------------
    public fun enableMockDeviceKit()
    public fun disableMockDeviceKit()
    public fun pairMockGlasses()
    public fun observeMockDeviceKit(onState: (NativeMockDeviceKitState) -> Unit)

    // --- Audio (platform Bluetooth: A2DP playback / HFP mic) -----------------
    public fun playAudioToGlasses(text: String)
    public fun stopAudioPlayback()
    public fun startMicCapture()
    public fun stopMicCapture()
    public fun observeAudio(onState: (NativeAudioState) -> Unit)
}
