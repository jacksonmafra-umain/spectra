package com.umain.spectra.android

import com.umain.spectra.core.SpectraError
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.spectraFailure
import com.umain.spectra.display.Display
import com.umain.spectra.display.DisplayContent
import com.umain.spectra.display.DisplayScope
import com.umain.spectra.display.DisplayState
import com.umain.spectra.display.displayContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.meta.wearable.dat.display.Display as MetaDisplay

/**
 * Wraps Meta's [MetaDisplay]. Builds a Spectra [DisplayContent] from your DSL,
 * then has [DisplayRenderer] replay it into Meta's builder. Every send replaces
 * the whole screen — that's the SDK's rule, not ours, so don't go looking for a
 * partial-update method that was never going to exist.
 */
internal class AndroidDisplay(
    private val meta: MetaDisplay,
) : Display {

    override val state: Flow<DisplayState> = meta.state.map { metaState ->
        when (metaState.toString().uppercase()) {
            "STARTING" -> DisplayState.STARTING
            "STARTED" -> DisplayState.STARTED
            "STOPPING" -> DisplayState.STOPPING
            "CLOSED" -> DisplayState.CLOSED
            else -> DisplayState.STOPPED
        }
    }

    override suspend fun sendContent(block: DisplayScope.() -> Unit): SpectraResult<Unit> =
        sendContent(displayContent(block))

    override suspend fun sendContent(content: DisplayContent): SpectraResult<Unit> =
        meta.sendContent { DisplayRenderer.render(this, content) }.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { error, _ -> spectraFailure(SpectraError.Backend(error.description, null)) },
        )

    override suspend fun close() {
        meta.close()
    }
}
