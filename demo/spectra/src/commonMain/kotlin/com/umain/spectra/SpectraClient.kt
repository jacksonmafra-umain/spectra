package com.umain.spectra

import com.umain.spectra.core.DeviceSelector
import com.umain.spectra.core.DeviceSession
import com.umain.spectra.core.MockDeviceKit
import com.umain.spectra.core.SpectraAudio
import com.umain.spectra.core.Permission
import com.umain.spectra.core.PermissionStatus
import com.umain.spectra.core.RegistrationState
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.WearableDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The registration half of the world: linking your app to the user's glasses.
 *
 * Split out as its own interface so a screen that only cares about registration
 * doesn't have to depend on camera streaming it will never call. Interface
 * Segregation — the "I" in SOLID, and the one everyone forgets.
 */
public interface Registrar {
    /** Observe the [RegistrationState]. Drive your "Connect your glasses" UI off this. */
    public val registrationState: Flow<RegistrationState>

    /** Kick off the one-time registration deeplink into the Meta AI app. */
    public suspend fun startRegistration(): SpectraResult<Unit>

    /** Undo it. The user can also do this from the Meta AI app, with or without your blessing. */
    public suspend fun startUnregistration(): SpectraResult<Unit>
}

/** The permission half: asking nicely, and checking whether the asking worked. */
public interface PermissionController {
    /** Check current status without prompting. Cheap, idempotent, do it often. */
    public suspend fun checkPermission(permission: Permission): PermissionStatus

    /** Prompt the user (via the Meta AI app for camera). Returns the resulting status. */
    public suspend fun requestPermission(permission: Permission): PermissionStatus
}

/** The device directory. Remember: a device only appears after a permission is granted. */
public interface DeviceRegistry {
    /** The current set of known glasses, live. Empty until permissions exist. */
    public val devices: Flow<List<WearableDevice>>

    /**
     * Whether there is currently a device ready to be used — the signal the
     * platform's auto-selector emits when it has picked an active pair of
     * glasses. Defaults to "are there any [devices]"; backends that have a
     * truer notion of "active" (iOS does) keep this honest by folding their
     * auto-selector into [devices].
     */
    public val hasActiveDevice: Flow<Boolean>
        get() = devices.map { it.isNotEmpty() }
}

/** Makes sessions. One responsibility, so it gets one interface. */
public interface SessionFactory {
    /**
     * Create a [DeviceSession] using the given [selector].
     *
     * @param selector [DeviceSelector.Auto] for "you pick", or
     *   [DeviceSelector.Specific] when you have your own device picker and the
     *   support tickets to match.
     */
    public suspend fun createSession(
        selector: DeviceSelector = DeviceSelector.Auto,
    ): SpectraResult<DeviceSession>
}

/**
 * The whole front door to Spectra.
 *
 * It's an aggregate of the small capability interfaces above, so you can depend
 * on the slice you need ([Registrar], [PermissionController], ...) in the places
 * that only need a slice, while still having one object to hold when you want
 * the lot. You don't implement this — you get one from [Spectra].
 *
 * The intended choreography, start to finish:
 * 1. [initialize] once at startup.
 * 2. [Registrar.startRegistration] and wait for [RegistrationState.Registered].
 * 3. [PermissionController.requestPermission] for [Permission.CAMERA].
 * 4. Watch [DeviceRegistry.devices] until one shows up.
 * 5. [SessionFactory.createSession], start it, then stream or display to your heart's content.
 *
 * Skip a step and the SDK will let you know, loudly, via a [com.umain.spectra.core.SpectraError].
 */
public interface SpectraClient : Registrar, PermissionController, DeviceRegistry, SessionFactory {

    /**
     * Wake the SDK up. Call this once per process before anything else. Calling
     * other methods first earns you [com.umain.spectra.core.SpectraError.NotInitialized],
     * which is the SDK's polite way of saying "read the manual".
     */
    public suspend fun initialize(): SpectraResult<Unit>

    /**
     * Open the Meta AI screen that installs/updates the on-glasses Device Access
     * Toolkit app. If a registered device never appears in [DeviceRegistry.devices]
     * despite the glasses being connected, they're usually missing that on-glasses
     * app — this sends the user to install it.
     */
    public suspend fun openGlassesAppUpdate(): SpectraResult<Unit>

    /** Tear everything down: stop sessions, drop listeners, release resources. Be a good citizen. */
    public suspend fun shutdown()

    /**
     * The developer-only [MockDeviceKit], or null when this backend can't fake a
     * device. Use it to run the whole pipeline against a simulated pair of
     * glasses — handy on a simulator, on CI, or whenever the real hardware is in
     * the other room with a dead battery.
     */
    public val mockDeviceKit: MockDeviceKit?
        get() = null

    /**
     * Audio to/from the glasses (A2DP playback, HFP mic), or null if this backend
     * can't route audio. It's plain platform Bluetooth, not DAT — see [SpectraAudio].
     */
    public val audio: SpectraAudio?
        get() = null
}
