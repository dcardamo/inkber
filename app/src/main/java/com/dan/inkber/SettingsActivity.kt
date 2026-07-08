package com.dan.inkber

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
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
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Show the current location permission status in the Location summary.
            findPreference<Preference>("location_status")?.apply {
                val granted = LocationProvider(requireContext()).hasPermission()
                summary = if (granted) {
                    "Granted — Uber can use your precise location for pickup and ETAs."
                } else {
                    getString(R.string.settings_location_summary)
                }
            }

            findPreference<SeekBarPreference>("font_boost")?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    Prefs.of(requireContext()).fontBoostPercent = (newValue as Int)
                    true
                }
            }

            findPreference<Preference>("about_version")?.apply {
                summary = "0.1.6"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyKeepScreenOn(Prefs.of(this).screenOnDuringTrip)
    }

    private fun applyKeepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}