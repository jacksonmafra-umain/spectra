package com.umain.spectra.mock

import com.umain.spectra.camera.Photo
import com.umain.spectra.camera.PhotoFormat
import com.umain.spectra.camera.VideoFrame
import com.umain.spectra.camera.VideoQuality

/**
 * Makes pretend pixels so the demo has something to draw.
 *
 * Real frames come off a camera sensor. These come off a `for` loop: a slow
 * diagonal gradient that drifts with time, so a UI rendering them looks alive
 * rather than frozen. Nobody will mistake it for the view through actual
 * glasses, and that's fine — it's a stand-in, not a forgery.
 *
 * Kept internal because the only thing that should ever call this is the mock.
 */
internal object MockFrames {

    /**
     * Build one RGBA frame for the given [quality] at time [timestampMillis].
     * The colour ramp is offset by the timestamp so consecutive frames differ
     * and motion is visible.
     */
    fun frame(quality: VideoQuality, timestampMillis: Long): VideoFrame {
        val width = quality.width
        val height = quality.height
        val bytes = ByteArray(width * height * 4)
        val drift = (timestampMillis / 16) % 256

        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                bytes[i] = ((x * 255 / width + drift) % 256).toByte()      // R
                bytes[i + 1] = ((y * 255 / height + drift) % 256).toByte() // G
                bytes[i + 2] = (((x + y) / 2 + drift) % 256).toByte()      // B
                bytes[i + 3] = 255.toByte()                                // A
                i += 4
            }
        }
        return VideoFrame(width, height, bytes, timestampMillis)
    }

    /**
     * "Capture" a still by snapshotting the current generated frame and calling
     * it a JPEG. It is not a JPEG. It is RGBA bytes wearing a JPEG name tag, and
     * for a mock that is precisely as honest as it needs to be.
     */
    fun photo(quality: VideoQuality, timestampMillis: Long): Photo {
        val f = frame(quality, timestampMillis)
        return Photo(
            bytes = f.bytes,
            format = PhotoFormat.JPEG,
            width = f.width,
            height = f.height,
        )
    }
}
