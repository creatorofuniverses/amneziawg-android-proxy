# Name-Independent Duplicate-Tunnel Detection — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reject a config that is already added under any name — matched by interface key + peer endpoint — across every import path, with a live "already added" hint on the paste screen.

**Architecture:** A pure helper `sameTunnelIdentity(a, b)` is the single source of truth for "same tunnel." `TunnelManager.findMatchingTunnel(config)` scans existing tunnels with it; `TunnelManager.create` calls that and throws (the global guarantee, inherited by file/QR/paste/deep-link); `PasteConfigActivity.validate` calls it for a live, best-effort "Already added as …" state.

**Tech Stack:** Kotlin (ui module), JUnit 4, AndroidX, the `tunnel` module's `Config`/`Interface`/`KeyPair`/`Key` types (already a dependency of `ui`).

**Spec:** `docs/superpowers/specs/2026-06-17-config-dedup-design.md`. Builds on the `awg://v1` import feature already on this branch (`refresh-paste-import`).

## Global Constraints

- **Match rule (exact):** two configs are the same tunnel iff their `[Interface]` **public** keys are equal AND their **first peer's endpoint string** is equal; two configs that both lack an endpoint are equal on the endpoint dimension. Compare the public key (`interface.keyPair.publicKey`, type `org.amnezia.awg.crypto.Key`, which has `equals`), never private-key bytes. Endpoint string = `peers.firstOrNull()?.endpoint?.orElse(null)?.toString()`.
- **Consequence:** same key + different endpoint is allowed (separate tunnel); same key + same endpoint is a duplicate.
- **Enforcement is global:** the authoritative check is in `TunnelManager.create`, so all import paths inherit it. The paste-screen check is a best-effort UX layer only; `create` is the guarantee.
- **Reuse the existing failure shape:** throw a plain `IllegalArgumentException(context.getString(R.string.tunnel_error_duplicate_config, existingName))` — identical in shape to the `tunnel_error_already_exists` throw one line above it. No new exception type, no new `ErrorMessages` branch.
- **Security (unchanged):** never `Log` the share-string / config / intent data. The paste-screen change must not introduce logging.
- **String text (exact):**
  - `tunnel_error_duplicate_config` = `This config is already added as “%1$s”.` (in `ui/src/main/res/values/strings.xml`)
  - `paste_duplicate` = `Already added as “%1$s”` (in `ui/src/main/res/values/strings-awg-add.xml`)
- **No refactoring** beyond these changes.

---

## File Structure

- Create `ui/src/main/java/org/amnezia/awg/util/TunnelIdentity.kt` — the pure `sameTunnelIdentity` helper (Task 1).
- Create `ui/src/test/java/org/amnezia/awg/util/TunnelIdentityTest.kt` — its unit tests (Task 1).
- Modify `ui/src/main/java/org/amnezia/awg/model/TunnelManager.kt` — add `findMatchingTunnel`, call it in `create` (Task 2).
- Modify `ui/src/main/res/values/strings.xml` — add `tunnel_error_duplicate_config` (Task 2).
- Modify `ui/src/main/java/org/amnezia/awg/activity/PasteConfigActivity.kt` — live duplicate state in `validate` (Task 3).
- Modify `ui/src/main/res/values/strings-awg-add.xml` — add `paste_duplicate` (Task 3).
- Modify `docs/superpowers/specs/2026-06-17-config-dedup-design.md` — status line (Task 4).

---

## Task 1: `sameTunnelIdentity` pure helper + unit tests

The single source of truth for the match rule. Pure JVM (operates on parsed `Config`), unit-testable without Robolectric — mirrors `ShareStringDecoderTest`.

**Files:**
- Create: `ui/src/main/java/org/amnezia/awg/util/TunnelIdentity.kt`
- Test: `ui/src/test/java/org/amnezia/awg/util/TunnelIdentityTest.kt`

**Interfaces:**
- Consumes: `org.amnezia.awg.config.Config` (`getInterface()`/`getPeers()`), `org.amnezia.awg.config.Interface.getKeyPair()`, `org.amnezia.awg.crypto.KeyPair.getPublicKey()`, `org.amnezia.awg.crypto.Key.equals`.
- Produces: `fun sameTunnelIdentity(a: Config, b: Config): Boolean` in package `org.amnezia.awg.util` (top-level function).

- [ ] **Step 1: Write the failing test**

```kotlin
// ui/src/test/java/org/amnezia/awg/util/TunnelIdentityTest.kt
package org.amnezia.awg.util

import org.amnezia.awg.config.Config
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class TunnelIdentityTest {
    // Two distinct, valid 32-byte base64 keys (same form as ShareStringDecoderTest).
    private val key1 = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA="
    private val key2 = "ISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0A="
    private val peerPub = "ISIjJCUmJygpKissLS4vMDEyMzQ1Njc4OTo7PD0+P0A="

    /** Build a Config from an interface private key, an optional endpoint, and an optional DNS line. */
    private fun config(privKey: String, endpoint: String?, dns: String? = null): Config {
        val sb = StringBuilder()
        sb.append("[Interface]\n")
        sb.append("PrivateKey = ").append(privKey).append('\n')
        sb.append("Address = 10.8.0.2/32\n")
        if (dns != null) sb.append("DNS = ").append(dns).append('\n')
        sb.append("[Peer]\n")
        sb.append("PublicKey = ").append(peerPub).append('\n')
        if (endpoint != null) sb.append("Endpoint = ").append(endpoint).append('\n')
        sb.append("AllowedIPs = 0.0.0.0/0\n")
        return Config.parse(ByteArrayInputStream(sb.toString().toByteArray(StandardCharsets.UTF_8)))
    }

    @Test fun sameKeySameEndpoint_isDuplicate() {
        assertTrue(sameTunnelIdentity(config(key1, "192.0.2.1:51820"), config(key1, "192.0.2.1:51820")))
    }

    @Test fun sameKeySameEndpoint_otherFieldsDiffer_isDuplicate() {
        // Only interface key + first-peer endpoint matter; a different DNS must not change the verdict.
        assertTrue(sameTunnelIdentity(config(key1, "192.0.2.1:51820", dns = "1.1.1.1"),
                                      config(key1, "192.0.2.1:51820", dns = "8.8.8.8")))
    }

    @Test fun sameKeyDifferentEndpoint_isNotDuplicate() {
        assertFalse(sameTunnelIdentity(config(key1, "192.0.2.1:51820"), config(key1, "198.51.100.7:51820")))
    }

    @Test fun differentKeySameEndpoint_isNotDuplicate() {
        assertFalse(sameTunnelIdentity(config(key1, "192.0.2.1:51820"), config(key2, "192.0.2.1:51820")))
    }

    @Test fun bothNoEndpoint_sameKey_isDuplicate() {
        assertTrue(sameTunnelIdentity(config(key1, null), config(key1, null)))
    }

    @Test fun oneNoEndpoint_isNotDuplicate() {
        assertFalse(sameTunnelIdentity(config(key1, "192.0.2.1:51820"), config(key1, null)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :ui:testDebugUnitTest --tests "org.amnezia.awg.util.TunnelIdentityTest"`
Expected: FAIL — `sameTunnelIdentity` unresolved (compilation error).

- [ ] **Step 3: Write minimal implementation**

```kotlin
// ui/src/main/java/org/amnezia/awg/util/TunnelIdentity.kt
package org.amnezia.awg.util

import org.amnezia.awg.config.Config

/**
 * Two configs identify the SAME tunnel — independent of the tunnel name — iff their interface
 * public keys are equal AND their first peer's endpoint is equal. Endpoints are compared by string
 * form; two configs that both lack an endpoint are equal on that dimension. The public key is used
 * (not the private key) because it is derived deterministically from the private key — an identical
 * identity test that does not touch secret-key bytes.
 */
fun sameTunnelIdentity(a: Config, b: Config): Boolean {
    val sameKey = a.`interface`.keyPair.publicKey == b.`interface`.keyPair.publicKey
    if (!sameKey) return false
    return firstEndpoint(a) == firstEndpoint(b)
}

private fun firstEndpoint(config: Config): String? =
    config.peers.firstOrNull()?.endpoint?.orElse(null)?.toString()
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :ui:testDebugUnitTest --tests "org.amnezia.awg.util.TunnelIdentityTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/java/org/amnezia/awg/util/TunnelIdentity.kt \
        ui/src/test/java/org/amnezia/awg/util/TunnelIdentityTest.kt
git commit -m "feat(ui): add sameTunnelIdentity (key + endpoint) config matcher"
```

---

## Task 2: `findMatchingTunnel` + `create` guard + duplicate string

The global enforcement. `create` is the single choke point for file import, QR scan, paste, and deep link, so the guard here covers them all.

**Files:**
- Modify: `ui/src/main/java/org/amnezia/awg/model/TunnelManager.kt`
- Modify: `ui/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `sameTunnelIdentity(a: Config, b: Config): Boolean` (Task 1); `getTunnels()` → `ObservableSortedKeyedArrayList<String, ObservableTunnel>` (iterable); `ObservableTunnel.getConfigAsync(): Config` (suspend; may throw if a stored config fails to load); `ObservableTunnel.name`.
- Produces: `suspend fun TunnelManager.findMatchingTunnel(config: Config): ObservableTunnel?`.

- [ ] **Step 1: Add the duplicate string**

In `ui/src/main/res/values/strings.xml`, immediately after the existing `tunnel_error_already_exists` entry, add:

```xml
    <string name="tunnel_error_duplicate_config">This config is already added as “%1$s”.</string>
```

> Find the anchor first: `grep -n tunnel_error_already_exists ui/src/main/res/values/strings.xml`. Keep the curly quotes “ ” exactly as shown.

- [ ] **Step 2: Add `findMatchingTunnel` to `TunnelManager`**

Add this method to `class TunnelManager` (e.g. directly below `getTunnels()`), in `ui/src/main/java/org/amnezia/awg/model/TunnelManager.kt`:

```kotlin
    suspend fun findMatchingTunnel(config: Config): ObservableTunnel? = withContext(Dispatchers.Main.immediate) {
        getTunnels().firstOrNull { existing ->
            val existingConfig = runCatching { existing.getConfigAsync() }.getOrNull() ?: return@firstOrNull false
            sameTunnelIdentity(existingConfig, config)
        }
    }
```

> `org.amnezia.awg.config.Config` is already imported in this file. Add `import org.amnezia.awg.util.sameTunnelIdentity` to the import block. `withContext`/`Dispatchers` are already imported (used throughout the file). `runCatching{}.getOrNull()` makes a tunnel whose config fails to load a non-match rather than throwing out of the scan.

- [ ] **Step 3: Call the guard inside `create`**

In `TunnelManager.create`, immediately after the existing name-collision check (`if (tunnelMap.containsKey(name)) throw …tunnel_error_already_exists…`) and before `addToList(...)`, insert:

```kotlin
        findMatchingTunnel(config!!)?.let {
            throw IllegalArgumentException(context.getString(R.string.tunnel_error_duplicate_config, it.name))
        }
```

> `config` is the `Config?` parameter; the existing code already force-unwraps it as `config!!` in the `addToList` line, so the same `config!!` here is consistent. `context` and `R` are already in scope (the line above uses `context.getString(R.string.tunnel_error_already_exists, name)`).

- [ ] **Step 4: Build to verify it compiles**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/java/org/amnezia/awg/model/TunnelManager.kt \
        ui/src/main/res/values/strings.xml
git commit -m "feat(ui): block content-duplicate configs in TunnelManager.create"
```

---

## Task 3: Live "already added" state on the paste screen

The best-effort UX layer the user approved: surface the duplicate during live decode and disable Add, with `create` (Task 2) as the backstop.

**Files:**
- Modify: `ui/src/main/java/org/amnezia/awg/activity/PasteConfigActivity.kt`
- Modify: `ui/src/main/res/values/strings-awg-add.xml`

**Interfaces:**
- Consumes: `Application.getTunnelManager().findMatchingTunnel(config)` (Task 2); existing fields `validation`, `previewCard`, `addButton`, `decoded`; existing `setValid(DecodedShare)`.
- Produces: a `setDuplicate(existingName)` state and a `validationToken` guard inside `PasteConfigActivity`.

- [ ] **Step 1: Add the live-duplicate string**

In `ui/src/main/res/values/strings-awg-add.xml`, after the `paste_invalid` entry, add:

```xml
    <string name="paste_duplicate">Already added as “%1$s”</string>
```

- [ ] **Step 2: Add a staleness token field**

In `PasteConfigActivity`, beside `private var decoded: DecodedShare? = null`, add:

```kotlin
    private var validationToken = 0
```

> Every `validate()` call bumps this token; an async duplicate-check result is applied only if its captured token still equals the current one — so a slow check from an earlier keystroke cannot clobber newer input.

- [ ] **Step 3: Bump the token in `validate` and kick off the duplicate check on the valid path**

Replace the body of `validate(raw: String)` with:

```kotlin
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
```

- [ ] **Step 4: Add `checkForDuplicate` and `setDuplicate`**

Add these methods to `PasteConfigActivity` (e.g. after `setValid`):

```kotlin
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
```

> `lifecycleScope`, `View`, `MaterialColors`, `R`, `Application`, `DecodedShare`, and `ShareStringDecoder` are already imported in this file (used by the existing code). Do not add any logging.

- [ ] **Step 5: Build to verify it compiles**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add ui/src/main/java/org/amnezia/awg/activity/PasteConfigActivity.kt \
        ui/src/main/res/values/strings-awg-add.xml
git commit -m "feat(ui): live 'already added' state on paste screen for duplicates"
```

---

## Task 4: Regression + docs touch-up

**Files:**
- Modify: `docs/superpowers/specs/2026-06-17-config-dedup-design.md` (status line)

- [ ] **Step 1: Run the unit suites**

Run: `./gradlew :tunnel:testDebugUnitTest :ui:testDebugUnitTest`
Expected: the new `TunnelIdentityTest` (6) green and all other `:ui` + `:tunnel` suites unchanged from before this plan. Note: a pre-existing `:tunnel` failure `BadConfigExceptionTest#throws_correctly_with_SYNTAX_ERROR_reason` is unrelated to this work (it predates the branch) — confirm the set of failures did not grow.

- [ ] **Step 2: Assemble**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Update the design-doc status line**

In `docs/superpowers/specs/2026-06-17-config-dedup-design.md`, change the `**Status:**` line from "Approved … ready for `superpowers:writing-plans`" to note it is implemented on `refresh-paste-import`, referencing this plan file `docs/superpowers/plans/2026-06-17-config-dedup.md`.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-06-17-config-dedup-design.md
git commit -m "docs: mark duplicate-detection implemented"
```

---

## Self-Review

**Spec coverage:**
- Match rule (public key + first-peer endpoint string, both-absent equal) → Task 1 `sameTunnelIdentity` + its 6 tests. ✓
- Global enforcement in `create` covering all import paths → Task 2. ✓
- Reuse plain `IllegalArgumentException` + existing `ErrorMessages`/Snackbar path → Task 2 Step 3 (same shape as `tunnel_error_already_exists`). ✓
- Live best-effort paste-screen state with stale-guard, preview stays, Add disabled → Task 3. ✓
- Exact strings in the correct files → Task 2 Step 1 (`strings.xml`), Task 3 Step 1 (`strings-awg-add.xml`). ✓
- Security: no logging introduced → Task 3 note. ✓
- `findMatchingTunnel` tolerates a config that fails to load → Task 2 Step 2 (`runCatching`). ✓

**Placeholder scan:** No TBD/TODO/"handle errors"/"similar to". All code blocks are complete; resource anchors (`grep` for the string anchor) are concrete.

**Type consistency:** `sameTunnelIdentity(a: Config, b: Config): Boolean` defined in Task 1, consumed verbatim in Task 2 (`findMatchingTunnel`). `findMatchingTunnel(config: Config): ObservableTunnel?` defined in Task 2, consumed verbatim in Task 3 (`checkForDuplicate`). `tunnel_error_duplicate_config` (Task 2) / `paste_duplicate` (Task 3) referenced only where defined. `validationToken`/`decoded`/`setValid`/`setInvalid`/`setNeutral` match the existing `PasteConfigActivity` members.
