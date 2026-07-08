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
 *
 * Uses onPageCommitVisible (API 23+) for the initial CSS injection because
 * Uber is a JS SPA that renders content after onPageFinished. The CSS
 * injection script also sets up a MutationObserver to re-inject when Uber's
 * JS dynamically adds DOM nodes.
 *
 * Location override is injected on onPageStarted (before the page's JS runs)
 * so that navigator.geolocation.getCurrentPosition is patched before Uber's
 * code calls it.
 */
class UberWebViewClient(
    private val einkEnabled: () -> Boolean,
    private val fontBoostPercent: () -> Int,
    private val onExternalUrl: (String) -> Unit,
    private val onTripStateChange: (Boolean) -> Unit,
    private val onLocationReady: () -> Unit = {}
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
            false
        } else {
            onExternalUrl(url)
            true
        }
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { onTripStateChange(isTripUrl(it)) }
        // Reset the injection guard so CSS re-injects on each new page load.
        view?.evaluateJavascript(
            "try{delete window.__inkberInjected;if(window.__inkberObserver){window.__inkberObserver.disconnect();delete window.__inkberObserver;}}catch(e){}", null
        )
        // Inject location override BEFORE the page's JS runs so that
        // navigator.geolocation.getCurrentPosition is patched before Uber
        // calls it for "use current location".
        if (url != null && EinkInjector.isInternal(url)) {
            onLocationReady()
        }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        injectEinkAndLocation(view, url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        injectEinkAndLocation(view, url)
    }

    private fun injectEinkAndLocation(view: WebView?, url: String?) {
        if (view == null || url == null) return
        if (!EinkInjector.isInternal(url)) return
        if (einkEnabled() && EinkInjector.shouldInject(url)) {
            view.evaluateJavascript(EinkInjector.css(fontBoostPercent()), null)
        }
        onLocationReady()
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
    }

    private fun isTripUrl(url: String): Boolean {
        return url.contains("/trip/") ||
            url.contains("/trips/") ||
            url.contains("tripState=") ||
            url.contains("/ride/")
    }
}