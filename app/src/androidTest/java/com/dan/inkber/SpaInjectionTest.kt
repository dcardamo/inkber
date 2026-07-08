package com.dan.inkber

import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Instrumented test that verifies the e-ink injection does not hang a dynamic
 * single-page app. We launch MainActivity with a local SPA fixture that shows a
 * loader for 800ms, then renders content. The test waits for a custom
 * JavaScript event and asserts the content is visible.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class SpaInjectionTest {

    @After
    fun tearDown() {
        scenario?.close()
    }

    private var scenario: ActivityScenario<MainActivity>? = null

    @Test
    fun spaRendersContentAfterInjection() {
        val intent = android.content.Intent(
            ApplicationProvider.getApplicationContext(),
            MainActivity::class.java
        ).apply {
            putExtra(MainActivity.EXTRA_SKIP_LOCATION_PROMPT, true)
            putExtra(
                MainActivity.EXTRA_TEST_URL_RIDES,
                "file:///android_asset/fixtures/uber-spa-fixture.html"
            )
            putExtra(
                MainActivity.EXTRA_TEST_URL_EATS,
                "file:///android_asset/fixtures/uber-spa-fixture.html"
            )
        }

        scenario = ActivityScenario.launch(intent)

        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<String>()

        scenario!!.onActivity { activity ->
            val webView = activity.findViewById<android.widget.FrameLayout>(R.id.webview_container)
                .findViewWithTag<WebView>("rides")
            webView.addJavascriptInterface(
                object {
                    @android.webkit.JavascriptInterface
                    fun onSpaLoaded() {
                        webView.evaluateJavascript(
                            """
                            (function(){
                              var h1 = document.querySelector('h1');
                              var cards = document.querySelectorAll('.card');
                              var bg = window.getComputedStyle(document.body).backgroundColor;
                              return JSON.stringify({
                                loaded: true,
                                h1: h1 ? h1.textContent : null,
                                cards: cards.length,
                                bg: bg
                              });
                            })()
                            """.trimIndent()
                        ) { result ->
                            resultRef.set(result)
                            latch.countDown()
                        }
                    }
                },
                "InkberTest"
            )
        }

        assertTrue("SPA did not finish loading within 15s", latch.await(15, TimeUnit.SECONDS))
        val result = resultRef.get() ?: ""
        assertTrue("Expected h1 'Where to?'", result.contains("\"h1\":\"Where to?\""))
        assertTrue("Expected 2 cards", result.contains("\"cards\":2"))
        assertTrue("Expected light background", result.contains("\"bg\":\"rgb(255, 255, 255)\""))
    }
}
