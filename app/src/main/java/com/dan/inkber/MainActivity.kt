package com.dan.inkber

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

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

    private val locationPromptResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "LocationPrompt result: ${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                injectLocationIntoActiveWebView()
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs.of(this)
        locationProvider = LocationProvider(this)

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
        Log.d(TAG, "onResume: shouldShowPrompt=${prefs.shouldShowLocationPrompt()} promptState=${prefs.locationPromptState} hasPerm=${locationProvider.hasPermission()}")
        configureWebView(ridesWebView)
        configureWebView(eatsWebView)
        applyKeepScreenOn(inTrip && prefs.screenOnDuringTrip)

        if (prefs.shouldShowLocationPrompt()) {
            showLocationOptInDialog()
        } else if (locationProvider.hasPermission()) {
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
            onTripStateChange = { trip -> onTripStateChange(trip) },
            onLocationReady = { injectLocationIntoWebView(wv) }
        )
        val chrome = UberWebChromeClient(this)
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
        }
    }

    private fun showLocationOptInDialog() {
        Log.d(TAG, "showLocationOptInDialog: launching LocationPromptActivity")
        prefs.locationPromptState = Prefs.PROMPT_SHOWN_AWAITING
        locationPromptResult.launch(Intent(this, LocationPromptActivity::class.java))
    }

    @SuppressLint("SetTextI18n")
    private fun injectLocationIntoActiveWebView() {
        injectLocationIntoWebView(activeWebView())
    }

    private fun injectLocationIntoWebView(wv: WebView) {
        val fix = locationProvider.lastKnownLocation() ?: return
        wv.evaluateJavascript(
            EinkInjector.locationOverrideScript(fix.latitude, fix.longitude), null
        )
    }

    companion object {
        private const val TAG = "Inkber"
        private const val KEY_RIDES_STATE = "rides_state"
        private const val KEY_EATS_STATE = "eats_state"
        private const val KEY_ACTIVE_TAB = "active_tab"
    }
}