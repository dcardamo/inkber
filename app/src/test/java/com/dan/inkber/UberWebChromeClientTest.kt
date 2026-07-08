package com.dan.inkber

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for the WebChromeClient geolocation gating logic.
 *
 * Bug 1 fix: there is no longer a separate "locationEnabled" app-level toggle.
 * Geolocation is granted solely based on the system permission state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class UberWebChromeClientTest {

    @Test fun grantsGeoWhenSystemPermissionGrantedAndInternal() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val client = UberWebChromeClient(ctx)
        assertTrue(client.wouldGrantGeolocation("https://m.uber.com/", true))
        assertTrue(client.wouldGrantGeolocation("https://www.ubereats.com/", true))
    }

    @Test fun deniesGeoWhenSystemPermissionMissing() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val client = UberWebChromeClient(ctx)
        assertFalse(client.wouldGrantGeolocation("https://m.uber.com/", false))
    }

    @Test fun deniesGeoForExternalOrigins() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val client = UberWebChromeClient(ctx)
        assertFalse(client.wouldGrantGeolocation("https://evil.example.com/", true))
        assertFalse(client.wouldGrantGeolocation("https://google.com/", true))
    }
}