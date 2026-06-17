package com.umain.spectra.core

/**
 * A stable identifier for a pair of glasses.
 *
 * It's a string wearing a type so you can't accidentally pass a session id, a
 * user id, or your lunch order where a device id was expected. The compiler is
 * cheaper than the bug.
 */
public data class DeviceId(public val value: String)

/**
 * A pair of Meta AI glasses that your app knows about.
 *
 * Remember the catch from the integration guides: a device won't even show up
 * in the device list until the user has granted at least one permission (camera
 * being the obvious one). No permission, no device, no matter how connected it
 * physically is.
 *
 * @property id the stable identifier you'll quote everywhere else.
 * @property name a friendly label, e.g. "Ray-Ban Meta". Friendly, not unique.
 * @property model the hardware model, when the SDK bothers to tell us.
 * @property isAvailable whether the device is reachable right now. Hinges open,
 *   Bluetooth up, glasses nearby. This flickers; treat it as a live fact, not a
 *   permanent truth.
 */
public data class WearableDevice(
    public val id: DeviceId,
    public val name: String,
    public val model: String,
    public val isAvailable: Boolean,
)

/**
 * How Spectra should pick which glasses to start a session with.
 *
 * Most apps want [Auto] and should resist the urge to build a device picker
 * for the seventeen users who own two pairs. If you are one of those apps,
 * [Specific] is right there.
 */
public sealed interface DeviceSelector {

    /**
     * Let the toolkit make a sensible choice. It usually picks the connected
     * device that's most likely to work, which is more than can be said for
     * most automatic choices in software.
     */
    public data object Auto : DeviceSelector

    /**
     * You know exactly which glasses you want. Bring your own [DeviceId],
     * presumably from a picker you built because you have power users and a
     * support burden.
     */
    public data class Specific(public val deviceId: DeviceId) : DeviceSelector
}
