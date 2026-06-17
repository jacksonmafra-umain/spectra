package com.umain.spectra.display

/**
 * Layout direction for a [FlexBox], lifted wholesale from every CSS flexbox you
 * have ever fought with. If you've centred a div, you already know this API.
 */
public enum class Direction { ROW, COLUMN, ROW_REVERSE, COLUMN_REVERSE }

/**
 * Alignment along an axis. Used for both main-axis [FlexBox.alignment] and
 * cross-axis [FlexBox.crossAlignment], because having two words for the same
 * four concepts would help nobody.
 */
public enum class Alignment { START, CENTER, END, STRETCH }

/**
 * Text presets matching the glasses' design system. You get three. This is a
 * 600x600 display strapped to someone's face, not a desktop word processor, so
 * three is plenty and arguably one too many.
 */
public enum class TextStyle { HEADING, BODY, META }

/** Text contrast. High for the words that matter, low for the words that don't. */
public enum class TextColor { PRIMARY, SECONDARY }

/** How big an image wants to be. [ICON] is inline-small, [FILL] eats the space. */
public enum class ImageSize { ICON, FILL }

/** Corner rounding, for when sharp rectangles feel a touch too brutalist. */
public enum class CornerRadius { NONE, SMALL, MEDIUM }

/** Button emphasis, from "press me" to "you could press me, I suppose". */
public enum class ButtonStyle { PRIMARY, SECONDARY, OUTLINE }

/** Icon fill. Solid or hollow. The entire decision tree. */
public enum class IconStyle { FILLED, OUTLINE }

/**
 * A deliberately small slice of the built-in icon catalogue.
 *
 * The real SDK ships 100+ glyphs. Hard-coding all of them here would be a
 * data-entry penance with no upside, so we expose the common ones and let you
 * fall back to a custom [image] for anything exotic. If you find yourself
 * needing the full list often, that's a pull request waiting to happen.
 */
public enum class IconName {
    ARROW_LEFT,
    ARROW_RIGHT,
    CHECKMARK_CIRCLE,
    BELL,
    GEAR,
    HEART,
    CAMERA,
    PLAY,
    PAUSE,
    CLOSE,
}

/**
 * The serialisable description of one piece of on-glasses UI.
 *
 * This is a plain data tree, not a live view. Each [com.umain.spectra.display.Display.sendContent]
 * call serialises the whole tree, ships it over Bluetooth, and replaces whatever
 * was on the display before. There is no partial update and there is no
 * retained state on the glasses — your phone is the single source of truth, full
 * stop. Build the entire screen every time. It's less wasteful than it sounds.
 */
public sealed interface DisplayNode {

    /**
     * A flex container. The root of every layout and, usually, half the nodes
     * inside it too.
     *
     * @property handlerId opaque id used to route tap callbacks back to your
     *   [onClick]. You will never need to read this; the backend does.
     */
    public data class FlexBox(
        val direction: Direction = Direction.COLUMN,
        val gap: Int = 0,
        val alignment: Alignment = Alignment.START,
        val crossAlignment: Alignment = Alignment.START,
        val wrap: Boolean = false,
        val paddingTop: Int = 0,
        val paddingBottom: Int = 0,
        val paddingStart: Int = 0,
        val paddingEnd: Int = 0,
        val flexGrow: Float = 0f,
        val flexShrink: Float = 1f,
        val children: List<DisplayNode> = emptyList(),
        val handlerId: String? = null,
    ) : DisplayNode

    /** Styled text. The workhorse. */
    public data class Text(
        val value: String,
        val style: TextStyle = TextStyle.BODY,
        val color: TextColor = TextColor.PRIMARY,
    ) : DisplayNode

    /**
     * An image from an `https` URL. Keep it at or below 600x600 — the display
     * can't show more and Bluetooth will punish you for trying.
     */
    public data class Image(
        val uri: String,
        val sizePreset: ImageSize = ImageSize.FILL,
        val cornerRadius: CornerRadius = CornerRadius.NONE,
    ) : DisplayNode

    /** A tappable button with a label and an optional icon. */
    public data class Button(
        val label: String,
        val style: ButtonStyle = ButtonStyle.PRIMARY,
        val iconName: IconName? = null,
        val handlerId: String? = null,
    ) : DisplayNode

    /** A single system icon. */
    public data class Icon(
        val name: IconName,
        val style: IconStyle = IconStyle.FILLED,
    ) : DisplayNode

    /**
     * Full-screen MP4 playback. Constraints are unforgiving: MP4 only, `https`
     * only, 400px per side max, 70,000 total pixels, one at a time. Treat it as
     * "short clip", never "feature film".
     */
    public data class Video(
        val uri: String,
    ) : DisplayNode
}
