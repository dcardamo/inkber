package com.dan.inkber

import android.content.Context
import android.content.SharedPreferences

/**
 * Typed accessor for the Inkber preferences.
 *
 * Keys are namespaced under "inkber_" to avoid collisions when backed up /
 * restored. Defaults are chosen so that the app is private-first: location is
 * off, e-ink optimisations are on (the whole point of the app), screen-on is
 * off (battery), and the location-prompt state is "not yet shown".
 */
class Prefs private constructor(private val sp: SharedPreferences) {

    companion object {
        private const val FILE = "inkber_prefs"
        private const val K_LOCATION = "inkber_location_enabled"
        private const val K_EINK = "inkber_eink_enabled"
        private const val K_FONT_BOOST = "inkber_font_boost_pct"
        private const val K_SCREEN_ON = "inkber_screen_on_during_trip"
        private const val K_LOC_PROMPT = "inkber_loc_prompt_state"

        const val PROMPT_NOT_SHOWN = 0
        const val PROMPT_SHOWN_AWAITING = 1
        const val PROMPT_NEVER_ASK = 2

        fun of(context: Context): Prefs =
            Prefs(context.getSharedPreferences(FILE, Context.MODE_PRIVATE))
    }

    var locationEnabled: Boolean
        get() = sp.getBoolean(K_LOCATION, false)
        set(v) { sp.edit().putBoolean(K_LOCATION, v).apply() }

    var einkEnabled: Boolean
        get() = sp.getBoolean(K_EINK, true)
        set(v) { sp.edit().putBoolean(K_EINK, v).apply() }

    var fontBoostPercent: Int
        get() = sp.getInt(K_FONT_BOOST, 15)
        set(v) { sp.edit().putInt(K_FONT_BOOST, v.coerceIn(0, 100)).apply() }

    var screenOnDuringTrip: Boolean
        get() = sp.getBoolean(K_SCREEN_ON, false)
        set(v) { sp.edit().putBoolean(K_SCREEN_ON, v).apply() }

    /** Tri-state for the first-launch location opt-in dialog. */
    var locationPromptState: Int
        get() = sp.getInt(K_LOC_PROMPT, PROMPT_NOT_SHOWN)
        set(v) { sp.edit().putInt(K_LOC_PROMPT, v).apply() }

    /** True iff we should show the opt-in dialog now. */
    fun shouldShowLocationPrompt(): Boolean =
        locationPromptState == PROMPT_NOT_SHOWN && !locationEnabled
}