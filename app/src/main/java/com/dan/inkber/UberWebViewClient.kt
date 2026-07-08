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
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        // This fires after the page is visually rendered — better timing for
        // Uber's SPA which renders most content via JS after onPageFinished.
        injectEinkAndLocation(view, url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // Also inject on onPageFinished as a fallback for pages where
        // onPageCommitVisible didn't fire or fired too early.
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