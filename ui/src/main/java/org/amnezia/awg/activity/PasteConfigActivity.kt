// ui/src/main/java/org/amnezia/awg/activity/PasteConfigActivity.kt
package org.amnezia.awg.activity

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import org.amnezia.awg.Application
import org.amnezia.awg.R
import org.amnezia.awg.util.DecodedShare
import org.amnezia.awg.util.ErrorMessages
import org.amnezia.awg.util.ShareStringDecoder

class PasteConfigActivity : AppCompatActivity() {
    private var decoded: DecodedShare? = null
    private var validationToken = 0

    private lateinit var input: TextInputEditText
    private lateinit var inputLayout: TextInputLayout
    private lateinit var validation: TextView
    private lateinit var previewCard: MaterialCardView
    private lateinit var previewName: TextView
    private lateinit var previewEndpoint: TextView
    private lateinit var previewObfuscation: TextView
    private lateinit var addButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paste_config)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        input = findViewById(R.id.string_input)
        inputLayout = findViewById(R.id.string_input_layout)
        validation = findViewById(R.id.validation_text)
        previewCard = findViewById(R.id.preview_card)
        previewName = findViewById(R.id.preview_name)
        previewEndpoint = findViewById(R.id.preview_endpoint)
        previewObfuscation = findViewById(R.id.preview_obfuscation)
        addButton = findViewById(R.id.add_button)
        emphasizeAmnezia(findViewById(R.id.trust_warning_text))

        input.doAfterTextChanged { validate(it?.toString().orEmpty()) }
        inputLayout.setEndIconOnClickListener { pasteFromClipboard() }
        addButton.setOnClickListener { commit() }

        // Entry (a): deep link awg://...  Entry (b): clipboard auto-fill if it already holds one.
        val deepLink = intent?.data?.toString()
        when {
            deepLink != null && deepLink.startsWith("awg://") -> {
                input.setText(deepLink)
                intent.data = null          // credential hygiene: clear from Activity Intent / recents
            }
            else -> clipboardText()?.takeIf { it.trim().startsWith("awg://") }?.let { input.setText(it.trim()) }
        }
    }

    private fun validate(raw: String) {
        validationToken++
        if (raw.isBlank()) { setNeutral(); return }
        try {
            val d = ShareStringDecoder.decode(raw)
            decoded = d
            setValid(d)
            checkForDuplicate(d, validationToken)
        } catch (e: Throwable) {
            decoded = null
            setInvalid()
        }
    }

    private fun setNeutral() {
        decoded = null
        validation.visibility = View.GONE
        previewCard.visibility = View.GONE
        addButton.isEnabled = false
    }

    private fun setInvalid() {
        validation.visibility = View.VISIBLE
        validation.text = getString(R.string.paste_invalid)
        val err = MaterialColors.getColor(validation, com.google.android.material.R.attr.colorError)
        validation.setTextColor(err)
        validation.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_warning_triangle, 0, 0, 0)
        previewCard.visibility = View.GONE
        addButton.isEnabled = false
    }

    private fun setValid(d: DecodedShare) {
        validation.visibility = View.VISIBLE
        validation.text = getString(R.string.paste_valid, d.name)
        val ok = MaterialColors.getColor(validation, com.google.android.material.R.attr.colorPrimary)
        validation.setTextColor(ok)
        validation.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0)
        previewName.text = d.name
        previewEndpoint.text = d.endpoint ?: "—"
        previewObfuscation.text = d.obfuscation
        previewCard.visibility = View.VISIBLE
        addButton.isEnabled = true
    }

    private fun checkForDuplicate(d: DecodedShare, token: Int) {
        lifecycleScope.launch {
            val existing = runCatching { Application.getTunnelManager().findMatchingTunnel(d.config) }.getOrNull()
            // Apply only if this is still the current input and it is still the valid state.
            if (token == validationToken && decoded === d && existing != null) {
                setDuplicate(existing.name)
            }
        }
    }

    private fun setDuplicate(existingName: String) {
        validation.visibility = View.VISIBLE
        validation.text = getString(R.string.paste_duplicate, existingName)
        val err = MaterialColors.getColor(validation, com.google.android.material.R.attr.colorError)
        validation.setTextColor(err)
        validation.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_warning_triangle, 0, 0, 0)
        previewCard.visibility = View.VISIBLE   // keep the decoded preview visible
        addButton.isEnabled = false             // block Add for the duplicate
    }

    private fun commit() {
        val d = decoded ?: return
        addButton.isEnabled = false
        lifecycleScope.launch {
            try {
                Application.getTunnelManager().create(d.name, d.config)
                input.text?.clear()                       // security: clear the credential field
                finish()
            } catch (e: Throwable) {
                addButton.isEnabled = true
                Snackbar.make(findViewById(android.R.id.content), ErrorMessages[e], Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun pasteFromClipboard() { clipboardText()?.let { input.setText(it.trim()) } }

    private fun clipboardText(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
    }

    private fun emphasizeAmnezia(tv: TextView) {
        val full = getString(R.string.add_trust_warning)
        val mark = "amnezia.org"
        val i = full.indexOf(mark)
        if (i < 0) return
        val sp = SpannableString(full)
        val onSurface = MaterialColors.getColor(tv, com.google.android.material.R.attr.colorOnSurface)
        sp.setSpan(ForegroundColorSpan(onSurface), i, i + mark.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(StyleSpan(Typeface.BOLD), i, i + mark.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        tv.text = sp
    }
}
