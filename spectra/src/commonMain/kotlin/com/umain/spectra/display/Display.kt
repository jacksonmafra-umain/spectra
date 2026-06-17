package com.umain.spectra.display

import com.umain.spectra.core.SpectraResult
import kotlinx.coroutines.flow.Flow

/**
 * The lifecycle of the display capability on Meta Ray-Ban Display glasses.
 *
 * `stopped -> starting -> started -> stopping -> stopped`, with [CLOSED] as the
 * terminal "this capability has been removed from the session" state. You can
 * only [Display.sendContent] while [STARTED]; everything else is a waiting room.
 */
public enum class DisplayState {
    /** Not active. The factory setting. */
    STOPPED,

    /** Connecting to the glasses' display service. Hold your content. */
    STARTING,

    /** Ready. Send content now or forever hold your FlexBox. */
    STARTED,

    /** Shutting down. */
    STOPPING,

    /** Removed from the session entirely. This handle is done. */
    CLOSED,
}

/**
 * Ways the display can fail, mapped from the SDK so you can branch instead of
 * squinting at strings.
 */
public enum class DisplayError {
    /** The device refused the display capability. Check your Developer Center config. */
    CAPABILITY_DENIED,

    /** The glasses disconnected. Prompt a reconnect; don't pretend it's fine. */
    DEVICE_DISCONNECTED,

    /** You sent content while the display wasn't [DisplayState.STARTED]. Wait. */
    INVALID_SESSION_STATE,

    /** The content reached the glasses but failed to render. Simplify and retry. */
    RENDERING_FAILED,

    /** The catch-all. Somewhere, something, somehow. */
    UNEXPECTED_ERROR,
}

/** Configuration for attaching a display. Empty for now; reserved for later regret. */
public class DisplayConfiguration

/**
 * A live handle to the glasses' display.
 *
 * Get one from [com.umain.spectra.core.DeviceSession.attachDisplay], wait for
 * [state] to hit [DisplayState.STARTED], then push screens with [sendContent].
 * Every send replaces the entire display — there's no diffing, no partial
 * update, no clever reconciliation. You are the reconciler now. Manage your
 * navigation and data on the phone; the glasses remember nothing.
 */
public interface Display {

    /** The current [DisplayState] as a [Flow]. Gate your sends on [DisplayState.STARTED]. */
    public val state: Flow<DisplayState>

    /**
     * Push a whole screen to the glasses.
     *
     * Build it with the [displayContent] DSL inline:
     * ```
     * display.sendContent {
     *     flexBox(direction = Direction.COLUMN, gap = 12, paddingAll = 16) {
     *         text("Oil Change Guide", style = TextStyle.HEADING)
     *         button("Start", iconName = IconName.ARROW_RIGHT, onClick = ::showFirstStep)
     *     }
     * }
     * ```
     *
     * @return success once the glasses accept it, or a failure carrying a
     *   [DisplayError] when they don't.
     */
    public suspend fun sendContent(block: DisplayScope.() -> Unit): SpectraResult<Unit>

    /**
     * Push a pre-built [DisplayContent]. Useful when you assemble screens
     * somewhere other than the call site — say, a navigator that owns the tree.
     */
    public suspend fun sendContent(content: DisplayContent): SpectraResult<Unit>

    /** Detach the display and let the session move on. */
    public suspend fun close()
}
