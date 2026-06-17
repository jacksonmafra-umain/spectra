package com.umain.spectra.android

import android.content.Context
import com.umain.spectra.ActivityBridge
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
 * Android [SpectraClient] — currently a documented skeleton.
 *
 * Why a skeleton and not the real thing: wiring Meta's `mwdat-*` artifacts
 * requires a GitHub Packages token to even compile, and the exact 0.7 API has
 * sharp edges that can't be verified without the SDK on the build machine
 * (devices arrive as bare `DeviceIdentifier`s with metadata in a separate flow,
 * `PhotoData` is `HEIC`/`Bitmap` rather than raw bytes, the display DSL uses a
 * `ContentScope` receiver, and so on). So the library ships building-and-green
 * with this skeleton, and the real delegation lives — corrected against the
 * actual 0.7 reference — in `spectra/android-reference/` for you to drop in.
 *
 * Until you wire it, use [com.umain.spectra.Spectra.mock], which runs the whole
 * flow on Android with no token and no hardware.
 *
 * @property appContext kept for when the real backend calls `Wearables.initialize(context)`.
 * @property bridge kept for the Activity-bound registration/permission flows.
 */
internal class AndroidSpectraClient(
    @Suppress("unused") private val appContext: Context,
    @Suppress("unused") private val bridge: ActivityBridge,
) : SpectraClient {

    private val _registrationState =
        MutableStateFlow<RegistrationState>(RegistrationState.NotRegistered)
    override val registrationState: Flow<RegistrationState> = _registrationState.asStateFlow()

    private val _devices = MutableStateFlow<List<WearableDevice>>(emptyList())
    override val devices: Flow<List<WearableDevice>> = _devices.asStateFlow()

    override suspend fun initialize(): SpectraResult<Unit> =
        notWired("Wearables.initialize(context)")

    override suspend fun startRegistration(): SpectraResult<Unit> =
        notWired("Wearables.startRegistration(activity) via ActivityBridge")

    override suspend fun startUnregistration(): SpectraResult<Unit> =
        notWired("Wearables.startUnregistration(activity) via ActivityBridge")

    override suspend fun checkPermission(permission: Permission): PermissionStatus =
        PermissionStatus.UNAVAILABLE

    override suspend fun requestPermission(permission: Permission): PermissionStatus =
        PermissionStatus.UNAVAILABLE

    override suspend fun createSession(selector: DeviceSelector): SpectraResult<DeviceSession> =
        notWired("Wearables.createSession(deviceSelector)")

    override suspend fun shutdown() {
        // Nothing allocated yet — nothing to release.
    }

    /**
     * The single honest failure for every un-wired call, naming the exact mwdat
     * symbol the reference template binds to. The message doubles as a to-do list.
     */
    private fun <T> notWired(symbol: String): SpectraResult<T> = spectraFailure(
        SpectraError.Backend(
            "Android backend not wired: bind `$symbol` from spectra/android-reference/, " +
                "then re-add the mwdat-* dependencies. Use Spectra.mock() until then.",
        ),
    )
}
