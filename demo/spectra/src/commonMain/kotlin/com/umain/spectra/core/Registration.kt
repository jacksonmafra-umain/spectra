package com.umain.spectra.core

/**
 * Where your app stands in the eyes of the Meta AI app.
 *
 * Registration is the one-time handshake that links your app to a user's
 * glasses. It deeplinks into the Meta AI app, the user nods, and they get
 * bounced back to you. Until that happens, you are a stranger, and the glasses
 * don't talk to strangers.
 */
public sealed interface RegistrationState {

    /** The default state of the universe: nobody has registered anything. */
    public data object NotRegistered : RegistrationState

    /** The deeplink is out and we're waiting for the user to finish in Meta AI. */
    public data object Registering : RegistrationState

    /** We're in. The app is a recognised integration and devices can appear. */
    public data object Registered : RegistrationState

    /**
     * Registration failed. Usually a misconfigured app id, a client token typo,
     * or the user changing their mind halfway through. Computers rarely fail for
     * interesting reasons.
     *
     * @property reason a description of what went wrong, for the logs you'll
     *   pretend to read later.
     */
    public data class Failed(public val reason: String) : RegistrationState
}
