package com.umain.spectra.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Spectra Playground — the whole demo in one screen.
 *
 * It walks the canonical integration sequence top to bottom: initialize,
 * register, permit, session, stream, capture, display. Every button maps to one
 * Spectra call, so the UI doubles as a clickable version of the README. Runs on
 * the [com.umain.spectra.Spectra.mock] client, so it works everywhere with
 * nothing plugged in.
 *
 * @param scope a coroutine scope tied to the host. Defaults to a Main-dispatched
 *   one for previews; real entry points hand in their own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun App(scope: CoroutineScope = CoroutineScope(Dispatchers.Main)) {
    val state = remember { PlaygroundState(scope) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                    "Meta's wearable SDK, wrapped in Kotlin Multiplatform and faked end to end. No glasses were harmed.",
                    style = MaterialTheme.typography.bodySmall,
                )

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
