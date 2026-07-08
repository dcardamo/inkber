package com.dan.inkber

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric tests for Prefs (SharedPreferences-backed) state machine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class PrefsTest {

    private lateinit var prefs: Prefs

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // Clear any prior state.
        ctx.getSharedPreferences("inkber_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = Prefs.of(ctx)
    }

    @Test fun defaultsArePrivacyFirst() {
        // Location off, e-ink on, screen-on off, prompt not shown.
        assertFalse(prefs.locationEnabled)
        assertTrue(prefs.einkEnabled)
        assertFalse(prefs.screenOnDuringTrip)
        assertEquals(Prefs.PROMPT_NOT_SHOWN, prefs.locationPromptState)
        assertEquals(15, prefs.fontBoostPercent)
    }

    @Test fun fontBoostClampedToRange() {
        prefs.fontBoostPercent = 200
        assertEquals(100, prefs.fontBoostPercent)
        prefs.fontBoostPercent = -5
        assertEquals(0, prefs.fontBoostPercent)
    }

    @Test fun shouldShowPromptOnlyWhenNotShownAndLocationOff() {
        assertTrue(prefs.shouldShowLocationPrompt())
        prefs.locationEnabled = true
        assertFalse(prefs.shouldShowLocationPrompt())
        prefs.locationEnabled = false
        prefs.locationPromptState = Prefs.PROMPT_NEVER_ASK
        assertFalse(prefs.shouldShowLocationPrompt())
    }

    @Test fun promptStatePersists() {
        prefs.locationPromptState = Prefs.PROMPT_NEVER_ASK
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val again = Prefs.of(ctx)
        assertEquals(Prefs.PROMPT_NEVER_ASK, again.locationPromptState)
        assertFalse(again.shouldShowLocationPrompt())
    }

    @Test fun togglesPersist() {
        prefs.locationEnabled = true
        prefs.einkEnabled = false
        prefs.screenOnDuringTrip = true
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val again = Prefs.of(ctx)
        assertTrue(again.locationEnabled)
        assertFalse(again.einkEnabled)
        assertTrue(again.screenOnDuringTrip)
    }
}