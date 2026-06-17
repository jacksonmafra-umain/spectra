package com.umain.spectra

import com.umain.spectra.mock.MockConfig
import com.umain.spectra.mock.MockSpectraClient

/**
 * The entry point. Everything starts here.
 *
 * Spectra is a thin, opinionated Kotlin Multiplatform wrapper over Meta's
 * Wearables Device Access Toolkit. The toolkit is powerful and the toolkit is
 * also two separate native SDKs (Swift and Kotlin) with callbacks, result
 * builders, and a small library of ways to hold it wrong. Spectra gives you one
 * coroutine-and-[kotlinx.coroutines.flow.Flow] shaped API in `commonMain` and
 * hides the platform plumbing where it belongs.
 *
 * Pick your client:
 * - [mock] — runs anywhere, including the JVM and your CI, with no glasses and
 *   no Meta AI app. This is what the demo uses and what your tests should use.
 * - `Spectra.create(context)` (Android) / `Spectra.create()` (iOS) — the real
 *   thing, wired to the actual toolkit. Defined in the platform source sets
 *   because their constructors disagree about what they need, as platforms do.
 */
public object Spectra {

    /** The Spectra version, for logs, bug reports, and bragging in changelogs. */
    public const val VERSION: String = "0.1.0"

    /**
     * A fully in-memory [SpectraClient] that simulates a pair of glasses.
     *
     * It registers, grants permissions, surfaces a fake device, streams
     * procedurally-generated frames, and fires display tap handlers on a timer.
     * No hardware, no tokens, no Bluetooth gods to appease. Perfect for the
     * demo, for tests, and for building UI on a train.
     *
     * @param config knobs for the simulation — see [MockConfig]. The defaults
     *   describe a well-behaved device that does everything first time, which is
     *   the one piece of fiction in this entire library.
     */
    public fun mock(config: MockConfig = MockConfig()): SpectraClient = MockSpectraClient(config)
}
