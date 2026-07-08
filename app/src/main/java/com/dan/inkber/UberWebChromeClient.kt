package com.dan.inkber

import android.content.Context
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient

/**
 * Gates browser-permission requests coming from Uber's web pages.
 *
 * Geolocation: granted for Uber origins if and only if the app holds the
 * ACCESS_FINE_LOCATION system permission. There is no separate "share location
 * with Uber" toggle — the system permission IS the toggle. If the user granted
 * it, we share; if they denied it, we don't. This is simpler and matches user
 * expectations: allowing location access to the app means Uber gets it.
 */
class UberWebChromeClient(
    private val context: Context
) : WebChromeClient() {

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        val allowed = LocationProvider(context).hasPermission() &&
            EinkInjector.isInternal(origin)
        callback.invoke(origin, allowed, false)
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        // Geolocation is handled by onGeolocationPermissionsShowPrompt above.
        // For all other browser-permission requests (camera, mic, etc.) we deny
        // by default: Uber's mobile web doesn't need them for rides/eats, and
        // the Kompakt is a privacy-focused device.
        request.deny()
    }

    /** Helper for tests: decide whether a geo request would be granted. */
    fun wouldGrantGeolocation(origin: String, systemPermissionGranted: Boolean): Boolean {
        return systemPermissionGranted && EinkInjector.isInternal(origin)
    }
}