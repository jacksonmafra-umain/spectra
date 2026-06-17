package com.umain.spectra.core

import kotlinx.coroutines.flow.Flow

/**
 * Which Bluetooth audio profile is currently driving the glasses.
 *
 * The two are mutually exclusive on the hardware: switching the microphone on
 * (HFP) kicks playback out of high-fidelity [A2DP_PLAYBACK] and down to 8 kHz
 * mono for the duration. That's a Bluetooth fact of life, not a Spectra choice.
 */
public enum class AudioProfile {
    /** Nothing routed — idle. */
    NONE,

    /** High-quality output only (44.1/48 kHz). Music, media, text-to-speech. */
    A2DP_PLAYBACK,

    /** Bidirectional, 8 kHz mono. The only way to capture the wearer's voice. */
    HFP_MIC,
}

/**
 * A snapshot of the glasses' audio routing.
 *
 * @param profile the active [AudioProfile].
 * @param micLevel normalized input level 0..1 while capturing (for a VU meter); 0 otherwise.
 * @param isPlaying whether something is currently playing out to the glasses.
 * @param routedToGlasses whether the OS actually put the route on the glasses
 *   (Bluetooth has the final say — it might land on the phone speaker instead).
 */
public data class AudioState(
    public val profile: AudioProfile = AudioProfile.NONE,
    public val micLevel: Float = 0f,
    public val isPlaying: Boolean = false,
    public val routedToGlasses: Boolean = false,
)

/**
 * Audio to and from the glasses.
 *
 * Deliberately NOT part of the Device Access Toolkit: on real hardware the
 * Ray-Ban speakers and microphone are plain Bluetooth (A2DP for output, HFP for
 * the mic), shared with the system audio stack. So this wraps the *platform*
 * audio session, not the DAT SDK — which is exactly why it lives behind its own
 * capability instead of on a [DeviceSession]. Null on backends that can't route
 * audio.
 *
 * Heads-up if you ever combine the mic with a camera stream: configure and start
 * HFP *before* you start the DAT camera stream, or the audio route can fail
 * silently. Add stream → start mic → wait for the route → start stream.
 */
public interface SpectraAudio {
    /** Live [AudioState] — drive your speaker/mic UI and VU meter off this. */
    public val state: Flow<AudioState>

    /** Speak [text] out to the glasses over A2DP (high quality). A no-asset way to prove output works. */
    public suspend fun playToGlasses(text: String)

    /** Stop any current playback and release the playback route. */
    public suspend fun stopPlayback()

    /** Start capturing the wearer's voice over HFP. Emits [AudioState.micLevel] while live. */
    public suspend fun startMicCapture()

    /** Stop capturing and hand the route back. */
    public suspend fun stopMicCapture()
}
