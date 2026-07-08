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
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class UberWebChromeClientTest {

    @Test fun grantsGeoWhenEnabledAndInternalAndPermitted() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val client = UberWebChromeClient(ctx) { true } // location enabled
        assertTrue(client.wouldGrantGeolocation("https://m.uber.com/", true))
        assertTrue(client.wouldGrantGeolocation("https://eats.uber.com/", true))
    }

    @Test fun deniesGeoWhenLocationDisabledAtAppLevel() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val client = UberWebChromeClient(ctx) { false } // location disabled
        assertFalse(client.wouldGrantGeolocation("https://m.uber.com/", true))
    }

    @Test fun deniesGeoForExternalOrigins() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val client = UberWebChromeClient(ctx) { true }
        assertFalse(client.wouldGrantGeolocation("https://evil.example.com/", true))
        assertFalse(client.wouldGrantGeolocation("https://google.com/", true))
    }

    @Test fun deniesGeoWhenSystemPermissionMissing() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val client = UberWebChromeClient(ctx) { true }
        assertFalse(client.wouldGrantGeolocation("https://m.uber.com/", false))
    }
}