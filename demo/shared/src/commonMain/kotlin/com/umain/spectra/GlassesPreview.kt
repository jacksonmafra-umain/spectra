package com.umain.spectra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment as ComposeAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.umain.spectra.display.Alignment
import com.umain.spectra.display.ButtonStyle
import com.umain.spectra.display.Direction
import com.umain.spectra.display.DisplayContent
import com.umain.spectra.display.DisplayNode
import com.umain.spectra.display.IconName
import com.umain.spectra.display.TextStyle

/**
 * Renders a Spectra [DisplayContent] tree as Compose, inside a round frame meant
 * to evoke the glasses display. It's a stand-in — the real thing is strapped to
 * someone's face — but tapping a button here fires the exact same handler the
 * real glasses would, which is the point.
 */
@Composable
fun GlassesPreview(content: DisplayContent?) {
    Box(
        modifier = Modifier
            .size(260.dp)
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
            .background(Color.Black, RoundedCornerShape(28.dp))
            .padding(12.dp),
        contentAlignment = ComposeAlignment.Center,
    ) {
        if (content == null) {
            Text("Display idle", color = Color.Gray)
        } else {
            NodeView(content.root, content.handlers)
        }
    }
}

@Composable
private fun NodeView(node: DisplayNode, handlers: Map<String, () -> Unit>) {
    when (node) {
        is DisplayNode.FlexBox -> {
            val padding = PaddingValues(
                start = node.paddingStart.dp,
                end = node.paddingEnd.dp,
                top = node.paddingTop.dp,
                bottom = node.paddingBottom.dp,
            )
            val clickMod = node.handlerId
                ?.let { id -> Modifier.clickable { handlers[id]?.invoke() } }
                ?: Modifier
            if (node.direction == Direction.ROW || node.direction == Direction.ROW_REVERSE) {
                Row(
                    modifier = clickMod.padding(padding),
                    horizontalArrangement = Arrangement.spacedBy(node.gap.dp),
                    verticalAlignment = crossVertical(node.crossAlignment),
                ) { node.children.forEach { NodeView(it, handlers) } }
            } else {
                Column(
                    modifier = clickMod.padding(padding),
                    verticalArrangement = Arrangement.spacedBy(node.gap.dp),
                    horizontalAlignment = crossHorizontal(node.crossAlignment),
                ) { node.children.forEach { NodeView(it, handlers) } }
            }
        }

        is DisplayNode.Text -> Text(
            text = node.value,
            color = Color.White,
            fontWeight = if (node.style == TextStyle.HEADING) FontWeight.Bold else FontWeight.Normal,
            style = when (node.style) {
                TextStyle.HEADING -> MaterialTheme.typography.titleMedium
                TextStyle.BODY -> MaterialTheme.typography.bodyMedium
                TextStyle.META -> MaterialTheme.typography.labelSmall
            },
        )

        is DisplayNode.Button -> {
            val onClick = { node.handlerId?.let { handlers[it]?.invoke() } ?: Unit }
            val label = node.iconName?.let { "${glyph(it)} ${node.label}" } ?: node.label
            if (node.style == ButtonStyle.OUTLINE) {
                OutlinedButton(onClick = onClick) { Text(label) }
            } else {
                Button(onClick = onClick) { Text(label) }
            }
        }

        is DisplayNode.Icon -> Text(glyph(node.name), color = Color.White)

        is DisplayNode.Image -> Box(
            modifier = Modifier.size(64.dp).background(Color.DarkGray, RoundedCornerShape(8.dp)),
            contentAlignment = ComposeAlignment.Center,
        ) { Text("IMG", color = Color.LightGray, style = MaterialTheme.typography.labelSmall) }

        is DisplayNode.Video -> Box(
            modifier = Modifier.size(96.dp).background(Color.DarkGray, RoundedCornerShape(8.dp)),
            contentAlignment = ComposeAlignment.Center,
        ) { Text("VIDEO", color = Color.LightGray, style = MaterialTheme.typography.labelSmall) }
    }
}

private fun crossVertical(a: Alignment) = when (a) {
    Alignment.START -> ComposeAlignment.Top
    Alignment.CENTER -> ComposeAlignment.CenterVertically
    Alignment.END -> ComposeAlignment.Bottom
    Alignment.STRETCH -> ComposeAlignment.CenterVertically
}

private fun crossHorizontal(a: Alignment) = when (a) {
    Alignment.START -> ComposeAlignment.Start
    Alignment.CENTER -> ComposeAlignment.CenterHorizontally
    Alignment.END -> ComposeAlignment.End
    Alignment.STRETCH -> ComposeAlignment.CenterHorizontally
}

private fun glyph(name: IconName) = when (name) {
    IconName.ARROW_LEFT -> "<-"
    IconName.ARROW_RIGHT -> "->"
    IconName.CHECKMARK_CIRCLE -> "(v)"
    IconName.BELL -> "(!)"
    IconName.GEAR -> "{*}"
    IconName.HEART -> "<3"
    IconName.CAMERA -> "[o]"
    IconName.PLAY -> ">"
    IconName.PAUSE -> "||"
    IconName.CLOSE -> "x"
}
