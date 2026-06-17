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
import com.umain.spectra.core.DeviceSelector
import com.umain.spectra.core.DeviceSession
import com.umain.spectra.core.Permission
import com.umain.spectra.core.PermissionStatus
import com.umain.spectra.core.RegistrationState
import com.umain.spectra.core.SessionState
import com.umain.spectra.core.WearableDevice
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
 * The brains of the Playground. It drives a [Spectra.mock] client through the
 * full happy path and parks every interesting value in Compose state so the UI
 * can just read it. No glasses, no tokens, no Bluetooth seance — it runs on a
 * phone or a simulator that has never seen a face.
 */
class PlaygroundState(private val scope: CoroutineScope) {

    private val client: SpectraClient = Spectra.mock()
    private var session: DeviceSession? = null
    private var stream: CameraStream? = null
    private var display: Display? = null

    var log: String by mutableStateOf("Tap \"Initialize\" to begin. The glasses are imaginary; the API is not.")
        private set

    var registration: RegistrationState by mutableStateOf(RegistrationState.NotRegistered)
        private set
    var cameraPermission: PermissionStatus by mutableStateOf(PermissionStatus.NOT_DETERMINED)
        private set
    var devices: List<WearableDevice> by mutableStateOf(emptyList())
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

    private var stepIndex by mutableIntStateOf(0)

    init {
        client.registrationState.onEach { registration = it }.launchIn(scope)
        client.devices.onEach { devices = it }.launchIn(scope)
    }

    fun initialize() = act("Initializing the SDK") {
        client.initialize()
        log = "Initialized. Now register the app with the (imaginary) Meta AI app."
    }

    fun register() = act("Registering") {
        client.startRegistration()
        log = "Registered. Note there's still no device — permission comes first."
    }

    fun requestCameraPermission() = act("Requesting camera permission") {
        cameraPermission = client.requestPermission(Permission.CAMERA)
        log = "Camera permission: $cameraPermission. A device should now appear above."
    }

    fun startSession() = act("Starting a session") {
        val newSession = client.createSession(DeviceSelector.Auto).getOrElse {
            log = "Could not create a session: ${it.message}"
            return@act
        }
        session = newSession
        newSession.state.onEach { sessionState = it }.launchIn(scope)
        newSession.start()
        log = "Session running. Stream the camera or push something to the display."
    }

    fun startStream() = act("Opening camera stream") {
        val active = session
        if (active == null) {
            log = "Start a session first."
            return@act
        }
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
        log = "Streaming. Those are procedurally-generated frames, not your living room."
    }

    fun capturePhoto() = act("Capturing a photo") {
        val active = stream
        if (active == null) {
            log = "Start a stream first."
            return@act
        }
        active.capturePhoto()
            .onSuccess {
                lastPhoto = it
                log = "Captured a ${it.width}x${it.height} still mid-stream."
            }
            .onFailure { log = "Capture failed: ${it.message}" }
    }

    fun showDisplayDemo() = act("Attaching the display") {
        val active = session
        if (active == null) {
            log = "Start a session first."
            return@act
        }
        val attached = display ?: active.attachDisplay().getOrElse {
            log = "Display attach failed: ${it.message}"
            return@act
        }.also { display = it }
        attached.state.onEach { displayState = it }.launchIn(scope)
        stepIndex = 0
        pushTutorialStep(attached)
        log = "Pushed a tutorial screen to the glasses. Tap its buttons below."
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

    fun reset() = act("Resetting") {
        stream?.close(); stream = null
        session?.stop(); session = null
        display?.close(); display = null
        client.shutdown()
        sessionState = null; streamState = null; lastFrame = null; frameCount = 0
        lastPhoto = null; displayState = null; displayContent = null
        cameraPermission = PermissionStatus.NOT_DETERMINED
        log = "Reset. The mock has been switched off and on again, as is tradition."
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
