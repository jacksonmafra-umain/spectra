package com.umain.spectra.android

import com.umain.spectra.camera.StreamConfiguration
import com.umain.spectra.camera.StreamState
import com.umain.spectra.camera.VideoQuality
import com.umain.spectra.core.DeviceId
import com.umain.spectra.core.DeviceSelector
import com.umain.spectra.core.Permission
import com.umain.spectra.core.PermissionStatus
import com.umain.spectra.core.SessionState
import com.umain.spectra.core.WearableDevice

// Aliases for Meta's SDK types so they don't collide with ours. Same words,
// different packages — a recurring theme when you wrap someone else's SDK.
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector as MetaAutoSelector
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector as MetaSpecificSelector
import com.meta.wearable.dat.core.types.Permission as MetaPermission
import com.meta.wearable.dat.core.types.PermissionStatus as MetaPermissionStatus
import com.meta.wearable.dat.core.types.SessionState as MetaSessionState
import com.meta.wearable.dat.camera.types.StreamConfiguration as MetaStreamConfiguration
import com.meta.wearable.dat.camera.types.StreamState as MetaStreamState
import com.meta.wearable.dat.camera.types.VideoQuality as MetaVideoQuality

/**
 * The boring-but-load-bearing layer: translating between Spectra's vocabulary
 * and Meta's. It's all one-liners. It's also exactly the layer that, if you
 * skip it and "just use the SDK enums everywhere", metastasises through your
 * codebase until you can never swap the backend again. So we pay it once, here.
 *
 * Targets the Meta Wearables Device Access Toolkit Android SDK, version 0.7.
 * If Meta renames a constant in a later release, this is the only file that
 * needs to care.
 */
internal object Mappers {

    fun permission(permission: Permission): MetaPermission = when (permission) {
        Permission.CAMERA -> MetaPermission.CAMERA
        Permission.MICROPHONE -> MetaPermission.MICROPHONE
    }

    fun permissionStatus(status: MetaPermissionStatus): PermissionStatus = when (status) {
        MetaPermissionStatus.Granted -> PermissionStatus.GRANTED
        MetaPermissionStatus.Denied -> PermissionStatus.DENIED
        MetaPermissionStatus.NotDetermined -> PermissionStatus.NOT_DETERMINED
        else -> PermissionStatus.UNAVAILABLE
    }

    fun sessionState(state: MetaSessionState): SessionState = when (state) {
        MetaSessionState.RUNNING -> SessionState.RUNNING
        MetaSessionState.PAUSED -> SessionState.PAUSED
        MetaSessionState.STOPPED -> SessionState.STOPPED
        else -> SessionState.STOPPED
    }

    fun streamState(state: MetaStreamState): StreamState = when (state) {
        MetaStreamState.STARTING -> StreamState.STARTING
        MetaStreamState.STARTED -> StreamState.STARTED
        MetaStreamState.STREAMING -> StreamState.STREAMING
        MetaStreamState.STOPPING -> StreamState.STOPPING
        MetaStreamState.STOPPED -> StreamState.STOPPED
        MetaStreamState.CLOSED -> StreamState.CLOSED
        else -> StreamState.STOPPED
    }

    fun videoQuality(quality: VideoQuality): MetaVideoQuality = when (quality) {
        VideoQuality.LOW -> MetaVideoQuality.LOW
        VideoQuality.MEDIUM -> MetaVideoQuality.MEDIUM
        VideoQuality.HIGH -> MetaVideoQuality.HIGH
    }

    fun streamConfiguration(config: StreamConfiguration): MetaStreamConfiguration =
        MetaStreamConfiguration(
            videoQuality = videoQuality(config.quality),
            frameRate = config.frameRate.fps,
        )

    fun selector(selector: DeviceSelector): Any = when (selector) {
        is DeviceSelector.Auto -> MetaAutoSelector()
        is DeviceSelector.Specific -> MetaSpecificSelector(selector.deviceId.value)
    }

    fun deviceId(raw: String): DeviceId = DeviceId(raw)

    fun device(id: String, name: String, model: String, available: Boolean): WearableDevice =
        WearableDevice(DeviceId(id), name, model, available)
}
