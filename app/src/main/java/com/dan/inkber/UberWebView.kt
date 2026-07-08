package com.dan.inkber

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * A WebView preconfigured for e-ink rendering and Uber's mobile web.
 *
 * Key settings and why:
 *  - LAYER_TYPE_SOFTWARE: hardware layers on E Ink panels cause artefacts and
 *    ghosting; software rendering produces clean partial updates.
 *  - javaScriptEnabled + domStorageEnabled: Uber's web app needs both.
 *  - setGeolocationEnabled: gated at the WebChromeClient layer; safe to leave
 *    the WebView-level flag on because the Chrome client answers per-origin.
 *  - MediaPlaybackRequiresUserGesture: blocks autoplay video that ghosts.
 *  - cacheMode = LOAD_DEFAULT: let the WebView use its HTTP cache normally;
 *    Uber's pages are heavy and the Kompakt's data connection may be slow.
 */
class UberWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    init {
        configure()
    }

    private fun configure() {
        // Software rendering avoids e-ink panel artefacts from hardware layers,
        // but on the Android emulator (swiftshader) it prevents the WebView from
        // painting at all. Only enable it on real hardware, not emulators.
        if (!isEmulator()) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        with(settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true)
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
            // Uber's mobile web is responsive; don't force a desktop viewport.
            useWideViewPort = true
            loadWithOverviewMode = true
            // No file access from the web - keep the attack surface small.
            allowFileAccess = false
            allowContentAccess = false
            // Fonts: the Kompakt ships a clean system font; don't override.
            standardFontFamily = "sans-serif"
            // The reference repo's biggest missing feature: a sensible UA so
            // Uber serves the mobile web app rather than a desktop page.
            userAgentString = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = true
        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        isScrollContainer = true

        isFocusable = true
        isFocusableInTouchMode = true
        setOnTouchListener { v, _ ->
            if (!v.hasFocus()) v.requestFocus()
            false // let the WebView handle the touch (scroll, tap, etc.)
        }
    }

    companion object {
        private fun isEmulator(): Boolean =
            Build.FINGERPRINT.contains("generic") ||
                Build.FINGERPRINT.contains("emu") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("SDK") ||
                Build.MODEL.contains("sdk") ||
                Build.PRODUCT.contains("sdk") ||
                Build.PRODUCT.contains("emu") ||
                Build.BRAND == "google" && Build.PRODUCT.contains("gphone")
    }
}