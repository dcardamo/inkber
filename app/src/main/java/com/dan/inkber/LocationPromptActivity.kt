package com.dan.inkber

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Full-screen activity for the first-launch location opt-in prompt.
 *
 * Uses a separate activity instead of AlertDialog because AlertDialog windows
 * don't get a rendering surface on some devices (emulators with swiftshader,
 * and reportedly on the Mudita Kompakt's e-ink panel).
 *
 * Result: RESULT_OK if user granted location, RESULT_CANCELED otherwise.
 * The prompt state is persisted in Prefs by this activity.
 */
class LocationPromptActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.of(this)
        prefs.locationPromptState = Prefs.PROMPT_SHOWN_AWAITING

        val ctx = this
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(48, 120, 48, 48)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        root.addView(TextView(ctx).apply {
            text = getString(R.string.location_dialog_title)
            textSize = 22f
            setTextColor(0xFF111111.toInt())
            setPadding(0, 0, 0, 32)
        })

        root.addView(TextView(ctx).apply {
            text = getString(R.string.location_dialog_message)
            textSize = 15f
            setTextColor(0xFF333333.toInt())
            setPadding(0, 0, 0, 48)
            setLineSpacing(6f, 1f)
        })

        fun addButton(text: String, bg: Int, fg: Int, onClick: () -> Unit) {
            root.addView(Button(ctx).apply {
                this.text = text
                setTextColor(fg)
                setBackgroundColor(bg)
                textSize = 16f
                setPadding(24, 40, 24, 40)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 16
                layoutParams = lp
                setOnClickListener { onClick() }
            })
        }

        val requestPermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    setResult(RESULT_OK)
                } else {
                    prefs.locationPromptState = Prefs.PROMPT_NEVER_ASK
                    setResult(RESULT_CANCELED)
                }
                finish()
            }

        addButton(getString(R.string.location_dialog_allow),
            0xFF111111.toInt(), 0xFFFFFFFF.toInt()) {
            requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        addButton(getString(R.string.location_dialog_not_now),
            0xFFFFFFFF.toInt(), 0xFF111111.toInt()) {
            prefs.locationPromptState = Prefs.PROMPT_NOT_SHOWN
            setResult(RESULT_CANCELED)
            finish()
        }

        addButton(getString(R.string.location_dialog_never),
            0xFFFFFFFF.toInt(), 0xFF111111.toInt()) {
            prefs.locationPromptState = Prefs.PROMPT_NEVER_ASK
            setResult(RESULT_CANCELED)
            finish()
        }

        setContentView(root)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        /** Test-only intent. Production users always see the opt-in prompt. */
        fun intentForTesting(context: Context): Intent {
            return Intent(context, LocationPromptActivity::class.java)
        }
    }
}