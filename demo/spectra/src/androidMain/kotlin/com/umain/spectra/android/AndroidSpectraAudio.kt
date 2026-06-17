package com.umain.spectra.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.speech.tts.TextToSpeech
import com.umain.spectra.core.AudioProfile
import com.umain.spectra.core.AudioState
import com.umain.spectra.core.SpectraAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sqrt

/**
 * Android audio over plain Bluetooth — no DAT involved. A2DP playback rides
 * [TextToSpeech] (the system routes it to connected glasses automatically); HFP
 * capture flips [AudioManager] into communication mode on the Bluetooth SCO route
 * and reads PCM off an [AudioRecord] for a live level.
 *
 * The mic path needs the `RECORD_AUDIO` runtime permission granted first; without
 * it, [startMicCapture] is a no-op (request it from your Activity).
 */
internal class AndroidSpectraAudio(private val context: Context) : SpectraAudio {

    private val _state = MutableStateFlow(AudioState())
    override val state: Flow<AudioState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var tts: TextToSpeech? = null
    private var micJob: Job? = null

    override suspend fun playToGlasses(text: String) {
        ensureTts()
        _state.value = AudioState(
            profile = AudioProfile.A2DP_PLAYBACK, isPlaying = true, routedToGlasses = isBluetoothOutput(),
        )
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "spectra")
        // TextToSpeech has no suspend completion here; the UI clears playback on stop.
    }

    override suspend fun stopPlayback() {
        tts?.stop()
        _state.value = AudioState()
    }

    override suspend fun startMicCapture() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return // needs RECORD_AUDIO — request it from the Activity first.
        }
        micJob?.cancel()
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        routeToBluetoothSco()
        micJob = scope.launch {
            val sampleRate = 8000 // HFP is 8 kHz mono
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(2048)
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf,
            )
            val buffer = ShortArray(1024)
            try {
                record.startRecording()
                while (isActive) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n > 0) {
                        var sum = 0.0
                        for (i in 0 until n) {
                            val s = buffer[i].toDouble(); sum += s * s
                        }
                        val level = (sqrt(sum / n) / 3000.0).coerceIn(0.0, 1.0).toFloat()
                        _state.value = AudioState(
                            profile = AudioProfile.HFP_MIC, micLevel = level, routedToGlasses = true,
                        )
                    }
                }
            } catch (_: Throwable) {
                // mic unavailable — fall through to cleanup
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }
    }

    override suspend fun stopMicCapture() {
        micJob?.cancel(); micJob = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION") audioManager.stopBluetoothSco()
        }
        audioManager.mode = AudioManager.MODE_NORMAL
        _state.value = AudioState()
    }

    private fun routeToBluetoothSco() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                ?.let { audioManager.setCommunicationDevice(it) }
        } else {
            @Suppress("DEPRECATION") audioManager.startBluetoothSco()
        }
    }

    private fun ensureTts() {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
            }
        }
    }

    private fun isBluetoothOutput(): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
}
