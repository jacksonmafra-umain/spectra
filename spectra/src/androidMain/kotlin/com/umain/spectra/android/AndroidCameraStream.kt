package com.umain.spectra.android

import com.umain.spectra.camera.CameraStream
import com.umain.spectra.camera.Photo
import com.umain.spectra.camera.PhotoFormat
import com.umain.spectra.camera.StreamState
import com.umain.spectra.camera.VideoFrame
import com.umain.spectra.core.SpectraError
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.spectraFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.meta.wearable.dat.camera.Stream as MetaStream

/**
 * Wraps Meta's camera [MetaStream].
 *
 * The frame mapping is where most of your CPU goes on a real device, so resist
 * the urge to do anything fancy here. Hand the bytes up and convert to a
 * `Bitmap` at the very edge, once, on the thread that's about to draw it.
 */
internal class AndroidCameraStream(
    private val meta: MetaStream,
) : CameraStream {

    override val state: Flow<StreamState> = meta.state.map(Mappers::streamState)

    override val frames: Flow<VideoFrame> = meta.videoStream.map { f ->
        VideoFrame(
            width = f.width,
            height = f.height,
            bytes = f.bytes,
            timestampMillis = f.timestampMs,
        )
    }

    override suspend fun start(): SpectraResult<Unit> = runCatchingSpectra { meta.start() }

    override suspend fun stop(): SpectraResult<Unit> = runCatchingSpectra { meta.stop() }

    override suspend fun capturePhoto(format: PhotoFormat): SpectraResult<Photo> =
        meta.capturePhoto().fold(
            onSuccess = { data ->
                Result.success(
                    Photo(
                        bytes = data.bytes,
                        format = PhotoFormat.JPEG,
                        width = data.width,
                        height = data.height,
                    ),
                )
            },
            onFailure = { error, _ -> spectraFailure(SpectraError.Backend(error.description, null)) },
        )

    override suspend fun close() {
        meta.close()
    }
}
