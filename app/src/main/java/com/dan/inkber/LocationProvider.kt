package com.dan.inkber

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Provides the device's last-known location using the platform LocationManager
 * only - no Play Services dependency, which keeps the APK small and the app
 * functional on AOSP-based devices like the Mudita Kompakt that ship without
 * Google Play Services.
 */
class LocationProvider(private val context: Context) {

    /** True if the app has been granted ACCESS_FINE_LOCATION. */
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Returns the most recent fix from any available provider, or null if no
     * provider has a recent fix or permission is missing. Does not trigger a
     * new fix - this is a passive read so it is cheap and side-effect-free.
     */
    fun lastKnownLocation(): Location? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = try {
            lm.getProviders(true)
        } catch (e: SecurityException) {
            return null
        }
        var best: Location? = null
        for (p in providers) {
            val loc = try {
                lm.getLastKnownLocation(p)
            } catch (e: SecurityException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
            if (loc != null && (best == null || loc.accuracy < best.accuracy)) {
                best = loc
            }
        }
        return best
    }

    /** A serialisable snapshot of a fix for logging/tests. */
    data class Fix(val lat: Double, val lon: Double, val accuracyM: Float, val tsMs: Long) {
        companion object {
            fun from(l: Location) = Fix(l.latitude, l.longitude, l.accuracy, l.time)
        }
    }
}