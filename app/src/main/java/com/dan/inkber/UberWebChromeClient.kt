package com.dan.inkber

import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient

/**
 * Gates browser-permission requests coming from Uber's web pages and forwards
 * JavaScript console messages to logcat for debugging injection issues.
 */
class UberWebChromeClient(
    private val context: Context
) : WebChromeClient() {

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        val msg = consoleMessage?.message() ?: ""
        val line = consoleMessage?.lineNumber() ?: 0
        val source = consoleMessage?.sourceId() ?: ""
        when (consoleMessage?.messageLevel()) {
            ConsoleMessage.MessageLevel.ERROR -> Log.e(TAG, "JS [$source:$line] $msg")
            ConsoleMessage.MessageLevel.WARNING -> Log.w(TAG, "JS [$source:$line] $msg")
            else -> Log.d(TAG, "JS [$source:$line] $msg")
        }
        return true
    }

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

    companion object {
        private const val TAG = "InkberChrome"
    }
}
