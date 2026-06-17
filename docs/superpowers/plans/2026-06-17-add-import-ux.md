# Add/Import UX — Clipboard, Rename-on-Import, Dedup Errors & ASCII Names — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Round out the tunnel-add flows: a dedicated "Paste from clipboard" button, an editable tunnel-name field on the paste screen, design-consistent duplicate-config error treatment across all three add methods (paste / manual / QR), an ASCII-only name guard that fixes the silent Cyrillic failure, and a pass restyling the remaining generic error surfaces into the Network-Teal language.

**Architecture:** All three add entry points already funnel through `TunnelManager.create()`, which validates name + name-collision + config-duplicate and throws. We keep that single source of truth and improve the *presentation* per entry point (SPEC-add-tunnel §C severity ladder: paste = inline soft card, manual = inline field error, QR = interrupting dialog). The Cyrillic bug is fixed at its root by tightening the shared `NameInputFilter` to ASCII, plus proactive name validation that gates the action button before commit. Duplicate detection reuses the existing `TunnelManager.findMatchingTunnel()` / `sameTunnelIdentity()`.

**Tech Stack:** Kotlin, Android Views + Material Components 1.11.0 (XML layouts, view binding / `findViewById`), Coroutines, JUnit (JVM unit tests in `ui/src/test` and `tunnel/src/test`).

## Global Constraints

- minSdk 24, no Compose. Material 3 XML components only. (README)
- Design system: Network Teal. Error treatment uses the `error` color role (`colorError` `#BA1A1A` light / `#FFB4AB` dark) + an alert-circle glyph; never the loud `errorContainer` block — keep cards/inline rows tonal and calm. (SPEC-add-tunnel §C)
- Tunnel names: `[a-zA-Z0-9_=+.-]{1,15}`, max 15 chars (`Tunnel.NAME_PATTERN` / `NAME_MAX_LENGTH`). ASCII only — no Unicode letters.
- Spacing/radius tokens only: `@dimen/space_xs..xl` (4/8/12/16/24), `@dimen/radius_input` (12dp), `@dimen/radius_card` (16dp). No hard-coded dp where a token exists.
- Duplicate-config identity = first peer public key + endpoint (`sameTunnelIdentity`). Name collisions are separate and handled by the name field.
- Duplicate policy is **reject** (the app's `create()` throws on collision) — surface `paste_name_duplicate`, do NOT auto-suffix. (INTEGRATION §2c note)
- New user-facing strings live in `ui/src/main/res/values/strings-awg-add.xml` (English) following the existing file's style.

---

## File Structure

**Shared / foundation**
- `ui/src/main/java/org/amnezia/awg/widget/NameInputFilter.kt` — tighten `isAllowed` to ASCII (root fix), expose predicate for test.
- `ui/src/main/res/drawable/ic_error.xml` — NEW alert-circle glyph (calm error icon).
- `ui/src/main/res/values/colors.xml` + `values-night/colors.xml` — NEW `awg_error_fill` / `awg_error_stroke` for the soft dup card.
- `ui/src/main/res/values/strings-awg-add.xml` — NEW strings (name field, dup messages, invalid-chars, QR dialog).
- `ui/src/main/res/values/styles*.xml` (or `styles-awg-buttons.xml`) — NEW tonal-button + soft-error-card + Snackbar style.

**Name validation helper**
- `ui/src/main/java/org/amnezia/awg/util/NameValidator.kt` — NEW pure helper returning a localized error key for a candidate name (empty / bad-chars / too-long). JVM-testable.

**Paste screen**
- `ui/src/main/res/layout/activity_paste_config.xml` — clipboard button + editable name field + soft dup card.
- `ui/src/main/java/org/amnezia/awg/activity/PasteConfigActivity.kt` — wire clipboard button, name field (pre-fill/stop-overwrite/validate), dup soft card + Open action.

**Manual editor**
- `ui/src/main/java/org/amnezia/awg/fragment/TunnelEditorFragment.kt` — inline Name-field error for invalid name + dup config (replace generic snackbar for the create path).

**QR**
- `ui/src/main/java/org/amnezia/awg/util/TunnelImporter.kt` — dup pre-check before naming dialog; raise interrupting dialog.
- `ui/src/main/java/org/amnezia/awg/fragment/ConfigNamingDialogFragment.kt` — proactive ASCII/name validation gating the Create button + ic_error glyph on the field error.

**TunnelManager**
- `ui/src/main/java/org/amnezia/awg/model/TunnelManager.kt` — add `suspend fun exists(name): Boolean` for proactive name-collision checks.

**Error restyle**
- Theme Snackbar style applied app-wide (foundation task) so remaining `ErrorMessages[e]` snackbars match the design.

---

## Task 1: ASCII-only name filter (Cyrillic root fix)

**Files:**
- Modify: `ui/src/main/java/org/amnezia/awg/widget/NameInputFilter.kt`
- Test: `ui/src/test/java/org/amnezia/awg/widget/NameInputFilterTest.kt`

**Interfaces:**
- Produces: `NameInputFilter.isAllowed(c: Char): Boolean` (make `internal`, ASCII-only) — used only by the filter + this test.

The bug: `isAllowed` uses `Character.isLetterOrDigit(c)`, which is Unicode-aware and returns `true` for Cyrillic (`'я'`). Cyrillic is typed in, then `Tunnel.isNameInvalid` (ASCII regex) rejects it at `create()` time. Restrict to ASCII so it can never be entered.

- [ ] **Step 1: Write the failing test**

```kotlin
// ui/src/test/java/org/amnezia/awg/widget/NameInputFilterTest.kt
package org.amnezia.awg.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameInputFilterTest {
    @Test fun asciiLettersAndDigitsAllowed() {
        for (c in "abcXYZ0189") assertTrue("$c should be allowed", NameInputFilter.isAllowed(c))
    }

    @Test fun symbolSubsetAllowed() {
        for (c in "_=+.-") assertTrue("$c should be allowed", NameInputFilter.isAllowed(c))
    }

    @Test fun cyrillicRejected() {
        for (c in "тунельЯ") assertFalse("$c must be rejected", NameInputFilter.isAllowed(c))
    }

    @Test fun spaceAndOtherSymbolsRejected() {
        for (c in " /\\@#") assertFalse("$c must be rejected", NameInputFilter.isAllowed(c))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :ui:testDebugUnitTest --tests "org.amnezia.awg.widget.NameInputFilterTest"`
Expected: FAIL — `cyrillicRejected` fails (`Character.isLetterOrDigit('я')` is true), and `isAllowed` is currently `private` so it won't compile from the test (compile error is an acceptable "fail").

- [ ] **Step 3: Tighten and expose the predicate**

In `NameInputFilter.kt`, replace the companion `isAllowed` so it is `internal` and ASCII-only:

```kotlin
    companion object {
        internal fun isAllowed(c: Char) =
            c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "_=+.-"

        @JvmStatic
        fun newInstance() = NameInputFilter()
    }
```

Leave the `filter(...)` body unchanged — it already calls `isAllowed(c)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :ui:testDebugUnitTest --tests "org.amnezia.awg.widget.NameInputFilterTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/java/org/amnezia/awg/widget/NameInputFilter.kt ui/src/test/java/org/amnezia/awg/widget/NameInputFilterTest.kt
git commit -m "fix(add): restrict tunnel-name input filter to ASCII (Cyrillic no longer silently fails)"
```

---

## Task 2: Name-validation helper

**Files:**
- Create: `ui/src/main/java/org/amnezia/awg/util/NameValidator.kt`
- Test: `ui/src/test/java/org/amnezia/awg/util/NameValidatorTest.kt`

**Interfaces:**
- Produces:
  - `enum class NameError { EMPTY, BAD_CHARS, TOO_LONG }`
  - `object NameValidator { fun validate(name: String): NameError? }` — returns `null` when valid, else the first problem. Used by PasteConfigActivity (Task 6), TunnelEditorFragment (Task 8), ConfigNamingDialogFragment (Task 9). Callers map `NameError` → string resource.

Pure logic (no Android deps) so it is JVM-testable and shared. Mirrors `Tunnel.NAME_PATTERN` / `NAME_MAX_LENGTH` but distinguishes *why* it's invalid so the UI can show a helpful message instead of a flat "Invalid name".

- [ ] **Step 1: Write the failing test**

```kotlin
// ui/src/test/java/org/amnezia/awg/util/NameValidatorTest.kt
package org.amnezia.awg.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NameValidatorTest {
    @Test fun validName() = assertNull(NameValidator.validate("awg-fi-01"))
    @Test fun empty() = assertEquals(NameError.EMPTY, NameValidator.validate(""))
    @Test fun blankIsEmpty() = assertEquals(NameError.EMPTY, NameValidator.validate("   "))
    @Test fun cyrillicIsBadChars() = assertEquals(NameError.BAD_CHARS, NameValidator.validate("тунель"))
    @Test fun spaceIsBadChars() = assertEquals(NameError.BAD_CHARS, NameValidator.validate("my tunnel"))
    @Test fun tooLong() = assertEquals(NameError.TOO_LONG, NameValidator.validate("0123456789abcdef")) // 16 chars
    @Test fun maxLengthOk() = assertNull(NameValidator.validate("0123456789abcde"))                    // 15 chars
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :ui:testDebugUnitTest --tests "org.amnezia.awg.util.NameValidatorTest"`
Expected: FAIL — `NameValidator` / `NameError` unresolved.

- [ ] **Step 3: Implement**

```kotlin
// ui/src/main/java/org/amnezia/awg/util/NameValidator.kt
package org.amnezia.awg.util

import org.amnezia.awg.backend.Tunnel

enum class NameError { EMPTY, BAD_CHARS, TOO_LONG }

object NameValidator {
    private val ALLOWED = Regex("[a-zA-Z0-9_=+.-]+")

    /** Returns the first problem with [name], or null if it is a valid tunnel name. */
    fun validate(name: String): NameError? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> NameError.EMPTY
            !ALLOWED.matches(trimmed) -> NameError.BAD_CHARS
            trimmed.length > Tunnel.NAME_MAX_LENGTH -> NameError.TOO_LONG
            else -> null
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :ui:testDebugUnitTest --tests "org.amnezia.awg.util.NameValidatorTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/java/org/amnezia/awg/util/NameValidator.kt ui/src/test/java/org/amnezia/awg/util/NameValidatorTest.kt
git commit -m "feat(add): add NameValidator helper distinguishing empty/bad-chars/too-long"
```

---

## Task 3: Shared resources (drawable, colors, strings, styles)

**Files:**
- Create: `ui/src/main/res/drawable/ic_error.xml`
- Modify: `ui/src/main/res/values/colors.xml`, `ui/src/main/res/values-night/colors.xml`
- Modify: `ui/src/main/res/values/strings-awg-add.xml`
- Modify: `ui/src/main/res/values/styles-awg-buttons.xml` (tonal button) and `ui/src/main/res/values/styles.xml` (snackbar theme attr)

**Interfaces:**
- Produces (referenced by Tasks 5–9):
  - `@drawable/ic_error` (alert-circle, tintable via `?attr/colorError`)
  - `@color/awg_error_fill`, `@color/awg_error_stroke`
  - strings: `paste_name_label`, `paste_name_hint`, `paste_name_empty`, `paste_name_invalid_chars`, `paste_name_too_long`, `paste_name_duplicate`, `paste_clipboard_empty`, `dup_config_inline`, `dup_config_detail`, `dup_open_named`, `dup_name_inline`, `dup_dialog_title`, `dup_dialog_body_qr`, `dup_dialog_open`, `dup_dialog_scan_again`
  - style: `Widget.Awg.Button.Tonal`

This is pure resource scaffolding consumed by later tasks; no test cycle of its own beyond a compile.

- [ ] **Step 1: Add the alert-circle drawable**

```xml
<!-- ui/src/main/res/drawable/ic_error.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorError">
    <path android:fillColor="@android:color/white"
        android:pathData="M12,2A10,10 0 1,0 22,12A10,10 0 0,0 12,2ZM12,20A8,8 0 1,1 20,12A8,8 0 0,1 12,20ZM11,7H13V13H11V7ZM11,15H13V17H11V15Z" />
</vector>
```

- [ ] **Step 2: Add dup-card colors**

In `ui/src/main/res/values/colors.xml` (light) add:

```xml
    <color name="awg_error_fill">#FCEEEE</color>
    <color name="awg_error_stroke">#E6B4B0</color>
```

In `ui/src/main/res/values-night/colors.xml` (dark) add:

```xml
    <color name="awg_error_fill">#241619</color>
    <color name="awg_error_stroke">#5C2B2E</color>
```

- [ ] **Step 3: Add strings**

Append to `ui/src/main/res/values/strings-awg-add.xml` before `</resources>`:

```xml
    <!-- Clipboard -->
    <string name="paste_clipboard_empty">Clipboard is empty or has no text.</string>

    <!-- Editable tunnel name (rename-on-import) -->
    <string name="paste_name_label">Tunnel name</string>
    <string name="paste_name_hint">Rename it now, or keep the server\'s default.</string>
    <string name="paste_name_empty">Enter a name for this tunnel.</string>
    <string name="paste_name_invalid_chars">Use only letters, numbers, and _ - . = + (no spaces or other characters).</string>
    <string name="paste_name_too_long">Keep the name to 15 characters or fewer.</string>
    <string name="paste_name_duplicate">A tunnel named \"%1$s\" already exists.</string>

    <!-- Duplicate config — inline (paste) -->
    <string name="dup_config_inline">This config is already added.</string>
    <string name="dup_config_detail">You already have this tunnel saved as \"%1$s\".</string>
    <string name="dup_open_named">Open \"%1$s\"</string>

    <!-- Duplicate config — manual editor field error -->
    <string name="dup_name_inline">This config is already added as \"%1$s\".</string>

    <!-- Duplicate config — QR interrupting dialog -->
    <string name="dup_dialog_title">Already added</string>
    <string name="dup_dialog_body_qr">This QR code matches \"%1$s\", a tunnel you already have.</string>
    <string name="dup_dialog_open">Open tunnel</string>
    <string name="dup_dialog_scan_again">Scan again</string>
```

- [ ] **Step 4: Add the tonal button style**

Append to `ui/src/main/res/values/styles-awg-buttons.xml` (inside `<resources>`):

```xml
    <!-- Full-width "Paste from clipboard" affordance: primaryContainer fill, primary icon/text. -->
    <style name="Widget.Awg.Button.Tonal" parent="Widget.Material3.Button.TonalButton">
        <item name="cornerRadius">@dimen/radius_input</item>
        <item name="iconGravity">textStart</item>
        <item name="iconPadding">@dimen/space_sm</item>
    </style>
```

- [ ] **Step 5: Verify resources compile**

Run: `./gradlew :ui:assembleDebug -q`
Expected: BUILD SUCCESSFUL (no missing-resource / AAPT errors). If the build is slow, `./gradlew :ui:processDebugResources -q` is enough to validate resources.

- [ ] **Step 6: Commit**

```bash
git add ui/src/main/res/drawable/ic_error.xml ui/src/main/res/values/colors.xml ui/src/main/res/values-night/colors.xml ui/src/main/res/values/strings-awg-add.xml ui/src/main/res/values/styles-awg-buttons.xml
git commit -m "feat(add): shared resources for clipboard/rename/dedup error UI"
```

---

## Task 4: TunnelManager.exists() for proactive name-collision checks

**Files:**
- Modify: `ui/src/main/java/org/amnezia/awg/model/TunnelManager.kt`

**Interfaces:**
- Produces: `suspend fun TunnelManager.exists(name: String): Boolean` — true if a tunnel with that exact name already exists. Used by PasteConfigActivity (Task 6) and TunnelEditorFragment (Task 8) to gate the action button before commit. Mirrors the private `tunnelMap.containsKey(name)` guard already used inside `create()`.

This is a thin accessor; no standalone unit test (it wraps `getTunnels()` which needs the full app graph). It is exercised through the UI tasks' manual verification.

- [ ] **Step 1: Add the method**

Add to `TunnelManager`, next to `findMatchingTunnel` (around line 56):

```kotlin
    suspend fun exists(name: String): Boolean = withContext(Dispatchers.Main.immediate) {
        getTunnels().containsKey(name)
    }
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :ui:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add ui/src/main/java/org/amnezia/awg/model/TunnelManager.kt
git commit -m "feat(add): add TunnelManager.exists(name) for proactive name-collision checks"
```

---

## Task 5: Paste screen layout — clipboard button, name field, dup card

**Files:**
- Modify: `ui/src/main/res/layout/activity_paste_config.xml`

**Interfaces:**
- Produces (view ids consumed by Task 6): `paste_clipboard_button`, `name_label`, `name_layout`, `name_input`, `name_hint`, `dup_card`, `dup_detail`, `dup_open`.

Pure layout. Verified by build + a visual check during Task 6. Insert order on screen: helper → CONFIG STRING label → input field → **clipboard button** → validation row → **dup card** (gone) → **TUNNEL NAME label + field + hint** (gone) → preview card → trust warning.

- [ ] **Step 1: Add the clipboard button** directly after the `string_input_layout` `TextInputLayout` (before `validation_text`):

```xml
            <com.google.android.material.button.MaterialButton
                android:id="@+id/paste_clipboard_button"
                style="@style/Widget.Awg.Button.Tonal"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/space_sm"
                android:text="@string/paste_from_clipboard"
                app:icon="@drawable/ic_paste" />
```

- [ ] **Step 2: Add the soft duplicate card** immediately after `validation_text` (hidden by default):

```xml
            <com.google.android.material.card.MaterialCardView
                android:id="@+id/dup_card"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/space_md"
                android:visibility="gone"
                app:cardBackgroundColor="@color/awg_error_fill"
                app:cardCornerRadius="@dimen/radius_input"
                app:cardElevation="0dp"
                app:strokeColor="@color/awg_error_stroke"
                app:strokeWidth="1dp">
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="@dimen/space_lg">
                    <TextView
                        android:id="@+id/dup_detail"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:textAppearance="@style/TextAppearance.Awg.FieldLabel"
                        android:textColor="?attr/colorOnSurface" />
                    <com.google.android.material.button.MaterialButton
                        android:id="@+id/dup_open"
                        style="@style/Widget.Material3.Button.TextButton"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginStart="-4dp"
                        android:layout_marginTop="@dimen/space_xs"
                        android:textColor="?attr/colorPrimary" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 3: Add the editable name field** after the dup card (hidden by default), before the `preview_card`:

```xml
            <TextView
                android:id="@+id/name_label"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/space_lg"
                android:text="@string/paste_name_label"
                android:textAllCaps="true"
                android:textAppearance="@style/TextAppearance.Awg.FieldLabel"
                android:textColor="?attr/colorPrimary"
                android:visibility="gone" />

            <com.google.android.material.textfield.TextInputLayout
                android:id="@+id/name_layout"
                style="@style/AmneziaWgTheme.TextInputLayout"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/space_sm"
                android:visibility="gone"
                app:boxCornerRadiusBottomEnd="@dimen/radius_input"
                app:boxCornerRadiusBottomStart="@dimen/radius_input"
                app:boxCornerRadiusTopEnd="@dimen/radius_input"
                app:boxCornerRadiusTopStart="@dimen/radius_input"
                app:endIconDrawable="@drawable/ic_edit"
                app:endIconMode="custom"
                app:errorIconDrawable="@drawable/ic_error">
                <com.google.android.material.textfield.TextInputEditText
                    android:id="@+id/name_input"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:imeOptions="actionDone"
                    android:inputType="text|textNoSuggestions|textVisiblePassword"
                    android:maxLines="1"
                    android:textAppearance="@style/TextAppearance.Awg.FieldValue" />
            </com.google.android.material.textfield.TextInputLayout>

            <TextView
                android:id="@+id/name_hint"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="@dimen/space_xs"
                android:text="@string/paste_name_hint"
                android:textAppearance="@style/TextAppearance.Awg.FieldLabel"
                android:textColor="?attr/colorOnSurfaceVariant"
                android:visibility="gone" />
```

> The `NameInputFilter` is attached in code (Task 6), not via `app:filter` here, since this layout has no data-binding `<data>` block.

- [ ] **Step 4: Remove the now-redundant Name row from the preview card.** Delete the `preview_name_label` and `preview_name` `TextView`s (the name now lives in the editable field above). Keep `preview_endpoint*` and `preview_obfuscation*`.

- [ ] **Step 5: Verify resources compile**

Run: `./gradlew :ui:processDebugResources -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add ui/src/main/res/layout/activity_paste_config.xml
git commit -m "feat(add): paste-screen layout — clipboard button, editable name field, soft dup card"
```

---

## Task 6: Paste screen logic — clipboard, name field, dedup card

**Files:**
- Modify: `ui/src/main/java/org/amnezia/awg/activity/PasteConfigActivity.kt`

**Interfaces:**
- Consumes: layout ids from Task 5; `NameValidator.validate` (Task 2); `NameInputFilter.newInstance()` (Task 1); `TunnelManager.exists` (Task 4) + existing `findMatchingTunnel` / `create`.

Behavior:
- Dedicated clipboard button reads the clipboard into the field; empty → `paste_clipboard_empty` snackbar.
- On decode-valid: reveal + pre-fill the name field (don't overwrite once user edited it); reveal preview; gate Add on `NameValidator` + name-collision.
- On content-duplicate: show the soft dup card with `dup_detail` + `dup_open` ("Open «name»") navigating to the existing tunnel; hide the name field; disable Add.
- Commit uses the **edited** name.

This is UI wiring — verified by manual on-device steps, not a unit test.

- [ ] **Step 1: Add fields + new view refs**

Add properties:

```kotlin
    private lateinit var pasteClipboardButton: MaterialButton
    private lateinit var nameLabel: TextView
    private lateinit var nameLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var nameHint: TextView
    private lateinit var dupCard: MaterialCardView
    private lateinit var dupDetail: TextView
    private lateinit var dupOpen: MaterialButton
    private var nameEditedByUser = false
    private var duplicateOf: org.amnezia.awg.model.ObservableTunnel? = null
```

Add imports: `org.amnezia.awg.util.NameError`, `org.amnezia.awg.util.NameValidator`, `org.amnezia.awg.widget.NameInputFilter`, `android.text.InputFilter`, `android.content.Intent`, `org.amnezia.awg.activity.MainActivity` (or the existing tunnel-detail entry — see Step 6).

- [ ] **Step 2: Bind views + filter in `onCreate`** (after the existing `findViewById` block):

```kotlin
        pasteClipboardButton = findViewById(R.id.paste_clipboard_button)
        nameLabel = findViewById(R.id.name_label)
        nameLayout = findViewById(R.id.name_layout)
        nameInput = findViewById(R.id.name_input)
        nameHint = findViewById(R.id.name_hint)
        dupCard = findViewById(R.id.dup_card)
        dupDetail = findViewById(R.id.dup_detail)
        dupOpen = findViewById(R.id.dup_open)

        nameInput.filters = arrayOf<InputFilter>(NameInputFilter.newInstance())
        pasteClipboardButton.setOnClickListener { pasteFromClipboardButton() }
        nameInput.doAfterTextChanged {
            if (nameInput.hasFocus()) nameEditedByUser = true
            updateAddEnabled()
        }
```

- [ ] **Step 3: Clipboard button handler**

```kotlin
    private fun pasteFromClipboardButton() {
        val text = clipboardText()?.trim()
        if (text.isNullOrEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), R.string.paste_clipboard_empty, Snackbar.LENGTH_SHORT).show()
        } else {
            input.setText(text)
            input.setSelection(text.length)
        }
    }
```

(The end-icon listener on `string_input_layout` can stay; both call into the clipboard.)

- [ ] **Step 4: Rework `setNeutral` / `setInvalid` / `setValid` to manage the new fields**

```kotlin
    private fun setNeutral() {
        decoded = null
        duplicateOf = null
        validation.visibility = View.GONE
        previewCard.visibility = View.GONE
        dupCard.visibility = View.GONE
        setNameFieldVisible(false)
        addButton.isEnabled = false
    }

    private fun setInvalid() {
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
```

> Note: `setValid` no longer sets `previewName` (that row was removed in Task 5).

- [ ] **Step 5: Name + add-enable gating**

```kotlin
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
        // Async name-collision check; keep button optimistic until it resolves.
        val token = validationToken
        lifecycleScope.launch {
            val taken = runCatching { Application.getTunnelManager().exists(currentName()) }.getOrDefault(false)
            if (token == validationToken && decoded != null && duplicateOf == null) {
                if (taken) nameLayout.error = getString(R.string.paste_name_duplicate, currentName())
                addButton.isEnabled = nameLayout.error == null
            }
        }
        addButton.isEnabled = true
    }
```

- [ ] **Step 6: Duplicate-config soft card + Open action**

```kotlin
    private fun setDuplicate(existing: org.amnezia.awg.model.ObservableTunnel) {
        duplicateOf = existing
        validation.visibility = View.VISIBLE
        validation.text = getString(R.string.dup_config_inline)
        val err = MaterialColors.getColor(validation, com.google.android.material.R.attr.colorError)
        validation.setTextColor(err)
        validation.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_error, 0, 0, 0)
        inputLayout.boxStrokeErrorColor = android.content.res.ColorStateList.valueOf(err)
        inputLayout.error = " "                       // force the error stroke without a second message
        dupDetail.text = getString(R.string.dup_config_detail, existing.name)
        dupOpen.text = getString(R.string.dup_open_named, existing.name)
        dupOpen.setOnClickListener { openExisting(existing) }
        dupCard.visibility = View.VISIBLE
        setNameFieldVisible(false)                    // rename can't fix a content duplicate
        previewCard.visibility = View.VISIBLE
        addButton.isEnabled = false
    }

    private fun openExisting(tunnel: org.amnezia.awg.model.ObservableTunnel) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("selected_tunnel", tunnel.name)   // MainActivity reads this to focus the tunnel
        }
        startActivity(intent)
        finish()
    }
```

> Verify the extra key MainActivity already honours for deep-selecting a tunnel; if none exists, fall back to `startActivity(Intent(this, MainActivity::class.java)); finish()` and drop the `putExtra`. Do not invent an unused key.

Update `checkForDuplicate` to call the new signature:

```kotlin
    private fun checkForDuplicate(d: DecodedShare, token: Int) {
        lifecycleScope.launch {
            val existing = runCatching { Application.getTunnelManager().findMatchingTunnel(d.config) }.getOrNull()
            if (token == validationToken && decoded === d && existing != null) setDuplicate(existing)
        }
    }
```

- [ ] **Step 7: Commit under the edited name**

In `commit()`, replace `create(d.name, d.config)` with `create(currentName(), d.config)`. Also clear the input error-stroke override when leaving the duplicate state (handled by `setValid`/`setNeutral` resetting `inputLayout.error = null`). Add `inputLayout.error = null` at the top of `setValid` and `setNeutral`.

- [ ] **Step 8: Build**

Run: `./gradlew :ui:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Manual verification (record results in the commit)**

Install: `./gradlew :ui:installDebug` then on device:
1. Copy an `awg://` link → open paste screen: field auto-fills (existing behavior), name field appears pre-filled, Add enabled.
2. Tap "Paste from clipboard" with non-link text → field fills, decode shows `paste_invalid`.
3. Clear clipboard → tap button → `paste_clipboard_empty` snackbar, field untouched.
4. Edit the name → re-trigger decode (re-paste) → name is NOT overwritten.
5. Type Cyrillic in the name field → characters are blocked (filter); paste a name with a space via decode default that's invalid → `paste_name_invalid_chars`, Add disabled.
6. Paste a link for an already-added tunnel → soft dup card with "Open «name»", name field hidden, Add disabled; tap Open → lands on that tunnel.

- [ ] **Step 10: Commit**

```bash
git add ui/src/main/java/org/amnezia/awg/activity/PasteConfigActivity.kt
git commit -m "feat(add): clipboard button, editable name, and soft dedup card on paste screen"
```

---

## Task 7: QR scan — interrupting duplicate dialog

**Files:**
- Modify: `ui/src/main/java/org/amnezia/awg/util/TunnelImporter.kt`
- Modify: `ui/src/main/java/org/amnezia/awg/fragment/TunnelListFragment.kt` (only if navigation to a tunnel needs a fragment-side helper)

**Interfaces:**
- Consumes: `TunnelManager.findMatchingTunnel` (existing); strings `dup_dialog_*` (Task 3).

Per SPEC §C3: when a scanned QR decodes to a config that already exists, do NOT open the naming dialog — show an interrupting `MaterialAlertDialog` ("Already added") with **Open tunnel** / **Scan again**. The scanner activity has already closed by the time the result returns (journeyapps `ScanContract`), so no analyzer pause is needed; "Scan again" simply re-launches the scanner.

- [ ] **Step 1: Add a dup-aware import entry in `TunnelImporter`**

```kotlin
    suspend fun importTunnelOrDuplicate(
        parentFragmentManager: FragmentManager,
        configText: String,
        onDuplicate: (existing: ObservableTunnel) -> Unit,
        messageCallback: (CharSequence) -> Unit
    ) {
        val config = try {
            Config.parse(ByteArrayInputStream(configText.toByteArray(StandardCharsets.UTF_8)))
        } catch (e: Throwable) {
            onTunnelImportFinished(emptyList(), listOf(e), messageCallback); return
        }
        val existing = runCatching { Application.getTunnelManager().findMatchingTunnel(config) }.getOrNull()
        if (existing != null) { onDuplicate(existing); return }
        ConfigNamingDialogFragment.newInstance(configText).show(parentFragmentManager, null)
    }
```

(Keep the original `importTunnel` for the file-import path; this adds a QR-specific variant. Add imports for `ObservableTunnel`.)

- [ ] **Step 2: Show the interrupting dialog from the QR result launcher** in `TunnelListFragment.qrImportResultLauncher`:

```kotlin
    private val qrImportResultLauncher = registerForActivityResult(ScanContract()) { result ->
        val qrCode = result.contents
        val activity = activity
        if (qrCode != null && activity != null) {
            activity.lifecycleScope.launch {
                TunnelImporter.importTunnelOrDuplicate(
                    parentFragmentManager, qrCode,
                    onDuplicate = { existing -> showDuplicateQrDialog(existing) }
                ) { showSnackbar(it) }
            }
        }
    }

    private fun showDuplicateQrDialog(existing: ObservableTunnel) {
        val ctx = context ?: return
        MaterialAlertDialogBuilder(ctx)
            .setIcon(R.drawable.ic_error)
            .setTitle(R.string.dup_dialog_title)
            .setMessage(getString(R.string.dup_dialog_body_qr, existing.name))
            .setPositiveButton(R.string.dup_dialog_open) { _, _ -> selectTunnel(existing) }
            .setNegativeButton(R.string.dup_dialog_scan_again) { d, _ -> d.dismiss(); onAddTunnelsScanQr() }
            .show()
    }
```

> Reuse the fragment's existing "open/select this tunnel" routine and its existing "launch QR scanner" routine — wire `selectTunnel(existing)` and `onAddTunnelsScanQr()` to whatever those are actually named in `TunnelListFragment` (the scan launch is the body that calls `qrImportResultLauncher.launch(ScanOptions()...)`). Add the `MaterialAlertDialogBuilder` import if missing.

- [ ] **Step 3: Build**

Run: `./gradlew :ui:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification**

1. Scan a QR for a NEW config → naming dialog appears as before.
2. Scan a QR for an already-added config → "Already added" dialog with the existing name; "Open tunnel" navigates to it; "Scan again" reopens the scanner.

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/java/org/amnezia/awg/util/TunnelImporter.kt ui/src/main/java/org/amnezia/awg/fragment/TunnelListFragment.kt
git commit -m "feat(add): interrupting duplicate dialog on QR scan"
```

---

## Task 8: Manual editor — inline name/dup field error

**Files:**
- Modify: `ui/src/main/java/org/amnezia/awg/fragment/TunnelEditorFragment.kt`

**Interfaces:**
- Consumes: `NameValidator.validate` (Task 2), `TunnelManager.findMatchingTunnel` (existing); layout id `interface_name_layout`.

Per SPEC §C2: on the manual "Create from scratch" path, drive the **Name** `TextInputLayout` into its error state (invalid chars or content-duplicate) instead of a generic snackbar, and keep the snackbar only for genuine config-parse failures unrelated to the name. `NameInputFilter` (now ASCII) already blocks Cyrillic entry; this adds a clear field error for the decoded/edge cases and the content-duplicate case.

- [ ] **Step 1: Pre-validate the name before create** in the save handler (`menu_action_save`, create branch). After `newConfig` resolves and before `manager.create(...)`:

```kotlin
                tunnel == null -> {
                    val nameLayout = binding!!.interfaceNameLayout
                    val candidate = binding!!.name.orEmpty()
                    val nameErr = when (org.amnezia.awg.util.NameValidator.validate(candidate)) {
                        org.amnezia.awg.util.NameError.EMPTY -> getString(R.string.paste_name_empty)
                        org.amnezia.awg.util.NameError.BAD_CHARS -> getString(R.string.paste_name_invalid_chars)
                        org.amnezia.awg.util.NameError.TOO_LONG -> getString(R.string.paste_name_too_long)
                        null -> null
                    }
                    if (nameErr != null) { nameLayout.error = nameErr; return@launch }
                    val manager = Application.getTunnelManager()
                    val dup = runCatching { manager.findMatchingTunnel(newConfig) }.getOrNull()
                    if (dup != null) { nameLayout.error = getString(R.string.dup_name_inline, dup.name); return@launch }
                    nameLayout.error = null
                    try {
                        onTunnelCreated(manager.create(candidate, newConfig), null)
                    } catch (e: Throwable) {
                        onTunnelCreated(null, e)
                    }
                }
```

> Confirm the generated binding id for `@+id/interface_name_layout` (likely `binding.interfaceNameLayout`). Set `app:errorIconDrawable="@drawable/ic_error"` on that `TextInputLayout` in `tunnel_editor_fragment.xml` for glyph consistency (one-line layout edit).

- [ ] **Step 2: Clear the field error when the name changes** so the user can recover. If the editor already observes the name via data binding, add a `doAfterTextChanged` on `interface_name_text` in `onViewCreated` that sets `interfaceNameLayout.error = null`.

- [ ] **Step 3: Build**

Run: `./gradlew :ui:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification**

1. Create from scratch, leave name empty → field error `paste_name_empty`, Save blocked.
2. Try to type Cyrillic → blocked by filter.
3. Enter a valid name but a config that matches an existing tunnel → field error `dup_name_inline` with the existing name.
4. Fix the name/config → error clears, save succeeds.

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/java/org/amnezia/awg/fragment/TunnelEditorFragment.kt ui/src/main/res/layout/tunnel_editor_fragment.xml
git commit -m "feat(add): inline name/duplicate field error in manual editor"
```

---

## Task 9: QR naming dialog — proactive name gating + error glyph

**Files:**
- Modify: `ui/src/main/java/org/amnezia/awg/fragment/ConfigNamingDialogFragment.kt`
- Modify: `ui/src/main/res/layout/config_naming_dialog_fragment.xml`

**Interfaces:**
- Consumes: `NameValidator.validate` (Task 2).

The naming dialog (used by QR + file import) currently only shows `e.message` after a failed `create()`. Add live validation: disable the Create button while the name is invalid, and route the create-time errors through the field with the `ic_error` glyph.

- [ ] **Step 1: Add the error icon to the field** in `config_naming_dialog_fragment.xml` on `tunnel_name_text_layout`:

```xml
        app:errorIconDrawable="@drawable/ic_error"
        app:errorEnabled="true"
```

- [ ] **Step 2: Gate the Create button** in `onCreateDialog` after `create()` returns the dialog:

```kotlin
        val dialog = alertDialogBuilder.create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.setOnShowListener {
            val createBtn = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            fun refresh() {
                val err = when (org.amnezia.awg.util.NameValidator.validate(binding?.tunnelNameText?.text?.toString().orEmpty())) {
                    org.amnezia.awg.util.NameError.EMPTY -> null  // empty: just disable, no scary message yet
                    org.amnezia.awg.util.NameError.BAD_CHARS -> getString(R.string.paste_name_invalid_chars)
                    org.amnezia.awg.util.NameError.TOO_LONG -> getString(R.string.paste_name_too_long)
                    null -> null
                }
                binding?.tunnelNameTextLayout?.error = err
                createBtn.isEnabled = org.amnezia.awg.util.NameValidator.validate(
                    binding?.tunnelNameText?.text?.toString().orEmpty()) == null
            }
            binding?.tunnelNameText?.doAfterTextChanged { refresh() }
            refresh()
        }
        return dialog
```

Add import `androidx.core.widget.doAfterTextChanged`.

- [ ] **Step 3: Keep create-time errors on the field** — `createTunnelAndDismiss` already sets `binding.tunnelNameTextLayout.error = e.message`; leave as-is (it now renders with the `ic_error` glyph). This covers the content-duplicate case (`tunnel_error_duplicate_config`).

- [ ] **Step 4: Build**

Run: `./gradlew :ui:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual verification**

1. Import a QR/file → naming dialog: Create disabled while name empty.
2. (Filter blocks Cyrillic; paste a name with bad chars if possible) → `paste_name_invalid_chars`, Create disabled.
3. Name that collides with existing → on Create, field shows the duplicate message with the alert glyph.

- [ ] **Step 6: Commit**

```bash
git add ui/src/main/java/org/amnezia/awg/fragment/ConfigNamingDialogFragment.kt ui/src/main/res/layout/config_naming_dialog_fragment.xml
git commit -m "feat(add): proactive name gating + error glyph in import naming dialog"
```

---

## Task 10: Restyle remaining error surfaces (Snackbar theme)

**Files:**
- Modify: `ui/src/main/res/values/themes-awg-additions.xml` + `values-night/themes-awg-additions.xml` (add `snackbarStyle` to the app theme) — confirm the actual app theme name first.
- Create: `ui/src/main/res/values/styles-awg-snackbar.xml`

**Interfaces:**
- Produces: `Widget.Awg.Snackbar` / `Widget.Awg.Snackbar.TextView` / `Widget.Awg.Snackbar.Button` applied via the theme so every `Snackbar` in the app (import errors, create errors, etc.) matches the design without touching each call site.

The remaining generic errors all surface through `Snackbar` (`ErrorMessages[e]`). Theming the Snackbar once restyles them consistently: tonal `surfaceContainerHigh` background, `onSurface` text, `colorError` for the action — calm, not the default black bar.

- [ ] **Step 1: Add the snackbar styles**

```xml
<!-- ui/src/main/res/values/styles-awg-snackbar.xml -->
<resources>
    <style name="Widget.Awg.Snackbar" parent="Widget.Material3.Snackbar">
        <item name="backgroundTint">?attr/colorSurfaceContainerHigh</item>
        <item name="android:layout_margin">@dimen/space_md</item>
        <item name="shapeAppearance">@style/ShapeAppearance.Material3.Corner.Medium</item>
    </style>
    <style name="Widget.Awg.Snackbar.TextView" parent="Widget.Material3.Snackbar.TextView">
        <item name="android:textColor">?attr/colorOnSurface</item>
    </style>
    <style name="Widget.Awg.Snackbar.Button" parent="Widget.Material3.Button.TextButton.Snackbar">
        <item name="android:textColor">?attr/colorError</item>
    </style>
</resources>
```

- [ ] **Step 2: Point the theme at them.** In the app theme (the parent `AmneziaWgTheme` definition — grep `name="AmneziaWgTheme"` in `themes*.xml` to find the exact theme applied in the manifest), add:

```xml
        <item name="snackbarStyle">@style/Widget.Awg.Snackbar</item>
        <item name="snackbarTextViewStyle">@style/Widget.Awg.Snackbar.TextView</item>
        <item name="snackbarButtonStyle">@style/Widget.Awg.Snackbar.Button</item>
```

- [ ] **Step 3: Build**

Run: `./gradlew :ui:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual verification**

1. Trigger an import error (e.g. import a malformed `.conf`) → snackbar now uses the tonal surface + teal/error action, not the default dark bar.
2. Trigger a config-save error in the editor → same restyled snackbar.

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/res/values/styles-awg-snackbar.xml ui/src/main/res/values/themes-awg-additions.xml ui/src/main/res/values-night/themes-awg-additions.xml
git commit -m "feat(ui): restyle Snackbars into the Network-Teal error language"
```

---

## Self-Review

**Spec coverage:**
- INTEGRATION §1 (clipboard button + auto-fill) → Task 5 (button) + Task 6 (handler); auto-fill on open already exists in `onCreate`. ✓
- INTEGRATION §2 / SPEC §B (editable name, pre-fill, stop-overwrite, validate, commit-under-name) → Tasks 5–6. ✓
- INTEGRATION §3 / SPEC §C1 (paste inline soft card + Open) → Tasks 5–6. ✓
- SPEC §C2 (manual inline field error) → Task 8. ✓
- SPEC §C3 (QR interrupting dialog) → Task 7. ✓
- User item 4 (Cyrillic ASCII guard, gate before commit) → Task 1 (root filter fix) + Task 2 (validator) applied in Tasks 6/8/9. ✓
- User add'l ("other errors styled with our design") → Task 9 (naming dialog glyph/gate) + Task 10 (Snackbar theme). ✓

**Placeholder scan:** Three steps defer to "confirm the actual id/key/name in the codebase" (MainActivity tunnel-select extra in Task 6 Step 6; editor binding id in Task 8; scan-launch/select routines in Task 7; app theme name in Task 10). These are real integration points the implementer must read from existing code rather than invent — each names the exact symbol to look for and the fallback. Acceptable, not hand-waving.

**Type consistency:** `NameError`/`NameValidator.validate` used identically in Tasks 6/8/9. `findMatchingTunnel`/`exists` signatures match Task 4 + existing `TunnelManager`. `setDuplicate(ObservableTunnel)` signature consistent between definition and `checkForDuplicate` caller (Task 6). View ids in Task 5 match the bindings in Task 6.
