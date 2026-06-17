package com.umain.spectra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Spectra Playground — the whole demo in one screen.
 *
 * It walks the canonical integration sequence top to bottom: initialize,
 * register, permit, session, stream, capture, display. Every button maps to one
 * Spectra call, so the UI doubles as a clickable version of the README.
 *
 * The "Backend" toggle picks the client at runtime: [Spectra.mock] (synthetic
 * frames, no hardware) or the real glasses backend passed in by the platform
 * entry point. On iOS the real option is disabled until the Swift bridge exists,
 * so [realClient] is null there and the mock is used.
 *
 * @param realClient the platform's hardware-backed client, or null if this
 *   platform can't reach the glasses yet. Android passes one in from
 *   `MainActivity`; iOS passes null for now.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(realClient: SpectraClient? = null) {
    val scope = rememberCoroutineScope()
    val mock = remember { Spectra.mock() }

    // Default to the mock so nobody accidentally pokes Bluetooth on first launch.
    var useMock by remember { mutableStateOf(true) }

    // Switching backend swaps the client; remember(client) rebuilds the state
    // holder so the new client starts from a clean slate.
    val client = if (!useMock && realClient != null) realClient else mock
    val state = remember(client) { PlaygroundState(scope, client) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.fillMaxSize().safeContentPadding().padding(16.dp)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Spectra Playground",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Meta's wearable SDK, wrapped in Kotlin Multiplatform. Pick a backend, then walk the buttons.",
                    style = MaterialTheme.typography.bodySmall,
                )

                // --- Backend selector (Mock vs real glasses) ---------------------
                Text("Backend", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = useMock,
                        onClick = { useMock = true },
                        label = { Text("Mock") },
                    )
                    FilterChip(
                        selected = !useMock,
                        enabled = realClient != null,
                        onClick = { useMock = false },
                        label = { Text("Glasses (real)") },
                    )
                }
                if (realClient == null) {
                    Text(
                        "Real backend isn't wired on this platform yet — running on the mock.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                } else {
                    Text(
                        if (useMock) "Synthetic frames. No hardware touched."
                        else "Live: talks to the Meta AI app and your glasses.",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }

                LogCard(state.log)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action("Initialize", state::initialize)
                    Action("Register", state::register)
                    Action("Grant camera", state::requestCameraPermission)
                    Action("Start session", state::startSession)
                    Action("Start stream", state::startStream)
                    Action("Capture photo", state::capturePhoto)
                    Action("Display demo", state::showDisplayDemo)
                    Action("Reset", state::reset)
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusRow("Registration", state.registration::class.simpleName ?: "?")
                        StatusRow("Camera permission", state.cameraPermission.name)
                        StatusRow("Devices", if (state.devices.isEmpty()) "none" else state.devices.joinToString { it.name })
                        StatusRow("Session", state.sessionState?.name ?: "—")
                        StatusRow("Stream", state.streamState?.name ?: "—")
                        StatusRow("Frames received", state.frameCount.toString())
                        StatusRow("Last photo", state.lastPhoto?.let { "${it.width}x${it.height} ${it.format}" } ?: "—")
                        StatusRow("Display", state.displayState?.name ?: "—")
                    }
                }

                Text("Camera stream", style = MaterialTheme.typography.titleSmall)
                CameraView(state.lastFrame)

                Text("Glasses display", style = MaterialTheme.typography.titleSmall)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GlassesPreview(state.displayContent)
                }
            }
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.padding(vertical = 4.dp)) { Text(label) }
}

@Composable
private fun LogCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}
