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
import kotlinx.coroutines.flow.map

// The real toolkit. Resolves only with a GitHub token; see settings.gradle.kts.
import com.meta.wearable.dat.core.Wearables

/**
 * Android [SpectraClient] backed by the real Meta Wearables Device Access
 * Toolkit (`mwdat-core` / `-camera` / `-display`, version 0.7).
 *
 * This is the thin layer. It delegates to [Wearables] for everything that
 * doesn't need an Activity, and to your [ActivityBridge] for the two things that
 * do. If a Meta constant or method name drifts in a future SDK release, the
 * blast radius is this file and [Mappers] — by design.
 *
 * Note: this code targets the documented 0.7 surface. It compiles against the
 * published artifacts, which means it does not compile until you've supplied a
 * GitHub Packages token. That's Meta's distribution choice, not ours, and the
 * mock client exists precisely so you're not blocked on it.
 */
internal class AndroidSpectraClient(
    private val appContext: Context,
    private val bridge: ActivityBridge,
) : SpectraClient {

    override val registrationState: Flow<RegistrationState> =
        Wearables.registrationState.map { metaState ->
            // Meta's registration state names are mapped here. Adjust if 0.x
            // renames them; everything downstream speaks Spectra's dialect.
            when (metaState.toString().uppercase()) {
                "REGISTERED" -> RegistrationState.Registered
                "REGISTERING" -> RegistrationState.Registering
                "FAILED" -> RegistrationState.Failed("Registration failed. Check app id and client token.")
                else -> RegistrationState.NotRegistered
            }
        }

    override val devices: Flow<List<WearableDevice>> =
        Wearables.devices.map { list ->
            list.map { d -> Mappers.device(d.id.toString(), d.name, d.model, d.available) }
        }

    override suspend fun initialize(): SpectraResult<Unit> = runCatchingSpectra {
        Wearables.initialize(appContext)
    }

    override suspend fun startRegistration(): SpectraResult<Unit> = runCatchingSpectra {
        bridge.launchRegistration()
    }

    override suspend fun startUnregistration(): SpectraResult<Unit> = runCatchingSpectra {
        bridge.launchUnregistration()
    }

    override suspend fun checkPermission(permission: Permission): PermissionStatus =
        Mappers.permissionStatus(Wearables.checkPermissionStatus(Mappers.permission(permission)))

    override suspend fun requestPermission(permission: Permission): PermissionStatus =
        when (permission) {
            Permission.CAMERA -> bridge.requestCameraPermission()
            // Microphone is platform HFP, not a Meta AI prompt. Ask Android, not us.
            Permission.MICROPHONE -> checkPermission(permission)
        }

    override suspend fun createSession(selector: DeviceSelector): SpectraResult<DeviceSession> {
        val metaSelector = Mappers.selector(selector)
        return Wearables.createSession(metaSelector).fold(
            onSuccess = { metaSession -> Result.success(AndroidDeviceSession(metaSession)) },
            onFailure = { error -> spectraFailure(SpectraError.Backend(error.message ?: "createSession failed", error)) },
        )
    }

    override suspend fun shutdown() {
        // The toolkit has no global teardown; sessions own their lifecycles.
        // Stop yours and let the rest fall out of scope like a polite guest.
    }
}

/**
 * Run an SDK call, converting any thrown exception into a [SpectraError.Backend]
 * failure. DRY: every delegating method would otherwise repeat this try/catch,
 * and copy-pasted error handling is how one bug becomes twelve.
 */
internal inline fun runCatchingSpectra(block: () -> Unit): SpectraResult<Unit> =
    try {
        block()
        Result.success(Unit)
    } catch (t: Throwable) {
        spectraFailure(SpectraError.Backend(t.message ?: "Meta SDK call failed", t))
    }
