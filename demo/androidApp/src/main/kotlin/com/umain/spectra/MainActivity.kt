package com.umain.spectra

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.umain.spectra.core.PermissionStatus
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.types.Permission as MetaPermission
import com.meta.wearable.dat.core.types.PermissionStatus as MetaPermissionStatus

/**
 * Android entry point. It implements [ActivityBridge] so Spectra can drive the
 * two Activity-bound flows — registration and the camera permission — that the
 * SDK can't do headless. Everything else lives in the shared `App()`.
 *
 * Run on real glasses: this builds the real client via `Spectra.create`. Swap to
 * `App()` (no argument) to fall back to the mock.
 */
class MainActivity : ComponentActivity(), ActivityBridge {

    // Must be registered before the Activity is RESUMED, hence a field.
    private var permissionContinuation: CancellableContinuation<PermissionStatus>? = null
    private val permissionLauncher =
        registerForActivityResult(Wearables.RequestPermissionContract()) { result ->
            val status = result.fold(
                onSuccess = { mapStatus(it) },
                onFailure = { _, _ -> PermissionStatus.UNAVAILABLE },
            )
            permissionContinuation?.resumeWith(Result.success(status))
            permissionContinuation = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val client = Spectra.create(applicationContext, this)
        setContent { App(client) }
    }

    // --- ActivityBridge -------------------------------------------------------

    override fun launchRegistration() {
        Wearables.startRegistration(this)
    }

    override fun launchUnregistration() {
        Wearables.startUnregistration(this)
    }

    override suspend fun requestCameraPermission(): PermissionStatus =
        suspendCancellableCoroutine { cont ->
            permissionContinuation = cont
            cont.invokeOnCancellation { permissionContinuation = null }
            permissionLauncher.launch(MetaPermission.CAMERA)
        }

    override fun openGlassesAppUpdate() {
        try { Wearables.openDATGlassesAppUpdate(this) } catch (_: Throwable) { }
    }

    private fun mapStatus(s: MetaPermissionStatus): PermissionStatus = when (s) {
        is MetaPermissionStatus.Granted -> PermissionStatus.GRANTED
        is MetaPermissionStatus.Denied -> PermissionStatus.DENIED
        else -> PermissionStatus.NOT_DETERMINED
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App() // mock client in previews
}
