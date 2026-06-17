package com.umain.spectra

import com.umain.spectra.core.DeviceSelector
import com.umain.spectra.core.DeviceSession
import com.umain.spectra.core.Permission
import com.umain.spectra.core.PermissionStatus
import com.umain.spectra.core.RegistrationState
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.WearableDevice
import kotlinx.coroutines.flow.Flow

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

    /** Tear everything down: stop sessions, drop listeners, release resources. Be a good citizen. */
    public suspend fun shutdown()
}
