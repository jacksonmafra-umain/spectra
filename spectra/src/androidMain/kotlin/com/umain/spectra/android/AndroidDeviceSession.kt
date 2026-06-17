package com.umain.spectra.android

import com.umain.spectra.camera.CameraStream
import com.umain.spectra.camera.StreamConfiguration
import com.umain.spectra.core.DeviceId
import com.umain.spectra.core.DeviceSession
import com.umain.spectra.core.SessionState
import com.umain.spectra.core.SpectraError
import com.umain.spectra.core.SpectraResult
import com.umain.spectra.core.spectraFailure
import com.umain.spectra.display.Display
import com.umain.spectra.display.DisplayConfiguration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.meta.wearable.dat.core.session.DeviceSession as MetaDeviceSession
import com.meta.wearable.dat.camera.types.StreamConfiguration as MetaStreamConfiguration
import com.meta.wearable.dat.display.types.DisplayConfiguration as MetaDisplayConfiguration

/**
 * Wraps Meta's [MetaDeviceSession]. Adds nothing clever — it maps states,
 * forwards calls, and turns the SDK's two-armed `fold` failures into Spectra
 * results so the rest of your code never has to learn Meta's error shapes.
 */
internal class AndroidDeviceSession(
    private val meta: MetaDeviceSession,
) : DeviceSession {

    override val deviceId: DeviceId = DeviceId(meta.deviceId.toString())

    override val state: Flow<SessionState> = meta.state.map(Mappers::sessionState)

    override suspend fun start(): SpectraResult<Unit> = runCatchingSpectra { meta.start() }

    override suspend fun stop(): SpectraResult<Unit> = runCatchingSpectra { meta.stop() }

    override suspend fun openCameraStream(
        configuration: StreamConfiguration,
    ): SpectraResult<CameraStream> {
        val metaConfig: MetaStreamConfiguration = Mappers.streamConfiguration(configuration)
        return meta.addStream(metaConfig).fold(
            onSuccess = { stream -> Result.success(AndroidCameraStream(stream)) },
            onFailure = { error, _ ->
                spectraFailure(SpectraError.Backend(error.description, null))
            },
        )
    }

    override suspend fun attachDisplay(
        configuration: DisplayConfiguration,
    ): SpectraResult<Display> {
        return meta.addDisplay(MetaDisplayConfiguration()).fold(
            onSuccess = { metaDisplay -> Result.success(AndroidDisplay(metaDisplay)) },
            onFailure = { error, _ ->
                spectraFailure(SpectraError.Backend(error.description, null))
            },
        )
    }

    override suspend fun removeDisplay() {
        meta.removeDisplay()
    }
}
