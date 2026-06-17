package com.umain.spectra.core

import com.umain.spectra.camera.CameraStream
import com.umain.spectra.camera.StreamConfiguration
import com.umain.spectra.display.Display
import com.umain.spectra.display.DisplayConfiguration
import kotlinx.coroutines.flow.Flow

/**
 * The state of a device session, as decided by the glasses, not by you.
 *
 * This is the single most important thing to get right: the *device* drives
 * these transitions and tells you about them asynchronously. You react. You do
 * not assume the cause — the SDK deliberately won't tell you *why* a session
 * paused, only that it did. Resist the urge to guess.
 */
public enum class SessionState {
    /** Inactive and not reconnecting. Free your resources and wait for the user. */
    STOPPED,

    /** Being established. Almost there. */
    STARTING,

    /** Live. Sensors are flowing. Do your live work here and nowhere else. */
    RUNNING,

    /**
     * Temporarily suspended — the user glanced away, a notification barged in,
     * something. Hold your work, keep the connection, and wait. Do *not* try to
     * restart a paused session; it may resume on its own, or it may stop. Both
     * are fine. Pre-empting it is not.
     */
    PAUSED,
}

/**
 * A session with a specific pair of glasses.
 *
 * This is where the capabilities live. Start it, wait for [SessionState.RUNNING],
 * then add a [CameraStream] or attach a [Display]. Only one session can run on a
 * device at a time — the glasses are monogamous about this — so if [createSession]
 * elsewhere fails, this is usually why.
 */
public interface DeviceSession {

    /** Which glasses this session is bound to. */
    public val deviceId: DeviceId

    /** The live [SessionState]. Collect it and handle every value, including [SessionState.PAUSED]. */
    public val state: Flow<SessionState>

    /** Start the session. Watch [state] for [SessionState.RUNNING] before doing anything useful. */
    public suspend fun start(): SpectraResult<Unit>

    /** Stop the session and release the device. */
    public suspend fun stop(): SpectraResult<Unit>

    /**
     * Open a camera stream on this session.
     *
     * Remember the hardware rule: you can't reconfigure a stream within a
     * session. If you need a different [StreamConfiguration] later, stop this
     * session and start a fresh one.
     *
     * @param configuration the resolution and frame rate you'd like (the
     *   operative word being "like"; Bluetooth has final say).
     */
    public suspend fun openCameraStream(
        configuration: StreamConfiguration = StreamConfiguration(),
    ): SpectraResult<CameraStream>

    /**
     * Attach the display capability — Meta Ray-Ban Display only.
     *
     * Exactly one display per session. If you've already attached one, remove it
     * before adding another, or expect to be told off.
     */
    public suspend fun attachDisplay(
        configuration: DisplayConfiguration = DisplayConfiguration(),
    ): SpectraResult<Display>

    /** Remove the attached display, if any, without stopping the session. */
    public suspend fun removeDisplay()
}
