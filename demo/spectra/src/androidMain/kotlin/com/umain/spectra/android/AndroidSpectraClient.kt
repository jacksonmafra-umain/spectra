package com.umain.spectra.android

import android.content.Context
import com.umain.spectra.ActivityBridge
import com.umain.spectra.SpectraClient
import com.umain.spectra.core.DeviceId
import com.umain.spectra.core.DeviceSelector
import com.umain.spectra.core.DeviceSession
import com.umain.spectra.core.Permission
import com.umain.spectra.core.PermissionStatus
import com.umain.spectra.core.RegistrationState
import com.umain.spectra.core.SpectraAudio
import com.umain.spectra.core.SpectraError
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.WearableDevice
import com.umain.spectra.core.spectraFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.types.Permission as MetaPermission
import com.meta.wearable.dat.core.types.PermissionStatus as MetaPermissionStatus
import com.meta.wearable.dat.core.types.WearablesError

/**
 * Android [SpectraClient] backed by the real toolkit (mwdat 0.7). Core +
 * permissions + sessions delegate to [Wearables]; the Activity-bound bits go
 * through your [ActivityBridge].
 *
 * Targets the documented 0.7 surface. If a constant or method name has drifted
 * in your installed SDK, this file and its siblings are the only place to fix.
 */
internal class AndroidSpectraClient(
    private val appContext: Context,
    private val bridge: ActivityBridge,
) : SpectraClient {

    // Initialize the SDK eagerly, before the flow properties below touch
    // Wearables — PlaygroundState starts collecting them at construction, long
    // before the user taps "Initialize". Skipping this throws "Wearables not
    // initialized" the moment the client is built.
    private val initResult = Wearables.initialize(appContext)

    override val audio: SpectraAudio = AndroidSpectraAudio(appContext)

    override val registrationState: Flow<RegistrationState> =
        Wearables.registrationState.map { meta ->
            when (meta.toString().uppercase()) {
                "REGISTERED" -> RegistrationState.Registered
                "REGISTERING" -> RegistrationState.Registering
                "FAILED" -> RegistrationState.Failed("Registration failed. Check app id and client token.")
                else -> RegistrationState.NotRegistered
            }
        }

    override val devices: Flow<List<WearableDevice>> =
        Wearables.devices.map { ids ->
            ids.map { id ->
                val meta = Wearables.devicesMetadata[id]?.value
                WearableDevice(
                    id = DeviceId(id.toString()),
                    name = meta?.name ?: id.toString(),
                    model = meta?.deviceType?.toString() ?: "unknown",
                    isAvailable = meta?.linkState?.toString()?.uppercase()?.contains("CONNECT") == true,
                )
            }
        }

    // Wearables was already initialized in the constructor; just report the
    // outcome. ALREADY_INITIALIZED counts as success (the SDK is up either way).
    override suspend fun initialize(): SpectraResult<Unit> =
        initResult.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error, cause ->
                if (error == WearablesError.ALREADY_INITIALIZED) Result.success(Unit)
                else spectraFailure(SpectraError.Backend(error.description, cause))
            },
        )

    override suspend fun startRegistration(): SpectraResult<Unit> =
        runCatchingSpectra { bridge.launchRegistration() }

    override suspend fun startUnregistration(): SpectraResult<Unit> =
        runCatchingSpectra { bridge.launchUnregistration() }

    override suspend fun checkPermission(permission: Permission): PermissionStatus =
        Wearables.checkPermissionStatus(metaPermission(permission)).fold(
            onSuccess = { status -> permissionStatus(status) },
            onFailure = { _, _ -> PermissionStatus.UNAVAILABLE },
        )

    override suspend fun requestPermission(permission: Permission): PermissionStatus =
        when (permission) {
            Permission.CAMERA -> bridge.requestCameraPermission()
            Permission.MICROPHONE -> checkPermission(permission)
        }

    override suspend fun createSession(selector: DeviceSelector): SpectraResult<DeviceSession> =
        Wearables.createSession(metaSelector(selector)).fold(
            onSuccess = { session -> Result.success(AndroidDeviceSession(session)) },
            onFailure = { error, cause -> spectraFailure(SpectraError.Backend(error.description, cause)) },
        )

    override suspend fun openGlassesAppUpdate(): SpectraResult<Unit> =
        runCatchingSpectra { bridge.openGlassesAppUpdate() }

    override suspend fun shutdown() {
        // Sessions own their lifecycle; nothing global to release.
    }

    private fun metaPermission(p: Permission): MetaPermission = when (p) {
        Permission.CAMERA -> MetaPermission.CAMERA
        Permission.MICROPHONE -> MetaPermission.MICROPHONE
    }

    private fun permissionStatus(s: MetaPermissionStatus): PermissionStatus = when (s) {
        is MetaPermissionStatus.Granted -> PermissionStatus.GRANTED
        is MetaPermissionStatus.Denied -> PermissionStatus.DENIED
        else -> PermissionStatus.NOT_DETERMINED
    }

    private fun metaSelector(selector: DeviceSelector) = when (selector) {
        is DeviceSelector.Auto -> AutoDeviceSelector()
        is DeviceSelector.Specific -> {
            val match = Wearables.devices.value.firstOrNull { it.toString() == selector.deviceId.value }
            if (match != null) SpecificDeviceSelector(match) else AutoDeviceSelector()
        }
    }
}

/** DRY error wrapper for fire-and-forget SDK calls. */
internal inline fun runCatchingSpectra(block: () -> Unit): SpectraResult<Unit> =
    try {
        block()
        Result.success(Unit)
    } catch (t: Throwable) {
        spectraFailure(SpectraError.Backend(t.message ?: "Meta SDK call failed", t))
    }
