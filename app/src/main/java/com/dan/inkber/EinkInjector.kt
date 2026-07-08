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
        "ubereats.com"
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
                /* Leave functional map tiles and overlays alone: Uber's map
                   canvases carry car icons and route polylines we must not
                   desaturate. Match by class/role heuristic. */
                [role='dialog'], [role='alertdialog'] {
                  background: #ffffff !important;
                  color: #111111 !important;
                  border: 1px solid #888888 !important;
                }
                /* Hide decorative gradients/blurs that ghost on e-ink. */
                [style*='blur'], [style*='gradient'], [style*='backdrop-filter'] {
                  background: #ffffff !important;
                  box-shadow: none !important;
                  backdrop-filter: none !important;
                }
                /* Soften but keep brand colour on primary action buttons so
                   users can still tell the main CTA apart. */
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