package com.umain.spectra.camera

/**
 * Video resolutions the glasses will agree to, give or take Bluetooth's opinion.
 *
 * These are *requests*, not guarantees. The link between the phone and the
 * glasses is Bluetooth Classic, which has the bandwidth of a garden hose with a
 * kink in it. The toolkit runs an automatic quality ladder: it drops resolution
 * a notch first, then frame rate (never below 15 fps), whenever the pipe gets
 * narrow. So you may ask for [HIGH] and quietly receive [MEDIUM]. Such is life.
 *
 * @property width pixel width at this preset.
 * @property height pixel height at this preset.
 */
public enum class VideoQuality(public val width: Int, public val height: Int) {
    /** 360 x 640. Small, fast, and honestly fine for most computer-vision work. */
    LOW(360, 640),

    /** 504 x 896. The sensible middle child nobody complains about. */
    MEDIUM(504, 896),

    /** 720 x 1280. Looks great in the demo, throttles first in the real world. */
    HIGH(720, 1280),
}

/**
 * Frame rates the hardware actually supports. Note that this is a fixed menu,
 * not a slider: ask for 23 fps and you'll get an exception, not a negotiation.
 */
public enum class FrameRate(public val fps: Int) {
    FPS_2(2),
    FPS_7(7),
    FPS_15(15),
    FPS_24(24),
    FPS_30(30),
    ;

    public companion object {
        /**
         * Maps a raw integer to a supported [FrameRate], or null if you've
         * picked a number the hardware has never heard of.
         */
        public fun fromFpsOrNull(fps: Int): FrameRate? = entries.firstOrNull { it.fps == fps }
    }
}

/**
 * What you want from the camera stream before reality intervenes.
 *
 * Counter-intuitive but true: requesting a *lower* resolution or frame rate can
 * yield a *better-looking* image, because the per-frame compression that fights
 * for bandwidth has less work to throw away. If your frames look like an
 * impressionist painting, try asking for less.
 *
 * @property quality desired resolution. A polite request, see [VideoQuality].
 * @property frameRate desired frames per second from the fixed menu.
 */
public data class StreamConfiguration(
    public val quality: VideoQuality = VideoQuality.MEDIUM,
    public val frameRate: FrameRate = FrameRate.FPS_24,
)
