package com.umain.spectra

import android.content.Context
import com.umain.spectra.android.AndroidSpectraClient
import com.umain.spectra.core.PermissionStatus

/**
 * The bits of the toolkit that genuinely need an `Activity` and the
 * `ActivityResult` API, hoisted into an interface your app implements.
 *
 * Why this exists: registration and camera permission both deeplink out to the
 * Meta AI app and come back via an `ActivityResult` contract, which Android
 * insists you register *before* the host is RESUMED. A library cannot do that
 * for you without owning your Activity, and a library that owns your Activity is
 * a library you will grow to resent. So you wire these three methods to a real
 * Activity (see the Android integration guide and the demo), and Spectra handles
 * literally everything else.
 */
public interface ActivityBridge {

    /** Launch the registration deeplink. Hooks up to `Wearables.startRegistration(activity)`. */
    public fun launchRegistration()

    /** Launch the unregistration deeplink. `Wearables.startUnregistration(activity)`. */
    public fun launchUnregistration()

    /**
     * Run the camera permission round-trip through your registered
     * `Wearables.RequestPermissionContract()` launcher and return the result.
     */
    public suspend fun requestCameraPermission(): PermissionStatus
}

/**
 * Create a real, hardware-backed [SpectraClient] on Android.
 *
 * @param context any [Context]; the application context is taken internally, so
 *   feel free to hand it an Activity without leaking it.
 * @param bridge your wiring for the Activity-bound flows. See [ActivityBridge]
 *   for the apology that explains why it's necessary.
 */
public fun Spectra.create(context: Context, bridge: ActivityBridge): SpectraClient =
    AndroidSpectraClient(context.applicationContext, bridge)
