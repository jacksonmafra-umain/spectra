package com.umain.spectra.mock

import com.umain.spectra.SpectraClient
import com.umain.spectra.core.AudioProfile
import com.umain.spectra.core.AudioState
import com.umain.spectra.core.DeviceId
import com.umain.spectra.core.DeviceSelector
import com.umain.spectra.core.DeviceSession
import com.umain.spectra.core.MockDeviceKit
import com.umain.spectra.core.MockDeviceKitState
import com.umain.spectra.core.Permission
import com.umain.spectra.core.SpectraAudio
import com.umain.spectra.core.PermissionStatus
import com.umain.spectra.core.RegistrationState
import com.umain.spectra.core.SpectraError
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.WearableDevice
import com.umain.spectra.core.spectraFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

    private val _mockKitState = MutableStateFlow(MockDeviceKitState())

    /**
     * A self-contained [MockDeviceKit]: flip it on, pair a fake pair of glasses,
     * and a device appears in [devices] without any real registration or
     * Bluetooth involved. It's the simulator-friendly twin of the real SDK's
     * MockDeviceKit, so the debug bottom sheet does something even here.
     */
    override val mockDeviceKit: MockDeviceKit = object : MockDeviceKit {
        override val state: Flow<MockDeviceKitState> = _mockKitState.asStateFlow()

        override fun enable() {
            _mockKitState.value = _mockKitState.value.copy(enabled = true)
            refreshDevices()
        }

        override fun disable() {
            _mockKitState.value = MockDeviceKitState()
            refreshDevices()
        }

        override fun pairGlasses() {
            if (!_mockKitState.value.enabled) return
            _mockKitState.value =
                _mockKitState.value.copy(pairedCount = _mockKitState.value.pairedCount + 1)
            refreshDevices()
        }
    }

    private val _audioState = MutableStateFlow(AudioState())
    private var micJob: Job? = null

    /** A self-contained audio stand-in: TTS "plays", and the mic emits a wandering level. */
    override val audio: SpectraAudio = object : SpectraAudio {
        override val state: Flow<AudioState> = _audioState.asStateFlow()

        override suspend fun playToGlasses(text: String) {
            _audioState.value = AudioState(
                profile = AudioProfile.A2DP_PLAYBACK, isPlaying = true, routedToGlasses = true,
            )
            delay(2_000)
            _audioState.value = AudioState(profile = AudioProfile.A2DP_PLAYBACK, routedToGlasses = true)
        }

        override suspend fun stopPlayback() {
            _audioState.value = AudioState()
        }

        override suspend fun startMicCapture() {
            micJob?.cancel()
            micJob = scope.launch {
                var t = 0.0
                while (isActive) {
                    val level = (0.5 + 0.5 * kotlin.math.sin(t)).toFloat() * 0.8f
                    _audioState.value = AudioState(
                        profile = AudioProfile.HFP_MIC, micLevel = level, routedToGlasses = true,
                    )
                    t += 0.4
                    delay(80)
                }
            }
        }

        override suspend fun stopMicCapture() {
            micJob?.cancel(); micJob = null
            _audioState.value = AudioState()
        }
    }

    override suspend fun initialize(): SpectraResult<Unit> {
        initialized = true
        return Result.success(Unit)
    }

    override suspend fun startRegistration(): SpectraResult<Unit> {
        if (!initialized) return spectraFailure(SpectraError.NotInitialized)
        _registrationState.value = RegistrationState.Registering
        delay(config.registrationDelayMillis)
        if (config.failRegistration) {
            _registrationState.value =
                RegistrationState.Failed("Mock registration failed because you asked it to.")
            return spectraFailure(SpectraError.Backend("Mock registration failed on request."))
        }
        _registrationState.value = RegistrationState.Registered
        refreshDevices()
        return Result.success(Unit)
    }

    override suspend fun startUnregistration(): SpectraResult<Unit> {
        if (!initialized) return spectraFailure(SpectraError.NotInitialized)
        _registrationState.value = RegistrationState.NotRegistered
        cameraStatus = PermissionStatus.NOT_DETERMINED
        refreshDevices()
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
        }
        return result
    }

    override suspend fun createSession(selector: DeviceSelector): SpectraResult<DeviceSession> {
        if (!initialized) return spectraFailure(SpectraError.NotInitialized)
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

    override suspend fun openGlassesAppUpdate(): SpectraResult<Unit> {
        // No real glasses to update in the mock.
        return Result.success(Unit)
    }

    override suspend fun shutdown() {
        scope.cancel()
        initialized = false
        _registrationState.value = RegistrationState.NotRegistered
        cameraStatus = PermissionStatus.NOT_DETERMINED
        _mockKitState.value = MockDeviceKitState()
        _devices.value = emptyList()
    }

    /**
     * A device becomes active once you've registered (your glasses are
     * connected) *or* the [mockDeviceKit] has paired a fake one. Camera
     * permission gates the stream's *contents*, not the device's existence —
     * which is exactly how the real auto-selector behaves, and why the official
     * sample can light up "Start streaming" before you've granted anything.
     */
    private fun refreshDevices() {
        val viaRegistration = _registrationState.value == RegistrationState.Registered
        val viaMockKit = _mockKitState.value.enabled && _mockKitState.value.pairedCount > 0
        _devices.value = if (viaRegistration || viaMockKit) {
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
}
