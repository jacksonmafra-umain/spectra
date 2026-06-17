package com.umain.spectra.ios

import com.umain.spectra.SpectraClient
import com.umain.spectra.SpectraNativeBridge
import com.umain.spectra.camera.CameraStream
import com.umain.spectra.camera.Photo
import com.umain.spectra.camera.PhotoFormat
import com.umain.spectra.camera.StreamConfiguration
import com.umain.spectra.camera.StreamState
import com.umain.spectra.camera.VideoFrame
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
import com.umain.spectra.core.SessionState
import com.umain.spectra.core.SpectraError
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.WearableDevice
import com.umain.spectra.core.spectraFailure
import com.umain.spectra.display.Display
import com.umain.spectra.display.DisplayConfiguration
import com.umain.spectra.toByteArray
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * iOS [SpectraClient]. It owns no SDK calls itself — it forwards everything to a
 * Swift-implemented [SpectraNativeBridge] and reshapes the bridge's callbacks
 * into Flows and suspend functions. The "observe…" listeners are registered once
 * here and pushed into [MutableStateFlow]s; the one-shot operations become
 * [suspendCancellableCoroutine]s.
 */
internal class IosSpectraClient(private val bridge: SpectraNativeBridge) : SpectraClient {

    private val _registrationState = MutableStateFlow<RegistrationState>(RegistrationState.NotRegistered)
    override val registrationState: Flow<RegistrationState> = _registrationState.asStateFlow()

    private val _devices = MutableStateFlow<List<WearableDevice>>(emptyList())
    override val devices: Flow<List<WearableDevice>> = _devices.asStateFlow()

    override val mockDeviceKit: MockDeviceKit = IosMockDeviceKit(bridge)
    override val audio: SpectraAudio = IosSpectraAudio(bridge)

    init {
        bridge.observeRegistrationState { _registrationState.value = mapRegistration(it) }
        bridge.observeDevices { ids ->
            _devices.value = ids.map { WearableDevice(DeviceId(it), it, "Meta glasses", true) }
        }
    }

    override suspend fun initialize(): SpectraResult<Unit> {
        bridge.configure()
        return Result.success(Unit)
    }

    override suspend fun startRegistration(): SpectraResult<Unit> {
        bridge.startRegistration()
        return Result.success(Unit)
    }

    override suspend fun startUnregistration(): SpectraResult<Unit> {
        bridge.startUnregistration()
        return Result.success(Unit)
    }

    override suspend fun checkPermission(permission: Permission): PermissionStatus =
        when (permission) {
            Permission.CAMERA -> suspendCancellableCoroutine { c ->
                bridge.checkCameraPermission { c.resume(mapPermission(it)) }
            }
            Permission.MICROPHONE -> PermissionStatus.GRANTED
        }

    override suspend fun requestPermission(permission: Permission): PermissionStatus =
        when (permission) {
            Permission.CAMERA -> suspendCancellableCoroutine { c ->
                bridge.requestCameraPermission { c.resume(mapPermission(it)) }
            }
            Permission.MICROPHONE -> PermissionStatus.GRANTED
        }

    override suspend fun createSession(selector: DeviceSelector): SpectraResult<DeviceSession> =
        suspendCancellableCoroutine { c ->
            bridge.createSession { error ->
                if (error == null) {
                    c.resume(Result.success(IosDeviceSession(bridge)))
                } else {
                    c.resume(spectraFailure(SpectraError.Backend(error)))
                }
            }
        }

    override suspend fun openGlassesAppUpdate(): SpectraResult<Unit> {
        bridge.openGlassesAppUpdate()
        return Result.success(Unit)
    }

    override suspend fun shutdown() {
        bridge.stopSession()
    }

    private fun mapRegistration(s: String): RegistrationState = when (s.uppercase()) {
        "REGISTERED" -> RegistrationState.Registered
        "REGISTERING" -> RegistrationState.Registering
        "FAILED" -> RegistrationState.Failed("Registration failed. Check MetaAppID and ClientToken.")
        else -> RegistrationState.NotRegistered
    }
}

/** Bridges the Swift MockDeviceKit (Meta's `MockDeviceKit.shared`) to the Flow world. */
internal class IosMockDeviceKit(private val bridge: SpectraNativeBridge) : MockDeviceKit {
    private val _state = MutableStateFlow(MockDeviceKitState())
    override val state: Flow<MockDeviceKitState> = _state.asStateFlow()

    init {
        bridge.observeMockDeviceKit { native ->
            _state.value = MockDeviceKitState(native.enabled, native.pairedCount)
        }
    }

    override fun enable() = bridge.enableMockDeviceKit()
    override fun disable() = bridge.disableMockDeviceKit()
    override fun pairGlasses() = bridge.pairMockGlasses()
}

/** Bridges the Swift platform-audio layer (AVAudioSession/AVAudioEngine) to a Flow. */
internal class IosSpectraAudio(private val bridge: SpectraNativeBridge) : SpectraAudio {
    private val _state = MutableStateFlow(AudioState())
    override val state: Flow<AudioState> = _state.asStateFlow()

    init {
        bridge.observeAudio { native ->
            _state.value = AudioState(
                profile = when (native.profile.uppercase()) {
                    "A2DP_PLAYBACK" -> AudioProfile.A2DP_PLAYBACK
                    "HFP_MIC" -> AudioProfile.HFP_MIC
                    else -> AudioProfile.NONE
                },
                micLevel = native.micLevel,
                isPlaying = native.isPlaying,
                routedToGlasses = native.routedToGlasses,
            )
        }
    }

    override suspend fun playToGlasses(text: String) = bridge.playAudioToGlasses(text)
    override suspend fun stopPlayback() = bridge.stopAudioPlayback()
    override suspend fun startMicCapture() = bridge.startMicCapture()
    override suspend fun stopMicCapture() = bridge.stopMicCapture()
}

internal fun mapPermission(s: String): PermissionStatus = when (s.uppercase()) {
    "GRANTED" -> PermissionStatus.GRANTED
    "DENIED" -> PermissionStatus.DENIED
    "NOT_DETERMINED" -> PermissionStatus.NOT_DETERMINED
    else -> PermissionStatus.UNAVAILABLE
}

/** A session backed by the single active native session the bridge manages. */
internal class IosDeviceSession(private val bridge: SpectraNativeBridge) : DeviceSession {

    override val deviceId: DeviceId = DeviceId("active")

    private val _state = MutableStateFlow(SessionState.STOPPED)
    override val state: Flow<SessionState> = _state.asStateFlow()

    init {
        bridge.observeSessionState { s ->
            _state.value = when (s.uppercase()) {
                "RUNNING", "STARTED" -> SessionState.RUNNING
                "STARTING" -> SessionState.STARTING
                "PAUSED" -> SessionState.PAUSED
                else -> SessionState.STOPPED
            }
        }
    }

    override suspend fun start(): SpectraResult<Unit> {
        bridge.startSession()
        return Result.success(Unit)
    }

    override suspend fun stop(): SpectraResult<Unit> {
        bridge.stopSession()
        return Result.success(Unit)
    }

    override suspend fun openCameraStream(configuration: StreamConfiguration): SpectraResult<CameraStream> =
        Result.success(IosCameraStream(bridge, configuration))

    override suspend fun attachDisplay(configuration: DisplayConfiguration): SpectraResult<Display> =
        spectraFailure(
            SpectraError.Backend("Display isn't wired in the iOS demo bridge yet (Ray-Ban Display only)."),
        )

    override suspend fun removeDisplay() { /* no display attached */ }
}

/** A camera stream backed by the native stream the bridge starts. */
internal class IosCameraStream(
    private val bridge: SpectraNativeBridge,
    private val configuration: StreamConfiguration,
) : CameraStream {

    private val _state = MutableStateFlow(StreamState.STOPPED)
    override val state: Flow<StreamState> = _state.asStateFlow()

    private val _frames = MutableSharedFlow<VideoFrame>(replay = 1, extraBufferCapacity = 2)
    override val frames: Flow<VideoFrame> = _frames.asSharedFlow()

    init {
        bridge.observeStreamState { s ->
            _state.value = when (s.uppercase()) {
                "STREAMING" -> StreamState.STREAMING
                "STARTED" -> StreamState.STARTED
                "STARTING" -> StreamState.STARTING
                "STOPPING" -> StreamState.STOPPING
                "CLOSED" -> StreamState.CLOSED
                else -> StreamState.STOPPED
            }
        }
        bridge.observeFrames { frame ->
            _frames.tryEmit(
                VideoFrame(frame.width, frame.height, frame.data.toByteArray(), frame.timestampMillis),
            )
        }
    }

    private val _lastError = MutableStateFlow<String?>(null)

    /** The last stream error message, or null. Surfaced so the UI can stop pretending all is well. */
    val lastError: Flow<String?> = _lastError.asStateFlow()

    override suspend fun start(): SpectraResult<Unit> {
        bridge.startStream(configuration.quality.name, configuration.frameRate.fps) { message ->
            // Don't swallow it: log to the console (shows in Xcode) and flip state.
            println("Spectra(stream) error: $message")
            _lastError.value = message
            _state.value = StreamState.STOPPED
        }
        return Result.success(Unit)
    }

    override suspend fun stop(): SpectraResult<Unit> {
        bridge.stopStream()
        return Result.success(Unit)
    }

    override suspend fun capturePhoto(format: PhotoFormat): SpectraResult<Photo> =
        suspendCancellableCoroutine { c ->
            bridge.capturePhoto { result ->
                val data = result.data
                if (data != null) {
                    c.resume(Result.success(Photo(data.toByteArray(), PhotoFormat.JPEG, result.width, result.height)))
                } else {
                    c.resume(spectraFailure(SpectraError.Backend(result.error ?: "capturePhoto failed")))
                }
            }
        }

    override suspend fun close() {
        bridge.stopStream()
    }
}
