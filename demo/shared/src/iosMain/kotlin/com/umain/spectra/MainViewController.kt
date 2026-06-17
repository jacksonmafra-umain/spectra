package com.umain.spectra

import androidx.compose.ui.window.ComposeUIViewController

/**
 * iOS entry point. The Swift app passes in its [SpectraNativeBridge]
 * implementation (wrapping Meta's SDK); we build the real client from it so the
 * "Glasses (real)" toggle lights up. Pass nothing for a mock-only build.
 */
fun MainViewController(bridge: SpectraNativeBridge? = null) = ComposeUIViewController {
    App(realClient = bridge?.let { Spectra.create(it) })
}
