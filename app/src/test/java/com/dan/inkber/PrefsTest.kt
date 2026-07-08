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
        ctx.getSharedPreferences("inkber_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()
        prefs = Prefs.of(ctx)
    }

    @Test fun defaultsArePrivacyFirst() {
        // No locationEnabled pref anymore — location is solely the system
        // permission. E-ink on, screen-on off, prompt not shown.
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

    @Test fun shouldShowPromptWhenNotShown() {
        // Bug 1 fix: no locationEnabled check — prompt shows if not yet shown
        // and not permanently dismissed.
        assertTrue(prefs.shouldShowLocationPrompt())
    }

    @Test fun shouldNotShowPromptWhenNeverAsk() {
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
        prefs.einkEnabled = false
        prefs.screenOnDuringTrip = true
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val again = Prefs.of(ctx)
        assertFalse(again.einkEnabled)
        assertTrue(again.screenOnDuringTrip)
    }
}