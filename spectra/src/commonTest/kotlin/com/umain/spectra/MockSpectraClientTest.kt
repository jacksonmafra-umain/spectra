package com.umain.spectra

import com.umain.spectra.core.DeviceSelector
import com.umain.spectra.core.Permission
import com.umain.spectra.core.PermissionStatus
import com.umain.spectra.core.RegistrationState
import com.umain.spectra.core.SessionState
import com.umain.spectra.core.SpectraException
import com.umain.spectra.core.SpectraError
import com.umain.spectra.camera.StreamState
import com.umain.spectra.display.DisplayState
import com.umain.spectra.display.IconName
import com.umain.spectra.display.TextStyle
import com.umain.spectra.mock.MockConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for the mock client. They double as executable documentation: if you
 * want to know the correct call order, read these instead of guessing and then
 * blaming the SDK.
 */
class MockSpectraClientTest {

    private fun client(config: MockConfig = MockConfig()) = Spectra.mock(config)

    @Test
    fun callingBeforeInitializeFails() = runTest {
        val spectra = client()
        val result = spectra.startRegistration()
        assertTrue(result.isFailure)
        val error = (result.exceptionOrNull() as? SpectraException)?.error
        assertEquals(SpectraError.NotInitialized, error)
    }

    @Test
    fun noDeviceUntilRegisteredAndPermissionGranted() = runTest {
        val spectra = client()
        spectra.initialize()
        assertTrue(spectra.devices.first().isEmpty(), "No device before anything happens")

        spectra.startRegistration()
        assertTrue(spectra.devices.first().isEmpty(), "Still none with registration alone")

        spectra.requestPermission(Permission.CAMERA)
        assertEquals(1, spectra.devices.first().size, "Device appears once permission is granted")
    }

    @Test
    fun deniedPermissionKeepsDeviceHidden() = runTest {
        val spectra = client(MockConfig(autoGrantPermissions = false))
        spectra.initialize()
        spectra.startRegistration()
        val status = spectra.requestPermission(Permission.CAMERA)
        assertEquals(PermissionStatus.DENIED, status)
        assertTrue(spectra.devices.first().isEmpty())
    }

    @Test
    fun failedRegistrationReportsFailedState() = runTest {
        val spectra = client(MockConfig(failRegistration = true))
        spectra.initialize()
        spectra.startRegistration()
        assertTrue(spectra.registrationState.first() is RegistrationState.Failed)
    }

    @Test
    fun fullHappyPathStreamsAndCaptures() = runTest {
        val spectra = client()
        spectra.initialize()
        spectra.startRegistration()
        spectra.requestPermission(Permission.CAMERA)

        val session = spectra.createSession(DeviceSelector.Auto).getOrThrow()
        session.start()
        assertEquals(SessionState.RUNNING, session.state.first { it == SessionState.RUNNING })

        val stream = session.openCameraStream().getOrThrow()
        stream.start()
        assertEquals(StreamState.STREAMING, stream.state.first { it == StreamState.STREAMING })

        val frame = stream.frames.first()
        assertTrue(frame.bytes.isNotEmpty())
        assertEquals(frame.width * frame.height * 4, frame.bytes.size, "RGBA payload size")

        val photo = stream.capturePhoto().getOrThrow()
        assertTrue(photo.bytes.isNotEmpty())

        stream.close()
        session.stop()
    }

    @Test
    fun cannotStreamFromSessionThatNeverStarted() = runTest {
        val spectra = client()
        spectra.initialize()
        spectra.startRegistration()
        spectra.requestPermission(Permission.CAMERA)
        val session = spectra.createSession().getOrThrow()
        val result = session.openCameraStream()
        assertTrue(result.isFailure)
    }

    @Test
    fun displayAcceptsContentOnceStarted() = runTest {
        val spectra = client()
        spectra.initialize()
        spectra.startRegistration()
        spectra.requestPermission(Permission.CAMERA)
        val session = spectra.createSession().getOrThrow()
        session.start()
        session.state.first { it == SessionState.RUNNING }

        val display = session.attachDisplay().getOrThrow()
        assertEquals(DisplayState.STARTED, display.state.first { it == DisplayState.STARTED })

        var tapped = false
        val result = display.sendContent {
            flexBox(paddingAll = 16) {
                text("Hello, glasses!", style = TextStyle.HEADING)
                button("Tap me", iconName = IconName.ARROW_RIGHT, onClick = { tapped = true })
            }
        }
        assertTrue(result.isSuccess)
        assertFalse(tapped, "Handler should not fire until something taps it")
    }

    @Test
    fun displayContentDslWiresHandlers() = runTest {
        val spectra = client()
        spectra.initialize()
        spectra.startRegistration()
        spectra.requestPermission(Permission.CAMERA)
        val session = spectra.createSession().getOrThrow()
        session.start()
        session.state.first { it == SessionState.RUNNING }
        val display = session.attachDisplay().getOrThrow()
        display.state.first { it == DisplayState.STARTED }

        val content = com.umain.spectra.display.displayContent {
            flexBox(paddingAll = 8) {
                button("One", onClick = {})
                button("Two", onClick = {})
                button("No handler") // intentionally none
            }
        }
        assertNotNull(content.root)
        assertEquals(2, content.handlers.size, "Two of three buttons have handlers")
    }
}
