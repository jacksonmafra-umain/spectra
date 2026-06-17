package com.umain.spectra

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.umain.spectra.camera.VideoFrame

/**
 * Visualises the live camera stream by sampling a low-resolution grid out of the
 * raw RGBA bytes of the most recent [VideoFrame] and painting it as coloured
 * cells. It's intentionally coarse — converting a full frame to a platform
 * bitmap on every tick is exactly the kind of main-thread sin this demo is
 * trying to teach you to avoid. The cells still move, which proves the bytes are
 * real and flowing.
 */
@Composable
fun CameraView(frame: VideoFrame?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color(0xFF101014), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (frame == null) {
            Text("No frames yet", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            return@Box
        }
        val cols = 16
        val rows = 12
        Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            val cellW = size.width / cols
            val cellH = size.height / rows
            for (gy in 0 until rows) {
                for (gx in 0 until cols) {
                    val px = (gx * frame.width / cols).coerceIn(0, frame.width - 1)
                    val py = (gy * frame.height / rows).coerceIn(0, frame.height - 1)
                    val i = (py * frame.width + px) * 4
                    val color = if (i + 2 < frame.bytes.size) {
                        Color(
                            red = frame.bytes[i].toInt().and(0xFF) / 255f,
                            green = frame.bytes[i + 1].toInt().and(0xFF) / 255f,
                            blue = frame.bytes[i + 2].toInt().and(0xFF) / 255f,
                            alpha = 1f,
                        )
                    } else {
                        Color.Black
                    }
                    drawRect(
                        color = color,
                        topLeft = Offset(gx * cellW, gy * cellH),
                        size = Size(cellW, cellH),
                    )
                }
            }
        }
    }
}
