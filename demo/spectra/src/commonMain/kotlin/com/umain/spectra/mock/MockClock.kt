package com.umain.spectra.mock

import kotlin.time.TimeSource

/**
 * A monotonic millisecond counter that works on every Kotlin target without
 * dragging in `kotlinx-datetime`. We don't need the wall clock, the calendar,
 * or your timezone's opinion — we need a number that goes up, for frame
 * timestamps and a drifting gradient. This is that number.
 */
private val origin = TimeSource.Monotonic.markNow()

/** Milliseconds elapsed since the mock subsystem first woke up. */
internal fun nowMillis(): Long = origin.elapsedNow().inWholeMilliseconds
