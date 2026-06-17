package com.umain.spectra.mock

import com.umain.spectra.core.SpectraError
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.spectraFailure
import com.umain.spectra.display.Display
import com.umain.spectra.display.DisplayContent
import com.umain.spectra.display.DisplayScope
import com.umain.spectra.display.DisplayState
import com.umain.spectra.display.displayContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A fake display capability.
 *
 * It walks the real lifecycle (`STARTING -> STARTED`) and accepts content. The
 * one mock-specific courtesy it adds is [lastContent]: a [StateFlow] of the most
 * recently sent screen, so a host app can render an on-phone "this is what the
 * glasses see" preview and dispatch tap [DisplayContent.handlers] itself. Real
 * glasses obviously don't expose this — they just show the thing on your face.
 */
internal class MockDisplay(scope: CoroutineScope) : Display {

    private val _state = MutableStateFlow(DisplayState.STARTING)
    override val state: Flow<DisplayState> = _state.asStateFlow()

    private val _lastContent = MutableStateFlow<DisplayContent?>(null)

    /** Mock-only window into what was last sent. The demo renders from this. */
    val lastContent: StateFlow<DisplayContent?> = _lastContent.asStateFlow()

    init {
        scope.launch {
            delay(200)
            _state.value = DisplayState.STARTED
        }
    }

    override suspend fun sendContent(block: DisplayScope.() -> Unit): SpectraResult<Unit> =
        sendContent(displayContent(block))

    override suspend fun sendContent(content: DisplayContent): SpectraResult<Unit> {
        if (_state.value != DisplayState.STARTED) {
            return spectraFailure(SpectraError.InvalidSessionState)
        }
        _lastContent.value = content
        return Result.success(Unit)
    }

    override suspend fun close() {
        _state.value = DisplayState.STOPPING
        _state.value = DisplayState.CLOSED
        _lastContent.value = null
    }
}
