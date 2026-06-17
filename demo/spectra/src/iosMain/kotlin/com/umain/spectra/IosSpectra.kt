package com.umain.spectra

import com.umain.spectra.ios.IosSpectraClient
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * Build a real, hardware-backed [SpectraClient] on iOS from a Swift-implemented
 * [SpectraNativeBridge]. Your `iosApp` constructs the bridge (wrapping
 * `Wearables.shared`) and hands it to `MainViewController`, which calls this.
 */
public fun Spectra.create(bridge: SpectraNativeBridge): SpectraClient =
    IosSpectraClient(bridge)

/**
 * Copy an [NSData] into a Kotlin [ByteArray]. This is the one genuinely fiddly
 * line of the whole bridge — pinning the array and `memcpy`-ing the bytes across
 * the Obj-C boundary. Do it once, here, rather than scattering cinterop through
 * the codebase.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned ->
            memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
        }
    }
}
