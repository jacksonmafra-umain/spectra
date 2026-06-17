package com.umain.spectra

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Style guide, lifted straight from the official CameraAccess sample -------
private val AppPrimary = Color(0xFF0064E0)     // appPrimaryColor
private val DestructiveBg = Color(0xFFFFD8DB)  // destructiveBackground
private val DestructiveFg = Color(0xFFAA071E)  // destructiveForeground
private val ScreenBlack = Color(0xFF000000)

/**
 * Spectra Playground, restyled to match Meta's CameraAccess sample.
 *
 * Three states, exactly like the real thing: a white welcome screen until your
 * glasses are connected, a black "stream your camera" screen once they are, and
 * a full-bleed video view while streaming. A floating ladybug opens a debug
 * sheet with the [com.umain.spectra.core.MockDeviceKit] — so you can run the
 * whole pipeline on a simulator with no glasses in sight.
 *
 * @param realClient the platform's hardware-backed client, or null if this
 *   platform can't reach the glasses yet. Android passes one in; iOS passes one
 *   once the Swift bridge is wired.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(realClient: SpectraClient? = null) {
    val scope = rememberCoroutineScope()
    val mock = remember { Spectra.mock() }

    // Default to the mock so nobody pokes Bluetooth on first launch.
    var useMock by remember { mutableStateOf(realClient == null) }
    val client = if (!useMock && realClient != null) realClient else mock
    val state = remember(client) { PlaygroundState(scope, client) }

    var showDebug by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    MaterialTheme(colorScheme = darkColorScheme(primary = AppPrimary)) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !state.isRegistered -> HomeScreen(
                    connecting = state.isConnecting,
                    onConnect = state::connect,
                )

                state.isStreaming -> StreamScreen(
                    state = state,
                    onStop = state::stopStreaming,
                    onCapture = state::capturePhoto,
                )

                else -> NonStreamScreen(
                    state = state,
                    onStartStreaming = state::startStreaming,
                    onDisconnect = state::disconnect,
                )
            }

            // Floating debug (ladybug) button — bottom-trailing, over everything.
            DebugButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                onClick = { showDebug = true },
            )

            // Captured-photo confirmation, dismissable.
            state.lastPhoto?.let { photo ->
                PhotoToast(
                    text = "Photo captured · ${photo.width}×${photo.height}",
                    onDismiss = state::dismissPhoto,
                    modifier = Modifier.align(Alignment.TopCenter).safeContentPadding().padding(16.dp),
                )
            }

            if (showDebug) {
                ModalBottomSheet(
                    onDismissRequest = { showDebug = false },
                    sheetState = sheetState,
                ) {
                    DebugSheet(
                        state = state,
                        useMock = useMock,
                        realAvailable = realClient != null,
                        onPickMock = { useMock = true },
                        onPickReal = { useMock = false },
                    )
                }
            }
        }
    }
}

// --- Screen 1: not registered -------------------------------------------------

@Composable
private fun HomeScreen(connecting: Boolean, onConnect: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(
            modifier = Modifier.fillMaxSize().safeContentPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            GlassesGlyph(Modifier.size(width = 132.dp, height = 56.dp), tint = AppPrimary)
            Spacer(Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TipItem("Video Capture", "Stream and record video straight from your glasses, from your point of view.")
                TipItem("Open-Ear Audio", "Hear notifications while keeping your ears open to the world around you.")
                TipItem("Enjoy On-the-Go", "Stay hands-free as you move through your day. Move freely, stay connected.")
            }

            Spacer(Modifier.weight(1f))

            Text(
                "You'll be redirected to the Meta AI app to confirm your connection.",
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                title = if (connecting) "Connecting…" else "Connect my glasses",
                enabled = !connecting,
                onClick = onConnect,
            )
        }
    }
}

@Composable
private fun TipItem(title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(36.dp).background(AppPrimary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(18.dp)) { drawGlasses(AppPrimary) }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = Color.Gray, fontSize = 15.sp)
        }
    }
}

// --- Screen 2: registered, not streaming --------------------------------------

@Composable
private fun NonStreamScreen(
    state: PlaygroundState,
    onStartStreaming: () -> Unit,
    onDisconnect: () -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(ScreenBlack)) {
        Column(
            modifier = Modifier.fillMaxSize().safeContentPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar: settings gear that reveals Disconnect.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier.size(36.dp).clickable { showSettings = !showSettings },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(24.dp)) { drawGear(Color.White) }
                }
            }
            if (showSettings) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(Modifier.width(140.dp)) {
                        DestructiveButton("Disconnect") {
                            showSettings = false
                            onDisconnect()
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            GlassesGlyph(Modifier.size(width = 132.dp, height = 56.dp), tint = Color.White)
            Spacer(Modifier.height(16.dp))
            Text(
                "Stream Your Glasses Camera",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Tap Start streaming to see live video from your glasses, or use the camera button to take a photo.",
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            Spacer(Modifier.weight(1f))

            if (!state.hasActiveDevice) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(14.dp)) { drawHourglass(Color.White.copy(alpha = 0.7f)) }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Waiting for an active device",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            PrimaryButton(
                title = "Start streaming",
                enabled = state.hasActiveDevice,
                onClick = onStartStreaming,
            )

            if (state.audioSupported) {
                Spacer(Modifier.height(12.dp))
                AudioPanel(state)
            }
        }
    }
}

@Composable
private fun AudioPanel(state: PlaygroundState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(20.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Audio (Bluetooth)", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                AudioButton(
                    title = if (state.audioState.isPlaying) "Playing…" else "Play to glasses",
                    active = state.audioState.isPlaying,
                    onClick = state::playToGlasses,
                )
            }
            Box(Modifier.weight(1f)) {
                AudioButton(
                    title = if (state.isMicCapturing) "Stop mic" else "Capture mic",
                    active = state.isMicCapturing,
                    onClick = state::toggleMic,
                )
            }
        }
        // Live mic level meter (HFP).
        if (state.isMicCapturing) {
            Box(
                modifier = Modifier.fillMaxWidth().height(6.dp)
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.audioState.micLevel.coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(AppPrimary, RoundedCornerShape(3.dp)),
                )
            }
        }
        Text(
            "A2DP = hi-fi output · HFP = mic (drops to 8 kHz mono). Mutually exclusive.",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun AudioButton(title: String, active: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) AppPrimary else Color.White.copy(alpha = 0.14f),
            contentColor = Color.White,
        ),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// --- Screen 3: streaming ------------------------------------------------------

@Composable
private fun StreamScreen(
    state: PlaygroundState,
    onStop: () -> Unit,
    onCapture: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().background(ScreenBlack)) {
        StreamBackdrop(state, Modifier.fillMaxSize())

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeContentPadding()
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { DestructiveButton("Stop streaming", onStop) }
            CaptureButton(onCapture)
        }
    }
}

@Composable
private fun StreamBackdrop(state: PlaygroundState, modifier: Modifier) {
    val frame = state.lastFrame
    if (frame == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Waiting for the first frame…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        return
    }
    // Decode the raw RGBA frame into a real bitmap and draw it full-bleed.
    val bitmap = remember(frame) { rgbaToImageBitmap(frame.bytes, frame.width, frame.height) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Live camera",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Decoding frame…", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
    }
}

// --- Debug bottom sheet (the ladybug opens this) ------------------------------

@Composable
private fun DebugSheet(
    state: PlaygroundState,
    useMock: Boolean,
    realAvailable: Boolean,
    onPickMock: () -> Unit,
    onPickReal: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mock Device Kit", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            "Simulate a Ray-Ban Meta to test streaming, capture and the rest — no real glasses required.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Backend selector (demo-only): mock client vs real glasses backend.
        Text("Backend", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = useMock, onClick = onPickMock, label = { Text("Mock") })
            FilterChip(
                selected = !useMock,
                enabled = realAvailable,
                onClick = onPickReal,
                label = { Text("Glasses (real)") },
            )
        }

        Spacer(Modifier.height(4.dp))

        if (!state.mockKitSupported) {
            Text(
                "This backend can't fake a device — switch to Mock, or connect real glasses.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (state.mockKit.enabled) {
            Text(
                "Enabled · ${state.mockKit.pairedCount} device(s) paired",
                fontSize = 13.sp,
                color = Color(0xFF2E7D32),
                fontWeight = FontWeight.SemiBold,
            )
            PrimaryButton("Pair Ray-Ban Meta", enabled = state.mockKit.pairedCount < 3, onClick = state::pairMockGlasses)
            DestructiveButton("Disable MockDeviceKit", onClick = state::disableMockKit)
        } else {
            PrimaryButton("Enable MockDeviceKit", onClick = state::enableMockKit)
        }

        Spacer(Modifier.height(4.dp))
        Text("Status: ${state.log}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- Reusable controls (the sample's CustomButton / CircleButton / DebugMenu) --

@Composable
private fun PrimaryButton(title: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppPrimary, contentColor = Color.White),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DestructiveButton(title: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(30.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DestructiveBg, contentColor = DestructiveFg),
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CaptureButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(56.dp).background(Color.White, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(22.dp)) { drawCamera(Color.Black) }
    }
}

@Composable
private fun DebugButton(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.size(52.dp).background(AppPrimary, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) { drawLadybug() }
    }
}

@Composable
private fun PhotoToast(text: String, onDismiss: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF1C1C1E), RoundedCornerShape(16.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("$text  ·  tap to dismiss", color = Color.White, fontSize = 13.sp)
    }
}

// --- Hand-drawn glyphs (no icon dependency) -----------------------------------

@Composable
private fun GlassesGlyph(modifier: Modifier, tint: Color) {
    Canvas(modifier) { drawGlasses(tint) }
}

private fun DrawScope.drawGlasses(color: Color) {
    val w = size.width
    val h = size.height
    val lens = Size(w * 0.34f, h * 0.7f)
    val r = CornerRadius(h * 0.25f, h * 0.25f)
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.1f)
    drawRoundRect(color, topLeft = Offset(0f, h * 0.15f), size = lens, cornerRadius = r, style = stroke)
    drawRoundRect(color, topLeft = Offset(w - lens.width, h * 0.15f), size = lens, cornerRadius = r, style = stroke)
    // Bridge between the lenses.
    drawLine(color, Offset(lens.width, h * 0.4f), Offset(w - lens.width, h * 0.4f), strokeWidth = h * 0.1f)
}

private fun DrawScope.drawGear(color: Color) {
    val c = Offset(size.width / 2, size.height / 2)
    val outer = size.minDimension * 0.5f
    val toothW = size.minDimension * 0.16f
    val toothH = size.minDimension * 0.30f
    val ring = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.16f)
    for (k in 0 until 8) {
        rotate(degrees = k * 45f, pivot = c) {
            drawRoundRect(
                color = color,
                topLeft = Offset(c.x - toothW / 2, c.y - outer),
                size = Size(toothW, toothH),
                cornerRadius = CornerRadius(toothW / 3, toothW / 3),
            )
        }
    }
    // A thick ring (rather than a punched hole) reads as a gear at icon size.
    drawCircle(color = color, radius = outer * 0.5f, center = c, style = ring)
}

private fun DrawScope.drawHourglass(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = h * 0.08f)
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.1f, 0f); lineTo(w * 0.9f, 0f)
        lineTo(w * 0.5f, h * 0.5f); close()
        moveTo(w * 0.1f, h); lineTo(w * 0.9f, h)
        lineTo(w * 0.5f, h * 0.5f); close()
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawCamera(color: Color) {
    val w = size.width
    val h = size.height
    // Top viewfinder bump.
    drawRoundRect(
        color,
        topLeft = Offset(w * 0.3f, h * 0.08f),
        size = Size(w * 0.28f, h * 0.18f),
        cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
    )
    // Body.
    drawRoundRect(
        color,
        topLeft = Offset(0f, h * 0.22f),
        size = Size(w, h * 0.6f),
        cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
    )
    // Lens (punched lighter).
    drawCircle(Color.White, radius = h * 0.2f, center = Offset(w / 2, h * 0.52f))
    drawCircle(color, radius = h * 0.1f, center = Offset(w / 2, h * 0.52f))
}

private fun DrawScope.drawLadybug() {
    val w = size.width
    val h = size.height
    val c = Offset(w / 2, h * 0.55f)
    val r = w * 0.42f
    // Body.
    drawCircle(Color(0xFFE53935), radius = r, center = c)
    // Head.
    drawCircle(Color.Black, radius = r * 0.34f, center = Offset(c.x, c.y - r * 0.95f))
    // Centre seam.
    drawLine(Color.Black, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = w * 0.05f)
    // Spots.
    val spot = r * 0.22f
    drawCircle(Color.Black, radius = spot, center = Offset(c.x - r * 0.45f, c.y - r * 0.1f))
    drawCircle(Color.Black, radius = spot, center = Offset(c.x + r * 0.45f, c.y - r * 0.1f))
    drawCircle(Color.Black, radius = spot, center = Offset(c.x - r * 0.35f, c.y + r * 0.5f))
    drawCircle(Color.Black, radius = spot, center = Offset(c.x + r * 0.35f, c.y + r * 0.5f))
}
