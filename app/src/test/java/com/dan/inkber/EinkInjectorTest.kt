package com.dan.inkber

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pure-JVM tests for the e-ink injection logic. No Android, no Robolectric.
 */
class EinkInjectorTest {

    @Test fun cssContainsEinkRules() {
        val css = EinkInjector.css()
        listOf(
            "color-scheme: light only",
            "background: #ffffff",
            "color: #111111",
            "animation: none",
            "transition: none",
            "line-height: 1.5",
            "__inkberInjected"
        ).forEach {
            assertTrue("CSS missing: $it", css.contains(it))
        }
    }

    @Test fun cssFontBoostApplied() {
        val css = EinkInjector.css(fontBoostPercent = 25)
        assertTrue(css.contains("25% larger"))
    }

    @Test fun cssFontBoostClampedLow() {
        val css = EinkInjector.css(fontBoostPercent = 0)
        assertTrue(css.contains("0% larger"))
    }

    @Test fun cssFontBoostOutOfRangeThrows() {
        try {
            EinkInjector.css(fontBoostPercent = 200)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {}
        try {
            EinkInjector.css(fontBoostPercent = -1)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {}
    }

    @Test fun shouldInjectForUberOrigins_rides() {
        assertTrue(EinkInjector.shouldInject("https://m.uber.com/"))
    }

    @Test fun shouldInjectForUberOrigins_eats() {
        assertTrue(EinkInjector.shouldInject("https://eats.uber.com/"))
    }

    @Test fun shouldInjectForWwwUbereats() {
        // Bug 3 fix: www.ubereats.com must be in the inject list.
        assertTrue(EinkInjector.shouldInject("https://www.ubereats.com/"))
    }

    @Test fun shouldInjectForAuthAndRiders() {
        assertTrue(EinkInjector.shouldInject("https://auth.uber.com/login"))
        assertTrue(EinkInjector.shouldInject("https://riders.uber.com/trip"))
    }

    @Test fun shouldNotInjectForExternalSites() {
        assertFalse(EinkInjector.shouldInject("https://google.com"))
        assertFalse(EinkInjector.shouldInject("https://facebook.com"))
        assertFalse(EinkInjector.shouldInject("https://example.com"))
    }

    @Test fun shouldNotInjectForMalformedUrls() {
        assertFalse(EinkInjector.shouldInject("not a url"))
        assertFalse(EinkInjector.shouldInject(""))
    }

    @Test fun isInternalForUberDomains() {
        assertTrue(EinkInjector.isInternal("https://m.uber.com/"))
        assertTrue(EinkInjector.isInternal("https://eats.uber.com/"))
        assertTrue(EinkInjector.isInternal("https://www.ubereats.com/"))
        assertTrue(EinkInjector.isInternal("https://login.uber.com/v2/login"))
    }

    @Test fun isNotInternalForExternalDomains() {
        assertFalse(EinkInjector.isInternal("https://google.com"))
        assertFalse(EinkInjector.isInternal("https://apple.com"))
    }

    @Test fun isBlockedForAdHosts() {
        assertTrue(EinkInjector.isBlocked("https://google-analytics.com/analytics.js"))
        assertTrue(EinkInjector.isBlocked("https://www.googletagmanager.com/gtm.js"))
        assertTrue(EinkInjector.isBlocked("https://ads.doubleclick.net/banner"))
    }

    @Test fun isNotBlockedForUberOrInnocentHosts() {
        assertFalse(EinkInjector.isBlocked("https://m.uber.com/"))
        assertFalse(EinkInjector.isBlocked("https://cdn.uber.com/image.png"))
        assertFalse(EinkInjector.isBlocked("https://example.com/script.js"))
    }

    @Test fun locationOverrideScriptOverridesGetCurrentPosition() {
        val js = EinkInjector.locationOverrideScript(37.7749, -122.4194)
        assertTrue("must override getCurrentPosition",
            js.contains("navigator.geolocation.getCurrentPosition = function"))
        assertTrue(js.contains("latitude: 37.7749"))
        assertTrue(js.contains("longitude: -122.4194"))
        assertTrue(js.contains("__inkberLoc"))
        assertTrue(js.contains("inkber:location"))
    }

    @Test fun locationOverrideScriptOverridesWatchPosition() {
        val js = EinkInjector.locationOverrideScript(37.7749, -122.4194)
        assertTrue("must override watchPosition",
            js.contains("navigator.geolocation.watchPosition = function"))
    }

    @Test fun locationOverrideScriptProvidesFullCoordsObject() {
        val js = EinkInjector.locationOverrideScript(0.0, 0.0)
        // The coords object must have all fields that the GeolocationPosition
        // API expects, otherwise Uber's code may throw.
        listOf("latitude", "longitude", "accuracy", "altitude", "altitudeAccuracy",
               "heading", "speed", "timestamp").forEach {
            assertTrue("coords missing: $it", js.contains(it))
        }
    }

    @Test fun locationOverrideScriptRejectsOutOfRangeCoords() {
        try {
            EinkInjector.locationOverrideScript(91.0, 0.0)
            fail("Expected IllegalArgumentException for lat > 90")
        } catch (e: IllegalArgumentException) {}
        try {
            EinkInjector.locationOverrideScript(0.0, 181.0)
            fail("Expected IllegalArgumentException for lon > 180")
        } catch (e: IllegalArgumentException) {}
    }

    @Test fun cssIsIdempotentGuard() {
        val css = EinkInjector.css()
        val guardCount = Regex("window\\.__inkberInjected").findAll(css).count()
        assertTrue("Expected idempotency guard", guardCount >= 2)
    }
}