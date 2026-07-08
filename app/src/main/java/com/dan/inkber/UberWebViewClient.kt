package com.dan.inkber

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * Decides which URLs the WebView is allowed to load, blocks ad/tracker hosts,
 * keeps navigation inside the in-app WebView for Uber domains, and injects the
 * e-ink stylesheet after each page finishes loading.
 */
class UberWebViewClient(
    private val einkEnabled: () -> Boolean,
    private val fontBoostPercent: () -> Int,
    private val onExternalUrl: (String) -> Unit,
    private val onTripStateChange: (Boolean) -> Unit
) : WebViewClient() {

    private val emptyResponse by lazy {
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url ?: return null
        if (EinkInjector.isBlocked(url.toString())) return emptyResponse
        return null
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        return if (EinkInjector.isInternal(url)) {
            false // let the WebView load it
        } else {
            onExternalUrl(url)
            true // hand off to the system browser
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { onTripStateChange(isTripUrl(it)) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (view == null || url == null) return
        if (!einkEnabled()) return
        if (!EinkInjector.shouldInject(url)) return
        view.evaluateJavascript(EinkInjector.css(fontBoostPercent()), null)
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        // Never silently proceed - let the page show the cert error so users
        // can decide. Safer on a privacy-focused device like the Kompakt.
        handler?.cancel()
    }

    /** Match Uber's trip-status URL patterns to toggle keep-screen-on. */
    private fun isTripUrl(url: String): Boolean {
        return url.contains("/trip/") ||
            url.contains("/trips/") ||
            url.contains("tripState=") ||
            url.contains("/ride/")
    }
}