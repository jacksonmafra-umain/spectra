package com.umain.spectra.mock

import com.umain.spectra.camera.CameraStream
import com.umain.spectra.camera.StreamConfiguration
import com.umain.spectra.core.DeviceId
import com.umain.spectra.core.DeviceSession
import com.umain.spectra.core.SessionState
import com.umain.spectra.core.SpectraError
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.spectraFailure
import com.umain.spectra.display.Display
import com.umain.spectra.display.DisplayConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A fake device session. It refuses to do anything useful until it's
 * [SessionState.RUNNING], exactly like the real one, so you can't accidentally
 * write code that "works" against the mock and then falls over on hardware
 * because you forgot to wait.
 */
internal class MockDeviceSession(
    override val deviceId: DeviceId,
    private val config: MockConfig,
    private val scope: CoroutineScope,
) : DeviceSession {

    private val _state = MutableStateFlow(SessionState.STOPPED)
    override val state: Flow<SessionState> = _state.asStateFlow()

    private var display: MockDisplay? = null

    override suspend fun start(): SpectraResult<Unit> {
        if (_state.value == SessionState.RUNNING) return Result.success(Unit)
        _state.value = SessionState.STARTING
        delay(config.sessionStartDelayMillis)
        _state.value = SessionState.RUNNING
        return Result.success(Unit)
    }

    override suspend fun stop(): SpectraResult<Unit> {
        _state.value = SessionState.STOPPED
        return Result.success(Unit)
    }

    override suspend fun openCameraStream(
        configuration: StreamConfiguration,
    ): SpectraResult<CameraStream> {
        if (_state.value != SessionState.RUNNING) {
            return spectraFailure(SpectraError.InvalidSessionState)
        }
        return Result.success(MockCameraStream(configuration, scope))
    }

    override suspend fun attachDisplay(
        configuration: DisplayConfiguration,
    ): SpectraResult<Display> {
        if (_state.value != SessionState.RUNNING) {
            return spectraFailure(SpectraError.InvalidSessionState)
        }
        // One display per session. Hand back the existing one rather than
        // quietly leaking a second; the SDK would have told you off anyway.
        display?.let { return Result.success(it) }
        return MockDisplay(scope).also { display = it }.let { Result.success(it) }
    }

    override suspend fun removeDisplay() {
        display?.close()
        display = null
    }
}
