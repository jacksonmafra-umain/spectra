package com.umain.spectra.android

import com.umain.spectra.camera.CameraStream
import com.umain.spectra.camera.Photo
import com.umain.spectra.camera.PhotoFormat
import com.umain.spectra.camera.StreamConfiguration
import com.umain.spectra.camera.StreamState
import com.umain.spectra.camera.VideoFrame
import com.umain.spectra.camera.VideoQuality
import com.umain.spectra.core.DeviceId
import com.umain.spectra.core.DeviceSession
import com.umain.spectra.core.SessionState
import com.umain.spectra.core.SpectraError
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.spectraFailure
import com.umain.spectra.display.Display
import com.umain.spectra.display.DisplayConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.nio.ByteBuffer

import com.meta.wearable.dat.core.session.DeviceSession as MetaSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.session.addStream
import com.meta.wearable.dat.camera.Stream as MetaStream
import com.meta.wearable.dat.camera.types.PhotoData
import com.meta.wearable.dat.camera.types.StreamConfiguration as MetaStreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState as MetaStreamState
import com.meta.wearable.dat.camera.types.VideoQuality as MetaVideoQuality

/**
 * Wraps Meta's [MetaSession]. Maps its [DeviceSessionState] to Spectra's
 * [SessionState] (STARTED becomes RUNNING; IDLE/STOPPING/STOPPED collapse to
 * STOPPED) and delegates streaming. Display is stubbed in this demo build — it's
 * Ray-Ban Display only, and wiring it is a separate exercise.
 */
internal class AndroidDeviceSession(private val meta: MetaSession) : DeviceSession {

    // The SDK session exposes no identifier; the demo doesn't need a real one.
    override val deviceId: DeviceId = DeviceId("active")

    override val state: Flow<SessionState> = meta.state.map { s ->
        when (s) {
            DeviceSessionState.STARTED -> SessionState.RUNNING
            DeviceSessionState.STARTING -> SessionState.STARTING
            DeviceSessionState.PAUSED -> SessionState.PAUSED
            DeviceSessionState.IDLE,
            DeviceSessionState.STOPPING,
            DeviceSessionState.STOPPED -> SessionState.STOPPED
        }
    }

    override suspend fun start(): SpectraResult<Unit> = runCatchingSpectra { meta.start() }
    override suspend fun stop(): SpectraResult<Unit> = runCatchingSpectra { meta.stop() }

    override suspend fun openCameraStream(configuration: StreamConfiguration): SpectraResult<CameraStream> =
        meta.addStream(metaConfig(configuration)).fold(
            onSuccess = { stream -> Result.success(AndroidCameraStream(stream)) },
            onFailure = { error, cause -> spectraFailure(SpectraError.Backend(error.description, cause)) },
        )

    override suspend fun attachDisplay(configuration: DisplayConfiguration): SpectraResult<Display> =
        spectraFailure(
            SpectraError.Backend(
                "Display delegation isn't wired in this demo build (Ray-Ban Display only). " +
                    "Camera streaming works; see the docs to add display support.",
            ),
        )

    override suspend fun removeDisplay() { /* no display attached */ }

    private fun metaConfig(c: StreamConfiguration) =
        MetaStreamConfiguration(videoQuality = metaQuality(c.quality), frameRate = c.frameRate.fps)

    private fun metaQuality(q: VideoQuality) = when (q) {
        VideoQuality.LOW -> MetaVideoQuality.LOW
        VideoQuality.MEDIUM -> MetaVideoQuality.MEDIUM
        VideoQuality.HIGH -> MetaVideoQuality.HIGH
    }
}

/** Wraps Meta's camera [MetaStream]. */
internal class AndroidCameraStream(private val meta: MetaStream) : CameraStream {

    override val state: Flow<StreamState> = meta.state.map(::mapState)

    override val frames: Flow<VideoFrame> = meta.videoStream.map { f ->
        VideoFrame(
            width = f.width,
            height = f.height,
            bytes = f.buffer.toByteArray(),
            timestampMillis = f.presentationTimeUs / 1000,
        )
    }

    override suspend fun start(): SpectraResult<Unit> =
        meta.start().fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error, cause -> spectraFailure(SpectraError.Backend(error.description, cause)) },
        )

    override suspend fun stop(): SpectraResult<Unit> = runCatchingSpectra { meta.stop() }

    override suspend fun capturePhoto(format: PhotoFormat): SpectraResult<Photo> =
        meta.capturePhoto().fold(
            onSuccess = { data -> Result.success(toPhoto(data)) },
            onFailure = { error, cause -> spectraFailure(SpectraError.Backend(error.description, cause)) },
        )

    override suspend fun close() = meta.close()

    private fun mapState(s: MetaStreamState): StreamState = when (s) {
        MetaStreamState.STARTING -> StreamState.STARTING
        MetaStreamState.STARTED -> StreamState.STARTED
        MetaStreamState.STREAMING -> StreamState.STREAMING
        MetaStreamState.STOPPING -> StreamState.STOPPING
        MetaStreamState.STOPPED -> StreamState.STOPPED
        MetaStreamState.CLOSED -> StreamState.CLOSED
    }

    private fun toPhoto(data: PhotoData): Photo = when (data) {
        is PhotoData.HEIC -> Photo(data.data.toByteArray(), PhotoFormat.JPEG, 0, 0)
        is PhotoData.Bitmap -> Photo(ByteArray(0), PhotoFormat.JPEG, data.bitmap.width, data.bitmap.height)
        else -> Photo(ByteArray(0), PhotoFormat.JPEG, 0, 0)
    }
}

private fun ByteBuffer.toByteArray(): ByteArray {
    val dup = duplicate()
    val out = ByteArray(dup.remaining())
    dup.get(out)
    return out
}
