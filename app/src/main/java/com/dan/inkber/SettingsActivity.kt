package com.dan.inkber

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import androidx.preference.SeekBarPreference

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        // No activity-transition animations on e-ink.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Wire the location toggle to Prefs + re-request system permission.
            findPreference<SwitchPreferenceCompat>("location_enabled")?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    val prefs = Prefs.of(requireContext())
                    prefs.locationEnabled = enabled
                    if (enabled) {
                        // Defer to MainActivity to request the system permission
                        // on resume; here we just record intent.
                        prefs.locationPromptState = Prefs.PROMPT_SHOWN_AWAITING
                    }
                    true
                }
            }

            findPreference<SeekBarPreference>("font_boost")?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    Prefs.of(requireContext()).fontBoostPercent = (newValue as Int)
                    true
                }
            }

            findPreference<Preference>("about_version")?.apply {
                summary = "0.1.0"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOn(Prefs.of(this).screenOnDuringTrip)
    }

    private fun applyKeepScreenOn(on: Boolean) {
        val flags = window.attributes.flags
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}