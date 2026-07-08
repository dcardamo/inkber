package com.dan.inkber

import android.content.Context
import android.location.Location
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient

/**
 * Gates browser-permission requests coming from Uber's web pages.
 *
 * Geolocation: granted only for Uber origins, and only if the user has opted in
 * to sharing location at the app level (Settings + system permission). This is
 * the bridge between [LocationProvider]'s system permission and the web page's
 * geolocation API - the page asks via the standard browser geo prompt, and we
 * answer yes or no here rather than silently leaking.
 */
class UberWebChromeClient(
    private val context: Context,
    private val locationEnabled: () -> Boolean
) : WebChromeClient() {

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        val allowed = locationEnabled() &&
            EinkInjector.isInternal(origin) &&
            LocationProvider(context).hasPermission()
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
        return locationEnabled() &&
            EinkInjector.isInternal(origin) &&
            systemPermissionGranted
    }
}