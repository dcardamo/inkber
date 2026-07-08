package com.dan.inkber

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var ridesWebView: UberWebView
    private lateinit var eatsWebView: UberWebView
    private lateinit var locationProvider: LocationProvider

    private lateinit var tabRides: Button
    private lateinit var tabEats: Button
    private lateinit var tabSettings: Button

    private var activeTab = Tab.RIDES
    private var inTrip = false

    private enum class Tab(val url: String) {
        RIDES("https://m.uber.com"),
        EATS("https://www.ubereats.com")
    }

    private val requestLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            prefs.locationEnabled = granted
            if (!granted) prefs.locationPromptState = Prefs.PROMPT_NEVER_ASK
            if (granted) injectLocationIntoActiveWebView()
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs.of(this)
        locationProvider = LocationProvider(this)

        // No activity-transition animations on e-ink.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        tabRides = findViewById(R.id.tab_rides)
        tabEats = findViewById(R.id.tab_eats)
        tabSettings = findViewById(R.id.tab_settings)

        val container = findViewById<android.widget.FrameLayout>(R.id.webview_container)
        ridesWebView = UberWebView(this).apply { tag = "rides" }
        eatsWebView = UberWebView(this).apply { tag = "eats" }
        container.addView(ridesWebView)
        container.addView(eatsWebView)
        eatsWebView.visibility = View.GONE

        configureWebView(ridesWebView)
        configureWebView(eatsWebView)

        ridesWebView.restoreState(savedInstanceState?.getBundle(KEY_RIDES_STATE) ?: Bundle())
        eatsWebView.restoreState(savedInstanceState?.getBundle(KEY_EATS_STATE) ?: Bundle())

        if (savedInstanceState == null) {
            ridesWebView.loadUrl(Tab.RIDES.url)
            eatsWebView.loadUrl(Tab.EATS.url)
        }

        tabRides.setOnClickListener { switchTab(Tab.RIDES) }
        tabEats.setOnClickListener { switchTab(Tab.EATS) }
        tabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        updateTabStyles()
    }

    override fun onResume() {
        super.onResume()
        // Re-read prefs in case they changed in SettingsActivity.
        configureWebView(ridesWebView)
        configureWebView(eatsWebView)
        applyKeepScreenOn(inTrip && prefs.screenOnDuringTrip)

        if (prefs.shouldShowLocationPrompt()) {
            showLocationOptInDialog()
        } else if (prefs.locationEnabled && locationProvider.hasPermission()) {
            injectLocationIntoActiveWebView()
        }
    }

    override fun onPause() {
        super.onPause()
        applyKeepScreenOn(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle(KEY_RIDES_STATE, Bundle().also { ridesWebView.saveState(it) })
        outState.putBundle(KEY_EATS_STATE, Bundle().also { eatsWebView.saveState(it) })
        outState.putString(KEY_ACTIVE_TAB, activeTab.name)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("Recycle")
    override fun onBackPressed() {
        // Back button navigates the active WebView's history before exiting.
        val active = activeWebView()
        if (active.canGoBack()) {
            active.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private fun configureWebView(wv: UberWebView) {
        val client = UberWebViewClient(
            einkEnabled = { prefs.einkEnabled },
            fontBoostPercent = { prefs.fontBoostPercent },
            onExternalUrl = { url -> openExternal(url) },
            onTripStateChange = { trip -> onTripStateChange(trip) }
        )
        val chrome = UberWebChromeClient(this) { prefs.locationEnabled }
        wv.webViewClient = client
        wv.webChromeClient = chrome
    }

    private fun switchTab(tab: Tab) {
        if (tab == activeTab) return
        activeTab = tab
        when (tab) {
            Tab.RIDES -> {
                ridesWebView.visibility = View.VISIBLE
                eatsWebView.visibility = View.GONE
            }
            Tab.EATS -> {
                ridesWebView.visibility = View.GONE
                eatsWebView.visibility = View.VISIBLE
            }
        }
        updateTabStyles()
    }

    private fun activeWebView(): UberWebView =
        if (activeTab == Tab.RIDES) ridesWebView else eatsWebView

    private fun updateTabStyles() {
        val active = 0xFF111111.toInt()
        val inactive = 0xFF666666.toInt()
        tabRides.setTextColor(if (activeTab == Tab.RIDES) active else inactive)
        tabEats.setTextColor(if (activeTab == Tab.EATS) active else inactive)
    }

    private fun onTripStateChange(trip: Boolean) {
        inTrip = trip
        applyKeepScreenOn(trip && prefs.screenOnDuringTrip)
    }

    private fun applyKeepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // No handler - silently ignore so we don't crash on a privacy-first
            // device that may not have a browser installed.
        }
    }

    private fun showLocationOptInDialog() {
        prefs.locationPromptState = Prefs.PROMPT_SHOWN_AWAITING
        AlertDialog.Builder(this)
            .setTitle(R.string.location_dialog_title)
            .setMessage(R.string.location_dialog_message)
            .setPositiveButton(R.string.location_dialog_allow) { _, _ ->
                requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton(R.string.location_dialog_not_now) { _, _ ->
                prefs.locationEnabled = false
                // Leave prompt state as SHOWN_AWAITING so we can ask again next launch.
                prefs.locationPromptState = Prefs.PROMPT_NOT_SHOWN
            }
            .setNeutralButton(R.string.location_dialog_never) { _, _ ->
                prefs.locationEnabled = false
                prefs.locationPromptState = Prefs.PROMPT_NEVER_ASK
            }
            .setCancelable(false)
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun injectLocationIntoActiveWebView() {
        val fix = locationProvider.lastKnownLocation() ?: return
        val js = EinkInjector.locationHookScript(fix.latitude, fix.longitude)
        activeWebView().evaluateJavascript(js, null)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        prefs.locationEnabled = granted
        if (granted) injectLocationIntoActiveWebView()
    }

    companion object {
        private const val KEY_RIDES_STATE = "rides_state"
        private const val KEY_EATS_STATE = "eats_state"
        private const val KEY_ACTIVE_TAB = "active_tab"
    }
}