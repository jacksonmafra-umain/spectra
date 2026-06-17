package com.umain.spectra.mock

import com.umain.spectra.camera.CameraStream
import com.umain.spectra.camera.Photo
import com.umain.spectra.camera.PhotoFormat
import com.umain.spectra.camera.StreamConfiguration
import com.umain.spectra.camera.StreamState
import com.umain.spectra.core.SpectraError
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.spectraFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A fake camera stream that emits procedurally-generated frames at the
 * requested frame rate. It honours the [StreamState] ladder faithfully, so code
 * written against the mock behaves the same against real glasses — which is the
 * entire reason a mock is worth having.
 */
internal class MockCameraStream(
    private val configuration: StreamConfiguration,
    private val scope: CoroutineScope,
) : CameraStream {

    private val _state = MutableStateFlow(StreamState.STOPPED)
    override val state: Flow<StreamState> = _state.asStateFlow()

    // Replay 1 so a late collector still gets the most recent frame instead of
    // staring at a blank surface until the next one arrives.
    private val _frames = MutableSharedFlow<com.umain.spectra.camera.VideoFrame>(replay = 1)
    override val frames: Flow<com.umain.spectra.camera.VideoFrame> = _frames.asSharedFlow()

    private var emitter: Job? = null
    private val frameIntervalMillis: Long = 1000L / configuration.frameRate.fps

    override suspend fun start(): SpectraResult<Unit> {
        if (_state.value == StreamState.STREAMING) return Result.success(Unit)
        _state.value = StreamState.STARTING
        delay(150)
        _state.value = StreamState.STARTED
        _state.value = StreamState.STREAMING
        emitter = scope.launch {
            val startedAt = nowMillis()
            while (isActive && _state.value == StreamState.STREAMING) {
                val t = nowMillis() - startedAt
                _frames.emit(MockFrames.frame(configuration.quality, t))
                delay(frameIntervalMillis)
            }
        }
        return Result.success(Unit)
    }

    override suspend fun stop(): SpectraResult<Unit> {
        if (_state.value == StreamState.CLOSED) {
            return spectraFailure(SpectraError.InvalidSessionState)
        }
        _state.value = StreamState.STOPPING
        emitter?.cancel()
        emitter = null
        _state.value = StreamState.STOPPED
        return Result.success(Unit)
    }

    override suspend fun capturePhoto(format: PhotoFormat): SpectraResult<Photo> {
        if (_state.value != StreamState.STREAMING) {
            return spectraFailure(SpectraError.InvalidSessionState)
        }
        // A real capture takes a beat. So does this one, for honesty's sake.
        delay(120)
        return Result.success(MockFrames.photo(configuration.quality, nowMillis()))
    }

    override suspend fun close() {
        emitter?.cancel()
        emitter = null
        _state.value = StreamState.CLOSED
    }
}
