package com.umain.spectra

import com.umain.spectra.ios.IosSpectraClient

/**
 * Create a [SpectraClient] on iOS.
 *
 * A word of brutal honesty, since you'll find out anyway: Meta's iOS toolkit
 * (`MWDATCore` / `MWDATCamera`) ships as a *Swift* Swift Package. Kotlin/Native
 * cannot call Swift directly — it speaks Objective-C interop. So a production
 * iOS integration needs a thin Swift "shim" framework that re-exposes the bits
 * you use with `@objc`, which `cinterop` then lets this module call.
 *
 * [IosSpectraClient] is that bridge, scaffolded and documented but deliberately
 * not pretending to be finished. The shim is project-specific (it depends on
 * which capabilities you actually use), so it lives in your app, not in a
 * general-purpose library that would only guess wrong.
 *
 * In the meantime, [Spectra.mock] runs perfectly well on iOS — including in a
 * Compose Multiplatform or SwiftUI preview — so your UI work isn't held hostage
 * by a bridging framework you haven't written yet.
 */
public fun Spectra.create(): SpectraClient = IosSpectraClient()
