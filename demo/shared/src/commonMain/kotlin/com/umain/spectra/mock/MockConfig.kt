package com.umain.spectra.mock

/**
 * Tuning knobs for the simulated glasses.
 *
 * The defaults describe a flawless device on a flawless day: everything is
 * granted, nothing fails, and the Bluetooth never so much as hiccups. That
 * device does not exist, which is rather the point of being able to flip these
 * and rehearse the failure paths before real hardware does it to you in front
 * of a customer.
 *
 * @property deviceName the name the fake glasses report.
 * @property deviceModel the model string they report.
 * @property registrationDelayMillis how long the fake registration deeplink
 *   pretends to take. Real ones take longer and involve a second app.
 * @property permissionDelayMillis simulated time for the permission round-trip.
 * @property sessionStartDelayMillis simulated time to bring a session up.
 * @property autoGrantPermissions if false, every permission request comes back
 *   [com.umain.spectra.core.PermissionStatus.DENIED] — handy for testing the
 *   "user said no" branch you'd otherwise never exercise.
 * @property failRegistration if true, registration ends in
 *   [com.umain.spectra.core.RegistrationState.Failed]. For when you want to see
 *   your error UI without misconfiguring a real app id on purpose.
 */
public data class MockConfig(
    public val deviceName: String = "Ray-Ban Meta (Mock)",
    public val deviceModel: String = "mock-glasses-gen2",
    public val registrationDelayMillis: Long = 600,
    public val permissionDelayMillis: Long = 500,
    public val sessionStartDelayMillis: Long = 400,
    public val autoGrantPermissions: Boolean = true,
    public val failRegistration: Boolean = false,
)
