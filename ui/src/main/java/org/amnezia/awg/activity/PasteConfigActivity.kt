// ui/src/main/java/org/amnezia/awg/activity/PasteConfigActivity.kt
package org.amnezia.awg.activity

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputFilter
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
import org.amnezia.awg.model.ObservableTunnel
import org.amnezia.awg.util.DecodedShare
import org.amnezia.awg.util.ErrorMessages
import org.amnezia.awg.util.NameError
import org.amnezia.awg.util.NameValidator
import org.amnezia.awg.util.ShareStringDecoder
import org.amnezia.awg.widget.NameInputFilter

class PasteConfigActivity : AppCompatActivity() {
    private var decoded: DecodedShare? = null
    private var validationToken = 0
    private var nameEditedByUser = false
    private var duplicateOf: ObservableTunnel? = null

    private lateinit var input: TextInputEditText
    private lateinit var inputLayout: TextInputLayout
    private lateinit var pasteClipboardButton: MaterialButton
    private lateinit var validation: TextView
    private lateinit var dupCard: View
    private lateinit var dupDetail: TextView
    private lateinit var dupOpen: MaterialButton
    private lateinit var nameLabel: TextView
    private lateinit var nameLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var nameHint: TextView
    private lateinit var previewCard: MaterialCardView
    private lateinit var previewEndpoint: TextView
    private lateinit var previewObfuscation: TextView
    private lateinit var addButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paste_config)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        input = findViewById(R.id.string_input)
        inputLayout = findViewById(R.id.string_input_layout)
        pasteClipboardButton = findViewById(R.id.paste_clipboard_button)
        validation = findViewById(R.id.validation_text)
        dupCard = findViewById(R.id.dup_card)
        dupDetail = findViewById(R.id.dup_detail)
        dupOpen = findViewById(R.id.dup_open)
        nameLabel = findViewById(R.id.name_label)
        nameLayout = findViewById(R.id.name_layout)
        nameInput = findViewById(R.id.name_input)
        nameHint = findViewById(R.id.name_hint)
        previewCard = findViewById(R.id.preview_card)
        previewEndpoint = findViewById(R.id.preview_endpoint)
        previewObfuscation = findViewById(R.id.preview_obfuscation)
        addButton = findViewById(R.id.add_button)
        emphasizeAmnezia(findViewById(R.id.trust_warning_text))

        // The config-string field never shows an error icon (it carries the paste end-icon);
        // duplicates only recolor its stroke.
        inputLayout.errorIconDrawable = null
        nameInput.filters = arrayOf<InputFilter>(NameInputFilter.newInstance())

        input.doAfterTextChanged { validate(it?.toString().orEmpty()) }
        inputLayout.setEndIconOnClickListener { pasteFromClipboard() }
        pasteClipboardButton.setOnClickListener { pasteFromClipboardButton() }
        nameInput.doAfterTextChanged {
            if (nameInput.hasFocus()) nameEditedByUser = true
            updateAddEnabled()
        }
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
        duplicateOf = null
        inputLayout.error = null
        validation.visibility = View.GONE
        previewCard.visibility = View.GONE
        dupCard.visibility = View.GONE
        setNameFieldVisible(false)
        addButton.isEnabled = false
    }

    private fun setInvalid() {
        inputLayout.error = null
        validation.visibility = View.VISIBLE
        validation.text = getString(R.string.paste_invalid)
        val err = MaterialColors.getColor(validation, com.google.android.material.R.attr.colorError)
        validation.setTextColor(err)
        validation.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_error, 0, 0, 0)
        previewCard.visibility = View.GONE
        dupCard.visibility = View.GONE
        setNameFieldVisible(false)
        addButton.isEnabled = false
    }

    private fun setValid(d: DecodedShare) {
        duplicateOf = null
        inputLayout.error = null
        dupCard.visibility = View.GONE
        validation.visibility = View.VISIBLE
        validation.text = getString(R.string.paste_valid, d.name)
        val ok = MaterialColors.getColor(validation, com.google.android.material.R.attr.colorPrimary)
        validation.setTextColor(ok)
        validation.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0)
        previewEndpoint.text = d.endpoint ?: "—"
        previewObfuscation.text = d.obfuscation
        previewCard.visibility = View.VISIBLE
        setNameFieldVisible(true)
        if (!nameEditedByUser) nameInput.setText(d.name)
        updateAddEnabled()
    }

    private fun setNameFieldVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        nameLabel.visibility = v
        nameLayout.visibility = v
        nameHint.visibility = v
    }

    private fun currentName(): String = nameInput.text?.toString()?.trim().orEmpty()

    private fun updateAddEnabled() {
        if (decoded == null || duplicateOf != null) { addButton.isEnabled = false; return }
        val err = when (NameValidator.validate(currentName())) {
            NameError.EMPTY -> getString(R.string.paste_name_empty)
            NameError.BAD_CHARS -> getString(R.string.paste_name_invalid_chars)
            NameError.TOO_LONG -> getString(R.string.paste_name_too_long)
            null -> null
        }
        nameLayout.error = err
        if (err != null) { addButton.isEnabled = false; return }
        // Async name-collision check; the field carries the verdict, the button follows it.
        val token = validationToken
        val candidate = currentName()
        lifecycleScope.launch {
            val taken = runCatching { Application.getTunnelManager().exists(candidate) }.getOrDefault(false)
            if (token == validationToken && decoded != null && duplicateOf == null && currentName() == candidate) {
                nameLayout.error = if (taken) getString(R.string.paste_name_duplicate, candidate) else null
                addButton.isEnabled = nameLayout.error == null
            }
        }
        addButton.isEnabled = true
    }

    private fun checkForDuplicate(d: DecodedShare, token: Int) {
        lifecycleScope.launch {
            val existing = runCatching { Application.getTunnelManager().findMatchingTunnel(d.config) }.getOrNull()
            // Apply only if this is still the current input and it is still the valid state.
            if (token == validationToken && decoded === d && existing != null) {
                setDuplicate(existing)
            }
        }
    }

    private fun setDuplicate(existing: ObservableTunnel) {
        duplicateOf = existing
        validation.visibility = View.VISIBLE
        validation.text = getString(R.string.dup_config_inline)
        val err = MaterialColors.getColor(validation, com.google.android.material.R.attr.colorError)
        validation.setTextColor(err)
        validation.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_error, 0, 0, 0)
        inputLayout.boxStrokeErrorColor = ColorStateList.valueOf(err)
        inputLayout.error = " "                       // recolor the stroke; the message lives above
        dupDetail.text = boldName(getString(R.string.dup_config_detail, existing.name), existing.name)
        dupOpen.text = getString(R.string.dup_open_named, existing.name)
        dupOpen.setOnClickListener { openExisting(existing) }
        dupCard.visibility = View.VISIBLE
        setNameFieldVisible(false)                    // rename can't fix a content duplicate
        previewCard.visibility = View.VISIBLE
        addButton.isEnabled = false
    }

    private fun openExisting(tunnel: ObservableTunnel) {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.INTENT_EXTRA_OPEN_TUNNEL, tunnel.name)
        })
        finish()
    }

    private fun commit() {
        val d = decoded ?: return
        addButton.isEnabled = false
        val name = currentName()
        lifecycleScope.launch {
            try {
                Application.getTunnelManager().create(name, d.config)
                input.text?.clear()                       // security: clear the credential field
                finish()
            } catch (e: Throwable) {
                addButton.isEnabled = true
                Snackbar.make(findViewById(android.R.id.content), ErrorMessages[e], Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun pasteFromClipboardButton() {
        val text = clipboardText()?.trim()
        if (text.isNullOrEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), R.string.paste_clipboard_empty, Snackbar.LENGTH_SHORT).show()
        } else {
            input.setText(text)
            input.setSelection(text.length)
        }
    }

    private fun pasteFromClipboard() { clipboardText()?.let { input.setText(it.trim()) } }

    private fun clipboardText(): String? {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
    }

    /** Bold the interpolated tunnel name inside the duplicate-card detail sentence. */
    private fun boldName(text: String, name: String): CharSequence {
        val i = text.indexOf(name)
        if (i < 0) return text
        val sp = SpannableString(text)
        sp.setSpan(StyleSpan(Typeface.BOLD), i, i + name.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return sp
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
