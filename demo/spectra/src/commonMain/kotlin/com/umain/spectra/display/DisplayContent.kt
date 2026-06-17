package com.umain.spectra.display

/**
 * A fully-built screen, ready to be flung at the glasses.
 *
 * It's two things: the [root] node tree (what to draw) and a map of tap
 * [handlers] (what to do when the user pokes it). You never build this by hand
 * — the [displayContent] DSL does it for you and keeps the two in sync, which
 * is the whole point of having a DSL.
 *
 * @property root the top of the [DisplayNode] tree.
 * @property handlers tap callbacks keyed by the opaque handler id baked into
 *   the relevant nodes.
 */
public class DisplayContent internal constructor(
    public val root: DisplayNode,
    public val handlers: Map<String, () -> Unit>,
)

/**
 * Mutable scratch space the DSL uses while building a screen. Carries the shared
 * handler registry and an id counter so every tappable thing gets a unique tag.
 * Internal on purpose: you should never see this.
 */
public class DisplayContentBuilder internal constructor() {
    internal val handlers: MutableMap<String, () -> Unit> = mutableMapOf()
    private var counter: Int = 0

    internal fun register(onClick: (() -> Unit)?): String? {
        if (onClick == null) return null
        val id = "h${counter++}"
        handlers[id] = onClick
        return id
    }
}

/**
 * The builder receiver for a chunk of display UI. Everything you call here adds
 * a child to the current container, in order, top to bottom.
 *
 * Yes, it looks like Jetpack Compose and SwiftUI had a baby. That's deliberate.
 * Familiarity is a feature; nobody wants to learn a fourth way to centre things.
 */
public class DisplayScope internal constructor(
    private val builder: DisplayContentBuilder,
) {
    internal val nodes: MutableList<DisplayNode> = mutableListOf()

    /** Add a line of styled text. */
    public fun text(
        value: String,
        style: TextStyle = TextStyle.BODY,
        color: TextColor = TextColor.PRIMARY,
    ) {
        nodes += DisplayNode.Text(value, style, color)
    }

    /** Add an image from an `https` URL. Keep it small; see [DisplayNode.Image]. */
    public fun image(
        uri: String,
        sizePreset: ImageSize = ImageSize.FILL,
        cornerRadius: CornerRadius = CornerRadius.NONE,
    ) {
        nodes += DisplayNode.Image(uri, sizePreset, cornerRadius)
    }

    /** Add a single system icon. */
    public fun icon(name: IconName, style: IconStyle = IconStyle.FILLED) {
        nodes += DisplayNode.Icon(name, style)
    }

    /** Add a short MP4. Re-read [DisplayNode.Video] before you get ambitious. */
    public fun video(uri: String) {
        nodes += DisplayNode.Video(uri)
    }

    /**
     * Add a button. Pass an [onClick] and it'll be wired up and called when the
     * user taps. Omit it and you've made a label that looks disappointingly
     * clickable.
     */
    public fun button(
        label: String,
        style: ButtonStyle = ButtonStyle.PRIMARY,
        iconName: IconName? = null,
        onClick: (() -> Unit)? = null,
    ) {
        nodes += DisplayNode.Button(label, style, iconName, builder.register(onClick))
    }

    /**
     * Add a flex container and describe its children in the trailing lambda.
     * Pass [onClick] to make the whole box tappable — handy for list rows, where
     * a row-sized hit target beats a tiny button every time.
     */
    public fun flexBox(
        direction: Direction = Direction.COLUMN,
        gap: Int = 0,
        alignment: Alignment = Alignment.START,
        crossAlignment: Alignment = Alignment.START,
        wrap: Boolean = false,
        paddingTop: Int = 0,
        paddingBottom: Int = 0,
        paddingStart: Int = 0,
        paddingEnd: Int = 0,
        paddingAll: Int? = null,
        flexGrow: Float = 0f,
        flexShrink: Float = 1f,
        onClick: (() -> Unit)? = null,
        content: DisplayScope.() -> Unit,
    ) {
        val child = DisplayScope(builder).apply(content)
        nodes += DisplayNode.FlexBox(
            direction = direction,
            gap = gap,
            alignment = alignment,
            crossAlignment = crossAlignment,
            wrap = wrap,
            paddingTop = paddingAll ?: paddingTop,
            paddingBottom = paddingAll ?: paddingBottom,
            paddingStart = paddingAll ?: paddingStart,
            paddingEnd = paddingAll ?: paddingEnd,
            flexGrow = flexGrow,
            flexShrink = flexShrink,
            children = child.nodes.toList(),
            handlerId = builder.register(onClick),
        )
    }
}

/**
 * Build a [DisplayContent] from a DSL block.
 *
 * If your block produces exactly one [DisplayNode.FlexBox] it becomes the root
 * as-is. Anything else gets wrapped in a column for you, because a display
 * needs a single root and arguing about it helps no one. Make that root your L0
 * view: the back gesture from the root ends the whole session, so always leave
 * the user a way home.
 */
public fun displayContent(block: DisplayScope.() -> Unit): DisplayContent {
    val builder = DisplayContentBuilder()
    val scope = DisplayScope(builder).apply(block)
    val root = scope.nodes.singleOrNull() as? DisplayNode.FlexBox
        ?: DisplayNode.FlexBox(direction = Direction.COLUMN, children = scope.nodes.toList())
    return DisplayContent(root, builder.handlers)
}
