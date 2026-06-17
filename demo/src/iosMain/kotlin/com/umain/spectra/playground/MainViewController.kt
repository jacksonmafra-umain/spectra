package com.umain.spectra.playground

import androidx.compose.ui.window.ComposeUIViewController

/**
 * The iOS entry point. An Xcode wrapper project imports the `PlaygroundShared`
 * framework and presents this view controller. Same Compose UI as Android, same
 * shared [PlaygroundState] — the only thing that differs is who calls it.
 */
fun MainViewController() = ComposeUIViewController { App() }
