package com.dan.inkber

/**
 * Generates the CSS and JS injected into Uber's mobile web pages to make them
 * readable on a 4.3" E Ink Carta panel (Mudita Kompakt).
 *
 * Balanced policy: force light theme, disable animations/transitions (the main
 * source of e-ink ghosting), boost contrast and font size, and desaturate
 * decorative coloured elements so they dither cleanly to grayscale. Functional
 * Uber UI - map tiles, car icons, route polylines - is left intact.
 *
 * Kept free of Android imports so it can be unit-tested on the JVM.
 */
object EinkInjector {

    /** Uber origins we inject the e-ink stylesheet into. */
    val INJECT_ORIGINS = setOf(
        "m.uber.com",
        "eats.uber.com",
        "auth.uber.com",
        "riders.uber.com",
        "www.uber.com",
        "ubereats.com",
        "www.ubereats.com"
    )

    /** Origins whose links may be opened in the in-app WebView (login flows etc). */
    val INTERNAL_HOST_SUFFIXES = listOf(
        ".uber.com",
        ".ubereats.com"
    )

    /** Ad / tracker / autoplay hosts we block at the resource-request layer. */
    val BLOCKED_HOSTS = setOf(
        "google-analytics.com",
        "googletagmanager.com",
        "doubleclick.net",
        "facebook.net",
        "connect.facebook.net",
        "scorecardresearch.com",
        "advertising.com",
        "moatads.com",
        "criteo.com",
        "adnxs.com",
        "pubmatic.com",
        "rubiconproject.com",
        "taboola.com",
        "outbrain.com"
    )

    /**
     * The e-ink stylesheet. Injected once per page load via a <style> element.
     * Written defensively (!important) because Uber's own CSS is high-specificity.
     */
    fun css(fontBoostPercent: Int = 15): String {
        require(fontBoostPercent in 0..100) { "fontBoostPercent out of range: $fontBoostPercent" }
        val boost = "${fontBoostPercent.coerceAtLeast(0)}%"
        return """
            (function(){
              if (window.__inkberInjected) return;
              window.__inkberInjected = true;
              var s = document.createElement('style');
              s.id = 'inkber-eink';
              s.textContent = `
                :root { color-scheme: light only !important; }
                html, body {
                  background: #ffffff !important;
                  color: #111111 !important;
                  color-scheme: light only !important;
                }
                * {
                  animation: none !important;
                  transition: none !important;
                  scroll-behavior: auto !important;
                }
                body, p, span, div, li, td, th, label, button, a, input, select, textarea, h1, h2, h3, h4, h5, h6 {
                  color: #111111 !important;
                  font-size: ${boost} larger;
                  line-height: 1.5 !important;
                  text-rendering: optimizeLegibility !important;
                  -webkit-font-smoothing: none !important;
                }
                a { color: #000000 !important; text-decoration: underline !important; }
                input, select, textarea, button {
                  background: #ffffff !important;
                  border-color: #888888 !important;
                  color: #111111 !important;
                }
                img { filter: grayscale(1) contrast(1.08) !important; }
                [role='dialog'], [role='alertdialog'] {
                  background: #ffffff !important;
                  color: #111111 !important;
                  border: 1px solid #888888 !important;
                }
                [style*='blur'], [style*='gradient'], [style*='backdrop-filter'] {
                  background: #ffffff !important;
                  box-shadow: none !important;
                  backdrop-filter: none !important;
                }
                button[style*='background'], .btn, [class*='primary'] {
                  filter: grayscale(0.35) contrast(1.05) !important;
                }
                video, [class*='video'], [class*='carousel'] { display: none !important; }
                @media (prefers-color-scheme: dark) {
                  html, body, div, span, p { background: #ffffff !important; color: #111111 !important; }
                }
              `;
              (document.head || document.documentElement).appendChild(s);
            })();
        """.trimIndent()
    }

    /**
     * JS that overrides navigator.geolocation.getCurrentPosition to return the
     * device's cached GPS fix. This is what makes Uber's "use current location"
     * button work — Uber's web app calls the standard browser geolocation API,
     * and without this override the WebView's geo prompt may not fire or may
     * return a stale/empty position.
     *
     * Also dispatches an 'inkber:location' event for any custom listeners.
     */
    fun locationOverrideScript(lat: Double, lon: Double): String {
        require(lat in -90.0..90.0) { "lat out of range" }
        require(lon in -180.0..180.0) { "lon out of range" }
        return """
            (function(){
              var pos = {
                coords: {
                  latitude: $lat,
                  longitude: $lon,
                  accuracy: 1,
                  altitude: null,
                  altitudeAccuracy: null,
                  heading: null,
                  speed: null
                },
                timestamp: Date.now()
              };
              var err = { code: 1, message: 'Location not available' };
              if (navigator.geolocation) {
                var orig = navigator.geolocation.getCurrentPosition.bind(navigator.geolocation);
                navigator.geolocation.getCurrentPosition = function(success, failure, opts) {
                  success(pos);
                };
                navigator.geolocation.watchPosition = function(success, failure, opts) {
                  success(pos);
                  return 0;
                };
              }
              window.__inkberLoc = pos;
              try {
                var evt = new CustomEvent('inkber:location', { detail: pos });
                window.dispatchEvent(evt);
              } catch(e) {}
            })();
        """.trimIndent()
    }

    /** True if [url] should receive the e-ink injection. */
    fun shouldInject(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return INJECT_ORIGINS.any { host == it || host.endsWith(".$it") }
    }

    /** True if [url] should stay inside the in-app WebView (Uber domains). */
    fun isInternal(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return INTERNAL_HOST_SUFFIXES.any { host == it.removePrefix(".") || host.endsWith(it) }
    }

    /** True if [url] host matches a blocked ad/tracker host. */
    fun isBlocked(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return BLOCKED_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /** JS that pipes the device's last-known GPS into Uber's page as a fallback. */
    fun locationHookScript(lat: Double, lon: Double): String {
        require(lat in -90.0..90.0) { "lat out of range" }
        require(lon in -180.0..180.0) { "lon out of range" }
        return """
            (function(){
              if (window.__inkberLoc) return;
              window.__inkberLoc = { lat: $lat, lon: $lon, ts: Date.now() };
              try {
                var evt = new CustomEvent('inkber:location', { detail: window.__inkberLoc });
                window.dispatchEvent(evt);
              } catch(e) {}
            })();
        """.trimIndent()
    }

    private fun hostOf(url: String): String? {
        return try {
            val u = java.net.URI(url)
            u.host?.lowercase()
        } catch (e: Exception) {
            null
        }
    }
}