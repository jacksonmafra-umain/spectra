package com.umain.spectra.mock

import com.umain.spectra.SpectraClient
import com.umain.spectra.core.DeviceId
import com.umain.spectra.core.DeviceSelector
import com.umain.spectra.core.DeviceSession
import com.umain.spectra.core.Permission
import com.umain.spectra.core.PermissionStatus
import com.umain.spectra.core.RegistrationState
import com.umain.spectra.core.SpectraError
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.WearableDevice
import com.umain.spectra.core.spectraFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The in-memory [SpectraClient]. A complete, self-contained simulation of the
 * happy path (and, if you ask it to, the unhappy ones) with no hardware in
 * sight.
 *
 * It enforces the same preconditions the real SDK does — initialize before use,
 * register before sessions, grant a permission before a device appears — so the
 * mock is a genuine rehearsal, not a participation trophy. Code that satisfies
 * the mock has at least learned the steps.
 */
internal class MockSpectraClient(
    private val config: MockConfig,
) : SpectraClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var initialized = false

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.NotRegistered)
    override val registrationState: Flow<RegistrationState> = _registrationState.asStateFlow()

    private val _devices = MutableStateFlow<List<WearableDevice>>(emptyList())
    override val devices: Flow<List<WearableDevice>> = _devices.asStateFlow()

    private var cameraStatus: PermissionStatus = PermissionStatus.NOT_DETERMINED

    private val mockDeviceId = DeviceId("mock-device-0001")

    override suspend fun initialize(): SpectraResult<Unit> {
        initialized = true
        return Result.success(Unit)
    }

    override suspend fun startRegistration(): SpectraResult<Unit> {
        notInitialized()?.let { return it }
        _registrationState.value = RegistrationState.Registering
        delay(config.registrationDelayMillis)
        if (config.failRegistration) {
            _registrationState.value =
                RegistrationState.Failed("Mock registration failed because you asked it to.")
            return spectraFailure(SpectraError.Backend("Mock registration failed on request."))
        }
        _registrationState.value = RegistrationState.Registered
        revealDeviceIfReady()
        return Result.success(Unit)
    }

    override suspend fun startUnregistration(): SpectraResult<Unit> {
        notInitialized()?.let { return it }
        _registrationState.value = RegistrationState.NotRegistered
        cameraStatus = PermissionStatus.NOT_DETERMINED
        _devices.value = emptyList()
        return Result.success(Unit)
    }

    override suspend fun checkPermission(permission: Permission): PermissionStatus =
        when (permission) {
            Permission.CAMERA -> cameraStatus
            // Mic goes through platform HFP on real devices; the mock just nods along.
            Permission.MICROPHONE -> PermissionStatus.GRANTED
        }

    override suspend fun requestPermission(permission: Permission): PermissionStatus {
        if (!initialized || _registrationState.value != RegistrationState.Registered) {
            return PermissionStatus.UNAVAILABLE
        }
        delay(config.permissionDelayMillis)
        val result =
            if (config.autoGrantPermissions) PermissionStatus.GRANTED else PermissionStatus.DENIED
        if (permission == Permission.CAMERA) {
            cameraStatus = result
            revealDeviceIfReady()
        }
        return result
    }

    override suspend fun createSession(selector: DeviceSelector): SpectraResult<DeviceSession> {
        notInitialized()?.let { return it }
        if (_registrationState.value != RegistrationState.Registered) {
            return spectraFailure(SpectraError.NotRegistered)
        }
        val available = _devices.value
        if (available.isEmpty()) return spectraFailure(SpectraError.NoDeviceAvailable)

        val targetId = when (selector) {
            is DeviceSelector.Auto -> available.first().id
            is DeviceSelector.Specific -> selector.deviceId
        }
        if (available.none { it.id == targetId }) {
            return spectraFailure(SpectraError.NoDeviceAvailable)
        }
        return Result.success(MockDeviceSession(targetId, config, scope))
    }

    override suspend fun shutdown() {
        scope.cancel()
        initialized = false
        _registrationState.value = RegistrationState.NotRegistered
        _devices.value = emptyList()
        cameraStatus = PermissionStatus.NOT_DETERMINED
    }

    /**
     * A device only shows up once you're registered *and* a permission is
     * granted — the same gotcha the integration guides warn about in bold. We
     * reproduce it on purpose, because a mock that hides the footguns just sells
     * them to you later at full price.
     */
    private fun revealDeviceIfReady() {
        val ready = _registrationState.value == RegistrationState.Registered &&
            cameraStatus == PermissionStatus.GRANTED
        _devices.value = if (ready) {
            listOf(
                WearableDevice(
                    id = mockDeviceId,
                    name = config.deviceName,
                    model = config.deviceModel,
                    isAvailable = true,
                ),
            )
        } else {
            emptyList()
        }
    }

    private fun <T> notInitialized(): SpectraResult<T>? =
        if (initialized) null else spectraFailure(SpectraError.NotInitialized)
}
