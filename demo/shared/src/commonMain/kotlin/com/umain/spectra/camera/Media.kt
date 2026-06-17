package com.umain.spectra.camera

/**
 * A single frame off the glasses, as raw bytes plus the numbers you need to
 * make sense of them.
 *
 * We hand you bytes rather than a platform bitmap on purpose: commonMain has no
 * opinion on `UIImage` versus `android.graphics.Bitmap`, and neither should
 * your shared code. Convert at the edges, where the platform lives.
 *
 * @property width pixel width of this frame.
 * @property height pixel height of this frame.
 * @property bytes the pixel payload. Format depends on the negotiated codec;
 *   for the raw path this is packed RGBA. Don't hold onto these forever — at
 *   24 fps they pile up faster than unread email.
 * @property timestampMillis capture time in milliseconds since session start.
 */
public class VideoFrame(
    public val width: Int,
    public val height: Int,
    public val bytes: ByteArray,
    public val timestampMillis: Long,
)

/** The photo formats the camera will hand back. It's JPEG. It's always JPEG. */
public enum class PhotoFormat {
    JPEG,
}

/**
 * A still photo captured mid-stream.
 *
 * Capturing a photo doesn't stop the video; it just plucks a higher-intent
 * frame out of the flow. Think "screenshot", not "say cheese".
 *
 * @property bytes the encoded image data.
 * @property format the encoding, see [PhotoFormat].
 * @property width pixel width.
 * @property height pixel height.
 */
public class Photo(
    public val bytes: ByteArray,
    public val format: PhotoFormat,
    public val width: Int,
    public val height: Int,
)
