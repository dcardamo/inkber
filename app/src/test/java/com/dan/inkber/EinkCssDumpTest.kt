package com.dan.inkber

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Dumps the e-ink CSS and a representative Uber mobile login HTML fixture to
 * docs/screenshots/ so the headless-Chromium screenshot script can load them
 * offline (no network dependency in CI).
 *
 * The HTML is a hand-built approximation of Uber's mobile login wall - it is
 * NOT copied from Uber; it's a generic mobile login layout that exercises the
 * CSS injection (light theme, no animations, grayscale images, font boost).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class EinkCssDumpTest {

    @Test fun dumpsCssAndFixture() {
        val root = run {
            var f = File(System.getProperty("user.dir") ?: ".")
            while (f.parentFile != null && !File(f, "flake.nix").exists()) {
                f = f.parentFile!!
            }
            f
        }
        val dir = File(root, "docs/screenshots").apply { mkdirs() }

        // Use cssText() directly — it's pure CSS without the JS wrapper.
        val pureCss = EinkInjector.cssText(15)
        File(dir, "eink.css").writeText(pureCss)

        // Mirror the same CSS and SPA fixture into androidTest assets so the
        // instrumented WebView tests can load them without a network server.
        val assetsDir = File(root, "app/src/androidTest/assets/fixtures").apply { mkdirs() }
        File(assetsDir, "eink.css").writeText(pureCss)

        // Build a representative mobile login page. Generic, not copied from Uber.
        // eink.css is placed last in <head> to mirror the app's JS injection order.
        val ridesFixture = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Uber</title>
<style>
  * { box-sizing: border-box; }
  body { margin: 0; font-family: -apple-system, system-ui, sans-serif; background: #fafafa; color: #222; }
  .app { display: flex; flex-direction: column; height: 100vh; }
  .content { flex: 1 1 auto; overflow: hidden; }
  .header { padding: 28px 20px 8px; }
  .logo { font-size: 28px; font-weight: 700; color: #222; }
  .hero { padding: 16px 20px; }
  .hero h1 { font-size: 22px; margin: 0 0 12px; color: #111; }
  .hero p { font-size: 15px; color: #444; line-height: 1.45; margin: 0 0 24px; }
  .map-preview {
    height: 180px; margin: 0 20px 16px; border-radius: 12px;
    background: linear-gradient(135deg, #e0e7ff 0%, #fef3c7 100%);
    display: flex; align-items: center; justify-content: center;
    color: #6b7280; font-size: 13px;
  }
  .input-group { padding: 0 20px 12px; }
  .input-group label { display: block; font-size: 13px; color: #555; margin-bottom: 6px; }
  .input-group input {
    width: 100%; padding: 14px 12px; font-size: 16px;
    border: 1px solid #ddd; border-radius: 8px; background: #fff; color: #111;
  }
  .cta { padding: 16px 20px; }
  .cta button {
    width: 100%; padding: 16px; font-size: 16px; font-weight: 600;
    background: #000; color: #fff; border: none; border-radius: 8px;
  }
  .alt { padding: 8px 20px 24px; text-align: center; }
  .alt a { color: #2563eb; text-decoration: none; font-size: 14px; }
  .footer { padding: 16px 20px; font-size: 12px; color: #888; text-align: center; }
  /* Bottom toggle bar - mirrors activity_main.xml */
  .toggle-bar {
    flex: 0 0 auto; display: flex; background: #fff;
    border-top: 1px solid #e5e5e5; padding: 10px 4px;
  }
  .toggle-bar .tab {
    flex: 1 1 0; text-align: center; padding: 12px 4px;
    font-size: 14px; color: #666; font-family: inherit;
  }
  .toggle-bar .tab.active { color: #111; font-weight: 600; }
  .spinner { display: inline-block; width: 16px; height: 16px; border: 2px solid #555;
             border-top-color: #fff; border-radius: 50%; animation: spin 1s linear infinite;
             vertical-align: middle; margin-left: 8px; }
  @keyframes spin { to { transform: rotate(360deg); } }
</style>
<link rel="stylesheet" href="eink.css">
</head>
<body>
  <div class="app">
    <div class="content">
      <div class="header"><div class="logo">Uber</div></div>
      <div class="hero">
        <h1>Get a ride or order food</h1>
        <p>Sign in to continue. We'll use your phone number to verify it's you.</p>
      </div>
      <div class="map-preview">Map preview</div>
      <div class="input-group">
        <label for="phone">Phone number or email</label>
        <input id="phone" type="text" placeholder="Enter phone number or email">
      </div>
      <div class="cta">
        <button>Continue<span class="spinner"></span></button>
      </div>
      <div class="alt"><a href="#">Use current location</a></div>
      <div class="alt"><a href="#">Use email instead</a></div>
      <div class="footer">By continuing you agree to the Terms.</div>
    </div>
    <div class="toggle-bar">
      <div class="tab active">Rides</div>
      <div class="tab">Eats</div>
      <div class="tab">Settings</div>
    </div>
  </div>
</body>
</html>
        """.trimIndent()
        File(dir, "uber-login-fixture.html").writeText(ridesFixture)

        // Eats variant — already light, just relabelled.
        val eatsFixture = ridesFixture
            .replace("<div class=\"logo\">Uber</div>", "<div class=\"logo\">Uber Eats</div>")
            .replace("Get a ride or order food", "Order food you love")
            .replace("Map preview", "Restaurants near you")
            .replace("<div class=\"tab active\">Rides</div>", "<div class=\"tab\">Rides</div>")
            .replace("<div class=\"tab\">Eats</div>", "<div class=\"tab active\">Eats</div>")
        File(dir, "uber-eats-fixture.html").writeText(eatsFixture)

        // Eats dark-mode fixture — exercises the reported Eats dark/scroll bug.
        // Mimics Uber Eats React SPA: data-theme dark, CSS-in-JS inline styles,
        // compressed line-heights, and a dark background class applied by JS.
        // The eink.css link is deliberately placed LAST in <head> because the
        // app injects it at the end of <head> via JS, so it wins source-order ties.
        val eatsDarkFixture = """
<!doctype html>
<html lang="en" data-theme="dark" color-scheme="dark" style="color-scheme: dark;">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Uber Eats</title>
<style>
  * { box-sizing: border-box; }
  body {
    margin: 0; font-family: -apple-system, system-ui, sans-serif;
    background: #0d0d0d; color: #f5f5f5;
    line-height: 1.1; /* squished text */
  }
  .theme-dark { background: #121212 !important; color: #fff !important; }
  .app { display: flex; flex-direction: column; height: 100vh; overflow: hidden; }
  .content {
    flex: 1 1 auto; overflow-y: auto; -webkit-overflow-scrolling: touch;
    background: #121212; color: #fff;
  }
  .header { padding: 28px 20px 8px; }
  .logo { font-size: 28px; font-weight: 700; color: #fff; }
  .hero { padding: 16px 20px; }
  .hero h1 { font-size: 22px; margin: 0 0 12px; color: #fff; line-height: 1.1; }
  .hero p { font-size: 15px; color: #aaa; line-height: 1.1; margin: 0 0 24px; }
  .search-dark {
    margin: 0 20px 16px; padding: 14px 12px; border-radius: 8px;
    background: #1e1e1e; color: #fff; border: 1px solid #333;
    font-size: 16px;
  }
  .category-dark {
    padding: 12px 20px; background: #181818; color: #fff;
    font-weight: 600; border-bottom: 1px solid #333;
  }
  .restaurant-card-dark {
    margin: 0 20px 16px; padding: 16px; border-radius: 12px;
    background: #1e1e1e; color: #fff; line-height: 1.1;
  }
  .restaurant-card-dark h2 { margin: 0 0 6px; font-size: 17px; color: #fff; }
  .restaurant-card-dark p { margin: 0; font-size: 13px; color: #bbb; line-height: 1.1; }
  .cta-dark {
    width: 100%; padding: 16px; font-size: 16px; font-weight: 600;
    background: #276ef1; color: #fff; border: none;
  }
  /* Bottom toggle bar */
  .toggle-bar {
    flex: 0 0 auto; display: flex; background: #121212;
    border-top: 1px solid #333; padding: 10px 4px;
  }
  .toggle-bar .tab {
    flex: 1 1 0; text-align: center; padding: 12px 4px;
    font-size: 14px; color: #999; font-family: inherit;
  }
  .toggle-bar .tab.active { color: #fff; font-weight: 600; }
</style>
<link rel="stylesheet" href="eink.css">
</head>
<body class="theme-dark" style="background: #121212; color: #ffffff;">
  <div class="app theme-dark">
    <div class="content theme-dark">
      <div class="header"><div class="logo">Uber Eats</div></div>
      <div class="hero">
        <h1 style="line-height: 1.1;">Order food you love</h1>
        <p style="line-height: 1.1;">Discover local restaurants and fast delivery.</p>
      </div>
      <div class="search-dark">Search restaurants or cuisines</div>
      <div class="category-dark">Popular near you</div>
      <div class="restaurant-card-dark" style="background: #1e1e1e;">
        <h2>Joe's Pizza</h2>
        <p style="line-height: 1.1;">20–35 min · $2.49 Delivery Fee · 4.7 (1,200)</p>
      </div>
      <div class="restaurant-card-dark" style="background: #1e1e1e;">
        <h2>Sakura Sushi</h2>
        <p style="line-height: 1.1;">15–25 min · $1.99 Delivery Fee · 4.8 (850)</p>
      </div>
      <div class="restaurant-card-dark" style="background: #1e1e1e;">
        <h2>Burger Joint</h2>
        <p style="line-height: 1.1;">25–40 min · $3.49 Delivery Fee · 4.5 (2,100)</p>
      </div>
      <div class="restaurant-card-dark" style="background: #1e1e1e;">
        <h2>Green Bowl</h2>
        <p style="line-height: 1.1;">10–20 min · $0.99 Delivery Fee · 4.9 (600)</p>
      </div>
      <div class="restaurant-card-dark" style="background: #1e1e1e;">
        <h2>Taco Place</h2>
        <p style="line-height: 1.1;">15–30 min · $2.99 Delivery Fee · 4.6 (1,500)</p>
      </div>
      <button class="cta-dark">Find more food</button>
      <div style="height: 40px;"></div>
    </div>
    <div class="toggle-bar">
      <div class="tab">Rides</div>
      <div class="tab active">Eats</div>
      <div class="tab">Settings</div>
    </div>
  </div>
</body>
</html>
        """.trimIndent()
        File(dir, "uber-eats-dark-fixture.html").writeText(eatsDarkFixture)

        // Rides dark-mode / squished-text fixture — exercises reported bug 5.
        // eink.css is placed last in <head> to mirror the app's JS injection order.
        val ridesDarkFixture = """
<!doctype html>
<html lang="en" data-theme="dark" color-scheme="dark" style="color-scheme: dark;">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Uber</title>
<style>
  * { box-sizing: border-box; }
  body {
    margin: 0; font-family: -apple-system, system-ui, sans-serif;
    background: #000000; color: #ffffff; line-height: 1.05;
  }
  .app { display: flex; flex-direction: column; height: 100vh; }
  .content { flex: 1 1 auto; overflow-y: auto; }
  .header { padding: 28px 20px 8px; }
  .logo { font-size: 28px; font-weight: 700; color: #fff; }
  .hero { padding: 16px 20px; }
  .hero h1 { font-size: 22px; margin: 0 0 12px; color: #fff; line-height: 1.05; }
  .hero p { font-size: 15px; color: #bbb; line-height: 1.05; margin: 0 0 24px; }
  .location-row {
    display: flex; align-items: center; gap: 12px;
    margin: 0 20px 16px; padding: 16px; border-radius: 12px;
    background: #1a1a1a; color: #fff;
  }
  .location-row p { margin: 0; font-size: 14px; line-height: 1.05; color: #fff; }
  .vehicle-option {
    margin: 0 20px 12px; padding: 16px; border-radius: 12px;
    background: #1a1a1a; color: #fff; line-height: 1.05;
  }
  .vehicle-option h3 { margin: 0 0 4px; font-size: 16px; color: #fff; }
  .vehicle-option p { margin: 0; font-size: 13px; color: #aaa; line-height: 1.05; }
  .estimate {
    margin: 0 20px 16px; padding: 14px 16px; border-radius: 8px;
    background: #262626; color: #fff; font-size: 14px; line-height: 1.05;
  }
  /* Bottom toggle bar */
  .toggle-bar {
    flex: 0 0 auto; display: flex; background: #121212;
    border-top: 1px solid #333; padding: 10px 4px;
  }
  .toggle-bar .tab {
    flex: 1 1 0; text-align: center; padding: 12px 4px;
    font-size: 14px; color: #999; font-family: inherit;
  }
  .toggle-bar .tab.active { color: #fff; font-weight: 600; }
</style>
<link rel="stylesheet" href="eink.css">
</head>
<body class="theme-dark" style="background: #000000; color: #ffffff;">
  <div class="app">
    <div class="content">
      <div class="header"><div class="logo">Uber</div></div>
      <div class="hero">
        <h1 style="line-height: 1.05;">Where to?</h1>
        <p style="line-height: 1.05;">Enter a destination to get a ride in minutes.</p>
      </div>
      <div class="location-row">
        <p style="line-height: 1.05;">Use current location</p>
      </div>
      <div class="vehicle-option">
        <h3 style="line-height: 1.05;">UberX</h3>
        <p style="line-height: 1.05;">Affordable, everyday rides · 3 min away</p>
      </div>
      <div class="vehicle-option">
        <h3 style="line-height: 1.05;">Comfort</h3>
        <p style="line-height: 1.05;">Newer cars with extra legroom · 5 min away</p>
      </div>
      <div class="estimate">
        Estimated trip: $12.34 · 15 min
      </div>
      <div class="vehicle-option">
        <h3 style="line-height: 1.05;">Black</h3>
        <p style="line-height: 1.05;">Premium rides with professional drivers · 8 min away</p>
      </div>
    </div>
    <div class="toggle-bar">
      <div class="tab active">Rides</div>
      <div class="tab">Eats</div>
      <div class="tab">Settings</div>
    </div>
  </div>
</body>
</html>
        """.trimIndent()
        File(dir, "uber-rides-dark-fixture.html").writeText(ridesDarkFixture)

        // Settings fixture mirrors preferences.xml structure.
        // eink.css is placed last in <head> to mirror the app's JS injection order.
        val settingsHtml = """
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Inkber Settings</title>
<style>
  * { box-sizing: border-box; }
  body { margin: 0; font-family: -apple-system, system-ui, sans-serif; background: #fff; color: #111; }
  .settings { padding: 20px 20px 40px; }
  .category {
    font-size: 13px; color: #666; font-weight: 600;
    padding: 28px 0 12px; border-bottom: 1px solid #eee;
  }
  .item {
    display: flex; align-items: center; padding: 18px 0;
    border-bottom: 1px solid #f0f0f0;
  }
  .item .text { flex: 1 1 auto; }
  .item .title { font-size: 16px; color: #111; }
  .item .summary { font-size: 12px; color: #888; padding-top: 4px; line-height: 1.4; }
  .toggle {
    flex: 0 0 auto; width: 44px; height: 24px; border-radius: 12px;
    background: #ccc; position: relative; margin-left: 12px;
  }
  .toggle.on { background: #000; }
  .toggle::after {
    content: ''; position: absolute; top: 2px; left: 2px;
    width: 20px; height: 20px; border-radius: 50%; background: #fff;
  }
  .toggle.on::after { left: 22px; }
  .seekbar {
    flex: 0 0 auto; width: 100px; height: 4px; background: #ddd;
    border-radius: 2px; margin-left: 12px; position: relative;
  }
  .seekbar::after {
    content: ''; position: absolute; top: -8px; left: 30%;
    width: 20px; height: 20px; border-radius: 50%; background: #000;
  }
  .info .summary { font-size: 13px; }
</style>
<link rel="stylesheet" href="eink.css">
</head>
<body>
  <div class="settings">
    <div class="category">Display (e-ink)</div>
    <div class="item">
      <div class="text">
        <div class="title">E-ink rendering optimisations</div>
        <div class="summary">Force light theme, disable animations, boost contrast and font size</div>
      </div>
      <div class="toggle on"></div>
    </div>
    <div class="item">
      <div class="text">
        <div class="title">Font size boost</div>
        <div class="summary">15%</div>
      </div>
      <div class="seekbar"></div>
    </div>

    <div class="category">Location</div>
    <div class="item info">
      <div class="text">
        <div class="title">Location</div>
        <div class="summary">Location sharing is controlled by Android's location permission. Grant it to share your GPS with Uber; deny it to keep location private.</div>
      </div>
    </div>

    <div class="category">Power</div>
    <div class="item">
      <div class="text">
        <div class="title">Keep screen on during trip</div>
        <div class="summary">Prevents the screen sleeping while a ride is in progress</div>
      </div>
      <div class="toggle"></div>
    </div>

    <div class="category">About Inkber</div>
    <div class="item info">
      <div class="text">
        <div class="title">Version</div>
        <div class="summary">0.1.5</div>
      </div>
    </div>
    <div class="item info">
      <div class="text">
        <div class="title">License</div>
        <div class="summary">MIT License, Copyright (c) 2026 Dan Cardamore</div>
      </div>
    </div>
    <div class="item info">
      <div class="text">
        <div class="title">Source</div>
        <div class="summary">github.com/dcardamo/inkber</div>
      </div>
    </div>
  </div>
</body>
</html>
        """.trimIndent()
        File(dir, "settings-fixture.html").writeText(settingsHtml)

        // Dynamic SPA fixture — mimics Uber's React app: shows a loader, then
        // renders content via JS after a delay. This is used by instrumented
        // tests to verify our CSS/JS injection does not hang the page.
        val dynamicSpa = """
<!doctype html>
<html lang="en" data-theme="dark" color-scheme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Uber SPA</title>
<style>
  * { box-sizing: border-box; }
  body {
    margin: 0; font-family: -apple-system, system-ui, sans-serif;
    background: #000000; color: #ffffff; line-height: 1.1;
  }
  .app { display: flex; flex-direction: column; height: 100vh; }
  .content { flex: 1 1 auto; overflow-y: auto; padding: 20px; }
  .spinner {
    width: 40px; height: 40px; margin: 40px auto;
    border: 4px solid #333; border-top-color: #fff; border-radius: 50%;
    animation: spin 1s linear infinite;
  }
  @keyframes spin { to { transform: rotate(360deg); } }
  .loaded h1 { font-size: 24px; margin: 0 0 16px; color: #fff; line-height: 1.1; }
  .loaded p { font-size: 15px; color: #aaa; line-height: 1.1; }
  .card {
    margin: 16px 0; padding: 16px; border-radius: 12px;
    background: #1a1a1a; color: #fff;
  }
</style>
<link rel="stylesheet" href="eink.css">
</head>
<body class="theme-dark">
  <div class="app">
    <div class="content" id="root">
      <div class="spinner" id="loader"></div>
    </div>
  </div>
  <script>
    // Mimic Uber's SPA: render content after a short delay.
    setTimeout(function() {
      var root = document.getElementById('root');
      root.innerHTML =
        '<div class="loaded">' +
        '<h1>Where to?</h1>' +
        '<p>Enter a destination to get a ride in minutes.</p>' +
        '<div class="card">UberX · 3 min away</div>' +
        '<div class="card">Comfort · 5 min away</div>' +
        '</div>';
      window.dispatchEvent(new CustomEvent('spa:loaded'));
      try {
        if (typeof InkberTest !== 'undefined') {
          InkberTest.onSpaLoaded();
        }
      } catch(e) {}
    }, 800);
  </script>
</body>
</html>
        """.trimIndent()
        File(dir, "uber-spa-fixture.html").writeText(dynamicSpa)

        // Copy dynamic SPA fixture and CSS into androidTest assets.
        File(assetsDir, "uber-spa-fixture.html").writeText(dynamicSpa)
    }
}