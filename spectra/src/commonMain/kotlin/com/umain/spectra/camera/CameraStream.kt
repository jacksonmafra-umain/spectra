package com.umain.spectra.camera

import com.umain.spectra.core.SpectraResult
import kotlinx.coroutines.flow.Flow

/**
 * The lifecycle of a camera stream, start to finish.
 *
 * The real SDK exposes this exact ladder. You don't drive it directly — the
 * device does — you just observe it and try to keep your UI honest about what's
 * happening.
 */
public enum class StreamState {
    /** Spinning up. Bytes imminent, allegedly. */
    STARTING,

    /** The stream exists and is connected, but frames haven't started flowing. */
    STARTED,

    /** Frames are arriving. This is the bit you built the app for. */
    STREAMING,

    /** Winding down on request. */
    STOPPING,

    /** Stopped, but the stream object is still around for a restart. */
    STOPPED,

    /** Gone. The stream is closed and this handle is now a paperweight. */
    CLOSED,
}

/**
 * A live camera stream from a pair of glasses.
 *
 * You don't construct this. You ask a
 * [com.umain.spectra.core.DeviceSession] for one and then babysit it: collect
 * [state], collect [frames], and call [capturePhoto] when something worth
 * keeping wanders into view.
 *
 * One hard-won caveat from the known-issues list: you cannot reconfigure a
 * stream within the same session. Want a different resolution? Stop the session
 * and start a new one. The hardware is not in a negotiating mood.
 */
public interface CameraStream {

    /** The current [StreamState], as a cold-then-hot [Flow] you can collect. */
    public val state: Flow<StreamState>

    /**
     * The actual video, one [VideoFrame] at a time. Collect this on a
     * background dispatcher and convert to a platform image at the very last
     * moment, on the main thread, when you're ready to draw.
     */
    public val frames: Flow<VideoFrame>

    /** Begin streaming. Idempotent enough to survive a double-tap. */
    public suspend fun start(): SpectraResult<Unit>

    /** Stop streaming but keep the handle. */
    public suspend fun stop(): SpectraResult<Unit>

    /**
     * Grab a still while the stream is live.
     *
     * @param format the desired encoding. There's only [PhotoFormat.JPEG], so
     *   this is less of a choice and more of a formality.
     * @return the [Photo], or a failure if the stream wasn't actually streaming.
     */
    public suspend fun capturePhoto(format: PhotoFormat = PhotoFormat.JPEG): SpectraResult<Photo>

    /** Close the stream and release everything. After this, get a new one. */
    public suspend fun close()
}
