package com.umain.spectra.core

/**
 * Every way Spectra can ruin your day, enumerated so you don't have to guess.
 *
 * The underlying Meta SDK loves returning errors that politely decline to
 * explain themselves. We translate those into something you can actually
 * `when`-branch on, instead of parsing a string and praying.
 *
 * @property message a human-readable description. Human as in "the kind of
 *   human who reads stack traces for fun", but human nonetheless.
 */
public sealed class SpectraError(public val message: String) {

    /**
     * You called something before [com.umain.spectra.SpectraClient.initialize].
     * The SDK is not psychic. It needs to be switched on first, like everything
     * else that has ever disappointed you.
     */
    public data object NotInitialized :
        SpectraError("Spectra was used before it was initialized. Call initialize() first.")

    /**
     * No registration, no party. The user hasn't linked your app to their
     * glasses through the Meta AI app yet, so there is nothing to talk to.
     */
    public data object NotRegistered :
        SpectraError("The app is not registered with the Meta AI app. Run registration first.")

    /**
     * The user said no. Or "not right now". Either way, you don't get the camera.
     * Respect it; nagging is how apps get uninstalled.
     */
    public data object PermissionDenied :
        SpectraError("The required permission was denied or not granted.")

    /**
     * Registration and permissions are fine, but there are no glasses within
     * reach that are willing to cooperate. Possibly folded, possibly flat,
     * possibly in a different room having a better time than you.
     */
    public data object NoDeviceAvailable :
        SpectraError("No wearable device is currently available to start a session.")

    /**
     * The glasses left the conversation. Hinges closed, Bluetooth dropped, or
     * the user simply took them off mid-sentence. Rude, but allowed.
     */
    public data object DeviceDisconnected :
        SpectraError("The wearable device disconnected during the operation.")

    /**
     * You asked a session to do something it's in no state to do — like
     * streaming from a session that's already stopped. Timing is everything.
     */
    public data object InvalidSessionState :
        SpectraError("The session is not in a valid state for this operation.")

    /**
     * Something failed deeper in Meta's SDK and we're passing the bad news along
     * verbatim. When you reach this branch, the docs and a strong coffee are
     * your friends.
     *
     * @property cause the original throwable, if the SDK deigned to provide one.
     */
    public class Backend(
        message: String,
        public val cause: Throwable? = null,
    ) : SpectraError(message)
}

/**
 * A [Result] that fails with a [SpectraError] rather than a naked [Throwable],
 * because "it broke" is not a diagnosis.
 *
 * We deliberately reuse Kotlin's [Result] instead of inventing a bespoke
 * `Either` type. Three competing `Result` classes in one codebase is how
 * libraries earn their one-star reviews.
 */
public typealias SpectraResult<T> = Result<T>

/** Wraps a [SpectraError] into a failed [SpectraResult]. Saves you the ceremony. */
public fun <T> spectraFailure(error: SpectraError): SpectraResult<T> =
    Result.failure(SpectraException(error))

/**
 * The throwable carrier for a [SpectraError]. It exists only because [Result]
 * insists on a [Throwable] and [SpectraError] had the audacity not to be one.
 */
public class SpectraException(public val error: SpectraError) : Exception(error.message)
