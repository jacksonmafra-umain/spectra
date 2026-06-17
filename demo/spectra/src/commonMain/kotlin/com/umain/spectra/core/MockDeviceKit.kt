package com.umain.spectra.core

import kotlinx.coroutines.flow.Flow

/**
 * A snapshot of the SDK's developer-only mock-device facility.
 *
 * @param enabled whether the mock kit is switched on.
 * @param pairedCount how many fake glasses are currently paired.
 */
public data class MockDeviceKitState(
    public val enabled: Boolean = false,
    public val pairedCount: Int = 0,
)

/**
 * A developer hook into the platform SDK's mock-device facility (Meta calls it
 * MockDeviceKit). Switch it on, pair a simulated Ray-Ban Meta, and the entire
 * register → session → stream pipeline runs end to end without a single real
 * pair of glasses going flat in a drawer.
 *
 * It exists so you can demo and test on a simulator, on CI, or on the train —
 * anywhere the actual hardware isn't. [SpectraClient.mockDeviceKit] returns null
 * on backends that can't fake it (a real Android build, say), so check before you
 * reach for it.
 */
public interface MockDeviceKit {
    /** The live [MockDeviceKitState]. Drive your debug UI off this. */
    public val state: Flow<MockDeviceKitState>

    /** Turn the mock kit on. Nothing is paired yet — call [pairGlasses] next. */
    public fun enable()

    /** Turn it off and unpair everything. Back to reality, such as it is. */
    public fun disable()

    /** Pair (and power on) a simulated Ray-Ban Meta so it shows up as an active device. */
    public fun pairGlasses()
}
