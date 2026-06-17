package com.umain.spectra.core

/**
 * The things you can ask the user's glasses for permission to use.
 *
 * Note that microphone access on real hardware goes through the platform's
 * Hands-Free Profile (HFP) Bluetooth plumbing, not through the Meta AI app's
 * permission flow. We model it here for completeness, but on Android and iOS
 * you'll still request the mic through the usual OS dialog. Two systems, one
 * microphone, infinite confusion.
 */
public enum class Permission {
    /** The camera. The headline act. The reason anyone installs your app. */
    CAMERA,

    /** The microphones. Granted via platform HFP, not the Meta AI app. */
    MICROPHONE,
}

/**
 * Whether the user has actually let you do the thing.
 *
 * Camera permission is checked across *all* the user's linked glasses: if any
 * one pair has said yes, you get [GRANTED]. The toolkit hides the per-device
 * bookkeeping, which is genuinely one of the nicer things it does for you.
 */
public enum class PermissionStatus {
    /** Yes. Go. Stream responsibly. */
    GRANTED,

    /** No. Explicitly denied. Don't ask again on a loop like a needy ex. */
    DENIED,

    /** The user hasn't been asked yet. The ball is in your court. */
    NOT_DETERMINED,

    /**
     * Can't be determined right now, usually because every linked device has
     * wandered off and disconnected. Try again when something's actually there.
     */
    UNAVAILABLE,
}
