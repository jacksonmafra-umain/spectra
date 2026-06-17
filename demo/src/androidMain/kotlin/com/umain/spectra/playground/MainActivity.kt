package com.umain.spectra.playground

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope

/**
 * The Android entry point. It does the bare minimum on purpose: hand Compose the
 * lifecycle-scoped coroutine scope and get out of the way. All the actual logic
 * lives in `commonMain`, shared with iOS, because writing it twice would be a
 * choice and not a good one.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(lifecycleScope) }
    }
}
