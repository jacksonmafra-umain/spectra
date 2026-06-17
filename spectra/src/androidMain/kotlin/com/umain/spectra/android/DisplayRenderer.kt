package com.umain.spectra.android

import com.umain.spectra.display.Alignment
import com.umain.spectra.display.ButtonStyle
import com.umain.spectra.display.CornerRadius
import com.umain.spectra.display.Direction
import com.umain.spectra.display.DisplayContent
import com.umain.spectra.display.DisplayNode
import com.umain.spectra.display.IconName
import com.umain.spectra.display.IconStyle
import com.umain.spectra.display.ImageSize
import com.umain.spectra.display.TextColor
import com.umain.spectra.display.TextStyle

// Meta's display DSL types (mwdat-display 0.7). The result-builder receiver is
// aliased to MetaScope. If Meta renames the scope class, this alias is the one
// line to update.
import com.meta.wearable.dat.display.views.ContentScope as MetaScope
import com.meta.wearable.dat.display.types.Direction as MetaDirection
import com.meta.wearable.dat.display.types.Alignment as MetaAlignment
import com.meta.wearable.dat.display.types.TextStyle as MetaTextStyle
import com.meta.wearable.dat.display.types.TextColor as MetaTextColor
import com.meta.wearable.dat.display.types.ImageSize as MetaImageSize
import com.meta.wearable.dat.display.types.CornerRadius as MetaCornerRadius
import com.meta.wearable.dat.display.types.ButtonStyle as MetaButtonStyle
import com.meta.wearable.dat.display.types.IconStyle as MetaIconStyle
import com.meta.wearable.dat.display.types.IconName as MetaIconName
import com.meta.wearable.dat.display.VideoPlayer as MetaVideoPlayer
import com.meta.wearable.dat.display.types.VideoSource as MetaVideoSource

/**
 * Walks a Spectra [DisplayContent] tree and replays it into Meta's display
 * result-builder. One recursive function does the whole job, because the tree is
 * uniform and writing a bespoke method per node type would be the same code six
 * times wearing different hats.
 *
 * Tap handlers are looked up by the [DisplayNode.handlerId] baked in during DSL
 * construction, then invoked through the closure Meta's `onClick` expects. Your
 * lambdas survive the round trip; the glasses just trigger them.
 */
internal object DisplayRenderer {

    fun render(scope: MetaScope, content: DisplayContent) {
        scope.node(content.root, content.handlers)
    }

    private fun MetaScope.node(node: DisplayNode, handlers: Map<String, () -> Unit>) {
        when (node) {
            is DisplayNode.FlexBox -> flexBox(
                direction = direction(node.direction),
                gap = node.gap,
                alignment = alignment(node.alignment),
                crossAlignment = alignment(node.crossAlignment),
                wrap = node.wrap,
                paddingTop = node.paddingTop,
                paddingBottom = node.paddingBottom,
                paddingStart = node.paddingStart,
                paddingEnd = node.paddingEnd,
                flexGrow = node.flexGrow,
                flexShrink = node.flexShrink,
                onClick = handler(node.handlerId, handlers),
            ) {
                node.children.forEach { child -> node(child, handlers) }
            }

            is DisplayNode.Text -> text(
                node.value,
                style = textStyle(node.style),
                color = textColor(node.color),
            )

            is DisplayNode.Image -> image(
                uri = node.uri,
                sizePreset = imageSize(node.sizePreset),
                cornerRadius = cornerRadius(node.cornerRadius),
            )

            is DisplayNode.Button -> button(
                label = node.label,
                style = buttonStyle(node.style),
                iconName = node.iconName?.let(::iconName),
                onClick = handler(node.handlerId, handlers) ?: {},
            )

            is DisplayNode.Icon -> icon(name = iconName(node.name), style = iconStyle(node.style))

            is DisplayNode.Video -> video(
                player = MetaVideoPlayer(source = MetaVideoSource.Url(node.uri)),
            )
        }
    }

    private fun handler(id: String?, handlers: Map<String, () -> Unit>): (() -> Unit)? =
        id?.let { handlers[it] }

    private fun direction(d: Direction) = when (d) {
        Direction.ROW -> MetaDirection.ROW
        Direction.COLUMN -> MetaDirection.COLUMN
        Direction.ROW_REVERSE -> MetaDirection.ROW_REVERSE
        Direction.COLUMN_REVERSE -> MetaDirection.COLUMN_REVERSE
    }

    private fun alignment(a: Alignment) = when (a) {
        Alignment.START -> MetaAlignment.START
        Alignment.CENTER -> MetaAlignment.CENTER
        Alignment.END -> MetaAlignment.END
        Alignment.STRETCH -> MetaAlignment.STRETCH
    }

    private fun textStyle(s: TextStyle) = when (s) {
        TextStyle.HEADING -> MetaTextStyle.HEADING
        TextStyle.BODY -> MetaTextStyle.BODY
        TextStyle.META -> MetaTextStyle.META
    }

    private fun textColor(c: TextColor) = when (c) {
        TextColor.PRIMARY -> MetaTextColor.PRIMARY
        TextColor.SECONDARY -> MetaTextColor.SECONDARY
    }

    private fun imageSize(s: ImageSize) = when (s) {
        ImageSize.ICON -> MetaImageSize.ICON
        ImageSize.FILL -> MetaImageSize.FILL
    }

    private fun cornerRadius(c: CornerRadius) = when (c) {
        CornerRadius.NONE -> MetaCornerRadius.NONE
        CornerRadius.SMALL -> MetaCornerRadius.SMALL
        CornerRadius.MEDIUM -> MetaCornerRadius.MEDIUM
    }

    private fun buttonStyle(b: ButtonStyle) = when (b) {
        ButtonStyle.PRIMARY -> MetaButtonStyle.PRIMARY
        ButtonStyle.SECONDARY -> MetaButtonStyle.SECONDARY
        ButtonStyle.OUTLINE -> MetaButtonStyle.OUTLINE
    }

    private fun iconStyle(s: IconStyle) = when (s) {
        IconStyle.FILLED -> MetaIconStyle.FILLED
        IconStyle.OUTLINE -> MetaIconStyle.OUTLINE
    }

    // The Spectra icon set is a deliberate subset, so this map is total on our
    // side and lossless on Meta's. Add a glyph to IconName and the compiler will
    // frogmarch you straight back here to map it. Good.
    private fun iconName(name: IconName) = when (name) {
        IconName.ARROW_LEFT -> MetaIconName.ARROW_LEFT
        IconName.ARROW_RIGHT -> MetaIconName.ARROW_RIGHT
        IconName.CHECKMARK_CIRCLE -> MetaIconName.CHECKMARK_CIRCLE
        IconName.BELL -> MetaIconName.BELL
        IconName.GEAR -> MetaIconName.GEAR
        IconName.HEART -> MetaIconName.HEART
        IconName.CAMERA -> MetaIconName.CAMERA
        IconName.PLAY -> MetaIconName.PLAY
        IconName.PAUSE -> MetaIconName.PAUSE
        IconName.CLOSE -> MetaIconName.CLOSE
    }
}
