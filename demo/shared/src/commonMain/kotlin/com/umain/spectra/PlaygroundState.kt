package com.umain.spectra

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.umain.spectra.camera.CameraStream
import com.umain.spectra.camera.FrameRate
import com.umain.spectra.camera.Photo
import com.umain.spectra.camera.StreamConfiguration
import com.umain.spectra.camera.StreamState
import com.umain.spectra.camera.VideoFrame
import com.umain.spectra.camera.VideoQuality
import com.umain.spectra.core.AudioProfile
import com.umain.spectra.core.AudioState
import com.umain.spectra.core.DeviceSelector
import com.umain.spectra.core.DeviceSession
import com.umain.spectra.core.MockDeviceKitState
import com.umain.spectra.core.Permission
import com.umain.spectra.core.PermissionStatus
import com.umain.spectra.core.RegistrationState
import com.umain.spectra.core.SessionState
import com.umain.spectra.display.ButtonStyle
import com.umain.spectra.display.Direction
import com.umain.spectra.display.Display
import com.umain.spectra.display.DisplayContent
import com.umain.spectra.display.DisplayState
import com.umain.spectra.display.IconName
import com.umain.spectra.display.TextColor
import com.umain.spectra.display.TextStyle
import com.umain.spectra.display.displayContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * The brains of the Playground, rebuilt to mirror the official CameraAccess
 * sample's three-state flow: connect your glasses, then stream from them, with a
 * developer-only mock kit standing in when no real glasses are around.
 *
 * Every public method maps to one Spectra call, so the screen doubles as a
 * clickable README — it just looks a lot more like a real product now.
 */
class PlaygroundState(
    private val scope: CoroutineScope,
    private val client: SpectraClient = Spectra.mock(),
) {

    private var session: DeviceSession? = null
    private var stream: CameraStream? = null
    private var display: Display? = null

    /** Whether this backend can fake a device (mock client and real iOS can; Android can't). */
    val mockKitSupported: Boolean = client.mockDeviceKit != null

    /** Whether this backend can route audio (mock, real iOS, real Android all can). */
    val audioSupported: Boolean = client.audio != null

    var log: String by mutableStateOf("Connect your glasses to begin.")
        private set

    var registration: RegistrationState by mutableStateOf(RegistrationState.NotRegistered)
        private set
    var hasActiveDevice: Boolean by mutableStateOf(false)
        private set
    var cameraPermission: PermissionStatus by mutableStateOf(PermissionStatus.NOT_DETERMINED)
        private set
    var sessionState: SessionState? by mutableStateOf(null)
        private set
    var streamState: StreamState? by mutableStateOf(null)
        private set
    var lastFrame: VideoFrame? by mutableStateOf(null)
        private set
    var frameCount: Int by mutableIntStateOf(0)
        private set
    var lastPhoto: Photo? by mutableStateOf(null)
        private set
    var displayState: DisplayState? by mutableStateOf(null)
        private set
    var displayContent: DisplayContent? by mutableStateOf(null)
        private set
    var mockKit: MockDeviceKitState by mutableStateOf(MockDeviceKitState())
        private set
    var audioState: AudioState by mutableStateOf(AudioState())
        private set

    /** True once frames are flowing — the UI flips to the full-screen video view. */
    val isStreaming: Boolean
        get() = streamState == StreamState.STREAMING || streamState == StreamState.STARTED

    val isRegistered: Boolean
        get() = registration == RegistrationState.Registered

    val isConnecting: Boolean
        get() = registration == RegistrationState.Registering

    private var stepIndex by mutableIntStateOf(0)

    init {
        client.registrationState.onEach { registration = it }.launchIn(scope)
        client.hasActiveDevice.onEach { hasActiveDevice = it }.launchIn(scope)
        client.mockDeviceKit?.state?.onEach { mockKit = it }?.launchIn(scope)
        client.audio?.state?.onEach { audioState = it }?.launchIn(scope)
    }

    // --- Audio (A2DP playback / HFP mic) -------------------------------------

    val isMicCapturing: Boolean
        get() = audioState.profile == AudioProfile.HFP_MIC

    fun playToGlasses() = act("Playing to your glasses") {
        client.audio?.playToGlasses("Hello from Spectra. If you can hear this, your glasses speakers are working.")
    }

    fun toggleMic() = act("Toggling the microphone") {
        val a = client.audio ?: return@act
        if (isMicCapturing) a.stopMicCapture() else a.startMicCapture()
    }

    fun stopAudio() = act("Stopping audio") {
        client.audio?.stopPlayback()
        client.audio?.stopMicCapture()
    }

    // --- Connection ----------------------------------------------------------

    /** Initialize the SDK and kick off registration (the "Connect my glasses" button). */
    fun connect() = act("Connecting") {
        client.initialize()
        client.startRegistration()
    }

    /** Unregister and tear down any live session — the gear menu's "Disconnect". */
    fun disconnect() = act("Disconnecting") {
        stream?.close(); stream = null
        session?.stop(); session = null
        display?.close(); display = null
        client.audio?.stopPlayback(); client.audio?.stopMicCapture()
        client.startUnregistration()
        sessionState = null; streamState = null; lastFrame = null; frameCount = 0
        lastPhoto = null; displayState = null; displayContent = null
        cameraPermission = PermissionStatus.NOT_DETERMINED
    }

    // --- Streaming -----------------------------------------------------------

    /**
     * The whole "Start streaming" path in one tap: grant camera, open a session,
     * start it, open the camera stream, start that. Mirrors the sample's
     * `handleStartStreaming`.
     */
    fun startStreaming() = act("Starting stream") {
        // Check first — only deeplink to Meta AI if we don't already have it,
        // otherwise every tap re-opens the permission screen.
        cameraPermission = client.checkPermission(Permission.CAMERA)
        if (cameraPermission != PermissionStatus.GRANTED) {
            cameraPermission = client.requestPermission(Permission.CAMERA)
        }
        if (cameraPermission != PermissionStatus.GRANTED) {
            log = "Camera permission is $cameraPermission — can't stream without it."
            return@act
        }

        val active = session ?: client.createSession(DeviceSelector.Auto).getOrElse {
            log = "Couldn't create a session: ${it.message}"
            return@act
        }.also { session = it; it.state.onEach { s -> sessionState = s }.launchIn(scope) }
        active.start()

        val newStream = active.openCameraStream(
            StreamConfiguration(quality = VideoQuality.LOW, frameRate = FrameRate.FPS_15),
        ).getOrElse {
            log = "Stream failed: ${it.message}"
            return@act
        }
        stream = newStream
        newStream.state.onEach { streamState = it }.launchIn(scope)
        newStream.frames.onEach { frame ->
            lastFrame = frame
            frameCount += 1
        }.launchIn(scope)
        newStream.start()
        log = "Streaming."
    }

    fun stopStreaming() = act("Stopping stream") {
        stream?.stop(); stream?.close(); stream = null
        session?.stop(); session = null
        streamState = null; sessionState = null; lastFrame = null; frameCount = 0
        log = "Stopped."
    }

    fun capturePhoto() = act("Capturing a photo") {
        val active = stream ?: run {
            log = "Start streaming first."
            return@act
        }
        active.capturePhoto()
            .onSuccess {
                lastPhoto = it
                log = "Captured a ${it.width}x${it.height} still."
            }
            .onFailure { log = "Capture failed: ${it.message}" }
    }

    fun dismissPhoto() { lastPhoto = null }

    // --- MockDeviceKit (debug bottom sheet) ----------------------------------

    fun enableMockKit() { client.mockDeviceKit?.enable() }
    fun disableMockKit() { client.mockDeviceKit?.disable() }
    fun pairMockGlasses() { client.mockDeviceKit?.pairGlasses() }

    // --- Display demo (optional, secondary) ----------------------------------

    fun showDisplayDemo() = act("Attaching the display") {
        val active = session ?: run {
            log = "Start streaming first so there's a session to attach to."
            return@act
        }
        val attached = display ?: active.attachDisplay().getOrElse {
            log = "Display attach failed: ${it.message}"
            return@act
        }.also { display = it }
        attached.state.onEach { displayState = it }.launchIn(scope)
        stepIndex = 0
        pushTutorialStep(attached)
        log = "Pushed a tutorial screen to the glasses."
    }

    private fun pushTutorialStep(target: Display) {
        val step = TUTORIAL_STEPS[stepIndex]
        val content = displayContent {
            flexBox(gap = 8, paddingAll = 16) {
                text("Step ${stepIndex + 1} of ${TUTORIAL_STEPS.size}", style = TextStyle.META, color = TextColor.SECONDARY)
                text(step.title, style = TextStyle.HEADING)
                text(step.body, style = TextStyle.BODY)
                flexBox(direction = Direction.ROW, gap = 8) {
                    button("Back", style = ButtonStyle.OUTLINE, iconName = IconName.ARROW_LEFT, onClick = {
                        if (stepIndex > 0) {
                            stepIndex -= 1
                            pushTutorialStep(target)
                        }
                    })
                    button("Next", iconName = IconName.ARROW_RIGHT, onClick = {
                        if (stepIndex < TUTORIAL_STEPS.lastIndex) {
                            stepIndex += 1
                            pushTutorialStep(target)
                        }
                    })
                }
            }
        }
        displayContent = content
        scope.launch { target.sendContent(content) }
    }

    /**
     * Launch one UI action on [scope], logging its label first. Named [act], not
     * `run`, specifically so it doesn't shadow `kotlin.run` and turn every
     * `return@run` into a debugging anecdote.
     */
    private inline fun act(label: String, crossinline block: suspend () -> Unit) {
        scope.launch {
            log = "$label..."
            block()
        }
    }

    private data class Step(val title: String, val body: String)

    private companion object {
        val TUTORIAL_STEPS = listOf(
            Step("Open the bonnet", "Pull the release under the dash. Mind your knuckles."),
            Step("Find the dipstick", "It's the one with the loop. No, the other one."),
            Step("Check the oil", "Wipe, reinsert, read. Between the marks is the goal."),
            Step("Done", "That's it. You are now legally a mechanic in no jurisdiction whatsoever."),
        )
    }
}
