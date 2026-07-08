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
        // Core e-ink rules must be present.
        listOf(
            "color-scheme: light only",
            "background: #ffffff",
            "color: #111111",
            "animation: none",
            "transition: none",
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
        // coerceAtLeast(0) - a negative input should still produce a positive boost.
        // (caller is expected to clamp, but the function should not throw)
        val css = EinkInjector.css(fontBoostPercent = 0)
        assertTrue(css.contains("0% larger"))
    }

    @Test fun cssFontBoostOutOfRangeThrows() {
        try {
            EinkInjector.css(fontBoostPercent = 200)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            EinkInjector.css(fontBoostPercent = -1)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun shouldInjectForUberOrigins() {
        assertTrue(EinkInjector.shouldInject("https://m.uber.com/"))
        assertTrue(EinkInjector.shouldInject("https://eats.uber.com/"))
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
        assertTrue(EinkInjector.isBlocked("https://connect.facebook.net/en_US/fbevents.js"))
    }

    @Test fun isNotBlockedForUberOrInnocentHosts() {
        assertFalse(EinkInjector.isBlocked("https://m.uber.com/"))
        assertFalse(EinkInjector.isBlocked("https://cdn.uber.com/image.png"))
        assertFalse(EinkInjector.isBlocked("https://example.com/script.js"))
    }

    @Test fun locationHookScriptEmbedsCoords() {
        val js = EinkInjector.locationHookScript(37.7749, -122.4194)
        assertTrue(js.contains("lat: 37.7749"))
        assertTrue(js.contains("lon: -122.4194"))
        assertTrue(js.contains("__inkberLoc"))
        assertTrue(js.contains("inkber:location"))
    }

    @Test fun locationHookScriptRejectsOutOfRangeCoords() {
        try {
            EinkInjector.locationHookScript(91.0, 0.0)
            fail("Expected IllegalArgumentException for lat > 90")
        } catch (e: IllegalArgumentException) {}
        try {
            EinkInjector.locationHookScript(0.0, 181.0)
            fail("Expected IllegalArgumentException for lon > 180")
        } catch (e: IllegalArgumentException) {}
        try {
            EinkInjector.locationHookScript(-91.0, 0.0)
            fail("Expected IllegalArgumentException for lat < -90")
        } catch (e: IllegalArgumentException) {}
    }

    @Test fun cssIsIdempotentGuard() {
        // The injected IIFE guards against double-injection with __inkberInjected.
        // Verify the guard is present so repeated onPageFinished calls are safe.
        val css = EinkInjector.css()
        val guardCount = Regex("window\\.__inkberInjected").findAll(css).count()
        assertTrue("Expected idempotency guard", guardCount >= 2) // check + set
    }
}