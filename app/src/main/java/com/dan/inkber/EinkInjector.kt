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
     * The e-ink CSS as pure stylesheet text (no JS wrapper).
     *
     * Key design decisions:
     * - Use extremely high specificity to override Uber's CSS-in-JS, which
     *   generates inline styles and class-based overrides with !important.
     * - Set line-height on every element type explicitly because Uber uses
     *   compressed line-heights (1.1-1.2) that squish text on e-ink.
     * - Force light theme via multiple mechanisms: color-scheme, background,
     *   color, and dark-mode media query override.
     * - Target common dark-mode class patterns used by Uber Eats's React SPA.
     */
    fun cssText(fontBoostPercent: Int = 15): String {
        require(fontBoostPercent in 0..100) { "fontBoostPercent out of range: $fontBoostPercent" }
        val rootFontSize = (16 * (1 + fontBoostPercent.toDouble() / 100.0)).toString()
        return """
            :root { color-scheme: light only !important; font-size: ${rootFontSize}px !important; }
            html, body {
              background: #ffffff !important;
              color: #111111 !important;
              color-scheme: light only !important;
              line-height: 1.5 !important;
              --bg-color: #ffffff !important;
              --text-color: #111111 !important;
              --color-background: #ffffff !important;
              --color-foreground: #111111 !important;
              --background-color: #ffffff !important;
              --foreground-color: #111111 !important;
            }
            *, *::before, *::after {
              animation: none !important;
              transition: none !important;
              scroll-behavior: auto !important;
              line-height: 1.5 !important;
            }
            html, body, div, span, p, a, li, td, th, label, button, input, select, textarea,
            h1, h2, h3, h4, h5, h6, section, article, header, footer, nav, main, aside,
            [class], [id] {
              color: #111111 !important;
              line-height: 1.5 !important;
              text-rendering: optimizeLegibility !important;
              -webkit-font-smoothing: none !important;
            }
            /* Force all backgrounds to white — covers Uber's dark-mode classes,
               styled-components, and CSS-in-JS inline styles. */
            html, body, div, section, article, header, footer, nav, main, aside,
            html body, html body div, html body section, html body article,
            [class*='dark'], [class*='theme-dark'], [class*='night'], [data-theme='dark'],
            [class*='background'], [class*='bg-'], [class*='container'], [class*='wrapper'],
            [class*='card'], [class*='panel'], [class*='sheet'], [class*='modal'],
            [class*='overlay'], [class*='screen'], [class*='page'], [class*='view'] {
              background: #ffffff !important;
              background-color: #ffffff !important;
            }
            a { color: #000000 !important; text-decoration: underline !important; }
            input, select, textarea, button {
              background: #ffffff !important;
              background-color: #ffffff !important;
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
              filter: none !important;
            }
            /* Override inline background styles that Uber's JS sets. */
            [style*='background:'], [style*='background-color:'] {
              background: #ffffff !important;
              background-color: #ffffff !important;
            }
            button[style*='background'], .btn, [class*='primary'] {
              filter: grayscale(0.35) contrast(1.05) !important;
            }
            video, [class*='video'], [class*='carousel'] { display: none !important; }
            @media (prefers-color-scheme: dark) {
              html, body, *, *::before, *::after {
                background: #ffffff !important;
                background-color: #ffffff !important;
                color: #111111 !important;
              }
            }
        """.trimIndent()
    }

    /**
     * The full JS injection script. Injects the CSS AND sets up a
     * MutationObserver to re-inject it when Uber's SPA dynamically adds
     * content. Uber is a JS-heavy single-page app that renders most of its
     * DOM after onPageFinished, so a one-shot CSS injection is not enough.
     *
     * The style element is moved to the end of <head> on every injection so
     * it wins any source-order ties against Uber's own !important rules.
     */
    /**
     * JS injection variant that also forces a synchronous re-layout of the
     * style element. Called from onPageStarted so the stylesheet is present
     * before Uber's JS runs, even when the DOM is initially empty.
     */
    fun css(fontBoostPercent: Int = 15): String {
        require(fontBoostPercent in 0..100) { "fontBoostPercent out of range: $fontBoostPercent" }
        val css = cssText(fontBoostPercent).replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n")
        return """
            (function(){
              var cssStr = '$css';
              function injectStyle() {
                var s = document.getElementById('inkber-eink');
                if (!s) {
                  s = document.createElement('style');
                  s.id = 'inkber-eink';
                }
                s.textContent = cssStr;
                var target = document.head || document.documentElement;
                // Move to the end so our !important declarations win ties.
                if (target.lastChild !== s) {
                  target.appendChild(s);
                }
              }
              function forceLightTheme() {
                document.documentElement.setAttribute('data-theme', 'light');
                document.documentElement.setAttribute('color-scheme', 'light');
                document.documentElement.style.colorScheme = 'light';
                if (document.body) {
                  document.body.setAttribute('data-theme', 'light');
                  document.body.style.background = '#ffffff';
                  document.body.style.color = '#111111';
                  document.body.style.colorScheme = 'light';
                }
              }
              injectStyle();
              forceLightTheme();
              if (window.__inkberObserver) return;
              var observer = new MutationObserver(function(mutations) {
                injectStyle();
                forceLightTheme();
              });
              if (document.documentElement) {
                observer.observe(document.documentElement, {
                  childList: true,
                  subtree: true,
                  attributes: true,
                  attributeFilter: ['style', 'class', 'data-theme']
                });
              }
              window.__inkberObserver = observer;
            })();
        """.trimIndent()
    }

    /**
     * JS that overrides navigator.geolocation.getCurrentPosition to return the
     * device's cached GPS fix. Injected as early as possible (onPageStarted)
     * so that Uber's "use current location" button works even if it calls
     * getCurrentPosition before onPageFinished.
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
              if (navigator.geolocation) {
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