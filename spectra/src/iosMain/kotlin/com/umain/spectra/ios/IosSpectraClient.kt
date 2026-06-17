package com.umain.spectra.ios

import com.umain.spectra.SpectraClient
import com.umain.spectra.core.DeviceSelector
import com.umain.spectra.core.DeviceSession
import com.umain.spectra.core.Permission
import com.umain.spectra.core.PermissionStatus
import com.umain.spectra.core.RegistrationState
import com.umain.spectra.core.SpectraError
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.WearableDevice
import com.umain.spectra.core.spectraFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The iOS bridge skeleton.
 *
 * This is where a Swift shim framework gets wired in via `cinterop`. The shape
 * is already correct — it implements [SpectraClient] exactly like the Android
 * and mock clients — so finishing it is a matter of replacing each
 * [bridgeNotWired] call with a call into your `@objc` Swift wrapper around
 * `Wearables.shared`.
 *
 * The mapping is a near-mirror of the Android adapter, because Meta kept the two
 * platform SDKs conceptually aligned:
 *
 * | Spectra                       | iOS (`MWDATCore` / `MWDATCamera`)                    |
 * |-------------------------------|------------------------------------------------------|
 * | `initialize()`                | `Wearables.configure()`                              |
 * | `startRegistration()`         | `Wearables.shared.startRegistration()`               |
 * | `registrationState`           | `wearables.registrationStateStream()`                |
 * | `devices`                     | `wearables.devicesStream()`                          |
 * | `checkPermission(CAMERA)`     | `wearables.checkPermissionStatus(.camera)`           |
 * | `requestPermission(CAMERA)`   | `wearables.requestPermission(.camera)`               |
 * | `createSession(Auto)`         | `wearables.createSession(deviceSelector:)`           |
 * | `DeviceSession.openCameraStream` | `session.addStream(config:)`                      |
 * | `CameraStream.frames`         | `stream.videoFramePublisher.listen { ... }`          |
 * | `CameraStream.capturePhoto`   | `stream.capturePhoto(format:)`                       |
 * | `DeviceSession.attachDisplay` | `session.addDisplay()`                               |
 * | `Display.sendContent`         | `display.send { ... }`                               |
 *
 * Until the shim exists, every operation fails loudly and on purpose. A bridge
 * that silently returns fake successes is worse than no bridge — it lets bugs
 * board the plane.
 */
internal class IosSpectraClient : SpectraClient {

    private val _registrationState =
        MutableStateFlow<RegistrationState>(RegistrationState.NotRegistered)
    override val registrationState: Flow<RegistrationState> = _registrationState.asStateFlow()

    private val _devices = MutableStateFlow<List<WearableDevice>>(emptyList())
    override val devices: Flow<List<WearableDevice>> = _devices.asStateFlow()

    override suspend fun initialize(): SpectraResult<Unit> = bridgeNotWired("Wearables.configure()")

    override suspend fun startRegistration(): SpectraResult<Unit> =
        bridgeNotWired("Wearables.shared.startRegistration()")

    override suspend fun startUnregistration(): SpectraResult<Unit> =
        bridgeNotWired("Wearables.shared.startUnregistration()")

    override suspend fun checkPermission(permission: Permission): PermissionStatus =
        PermissionStatus.UNAVAILABLE

    override suspend fun requestPermission(permission: Permission): PermissionStatus =
        PermissionStatus.UNAVAILABLE

    override suspend fun createSession(selector: DeviceSelector): SpectraResult<DeviceSession> =
        bridgeNotWired("wearables.createSession(deviceSelector:)")

    override suspend fun shutdown() {
        // Nothing to tear down until the shim allocates something.
    }

    /**
     * The single, honest failure for every un-bridged call. Names the exact
     * Swift symbol you still need to expose, so the error message doubles as a
     * to-do list.
     */
    private fun <T> bridgeNotWired(swiftSymbol: String): SpectraResult<T> = spectraFailure(
        SpectraError.Backend(
            "iOS bridge not wired: expose `$swiftSymbol` via an @objc Swift shim and " +
                "call it through cinterop. Use Spectra.mock() until then.",
        ),
    )
}
