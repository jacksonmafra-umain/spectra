package com.umain.spectra

import android.content.Context
import com.umain.spectra.android.AndroidSpectraClient
import com.umain.spectra.core.PermissionStatus

/**
 * The Activity-bound flows the SDK can't do headless: registration and the
 * camera permission both deeplink to the Meta AI app and return via an
 * ActivityResult contract that must be registered before the host is RESUMED.
 * Your Activity implements these three; Spectra does the rest.
 */
public interface ActivityBridge {
    public fun launchRegistration()
    public fun launchUnregistration()
    public suspend fun requestCameraPermission(): PermissionStatus
}

/**
 * Real, hardware-backed [SpectraClient] on Android, delegating to the Meta
 * Wearables Device Access Toolkit (mwdat 0.7). Needs a GitHub Packages token to
 * resolve the SDK and the credentials in AndroidManifest.xml to connect.
 */
public fun Spectra.create(context: Context, bridge: ActivityBridge): SpectraClient =
    AndroidSpectraClient(context.applicationContext, bridge)
