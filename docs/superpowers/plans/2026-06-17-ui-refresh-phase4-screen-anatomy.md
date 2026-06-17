# UI Refresh Phase 4 — Screen Anatomy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the detail / editor / split-tunnel screens to the asset-pack anatomy, add a 3-way protocol badge (WG / Amnezia / Proxy), and rename the app — all as a refresh, no new screens.

**Architecture:** Pure logic (protocol detection, split-tunnel count) goes into the Java `tunnel` module behind unit tests. Screen changes are XML layout + small Kotlin in the existing fragments (`TunnelDetailFragment`, `TunnelEditorFragment`, `TunnelListFragment`), reusing the existing `ViewDataBinding` and the app-wide DataStore. Chips use Material's `ChipGroup` (no new dependency).

**Tech Stack:** Android XML + data binding, Kotlin, Material Components 1.11.0, AndroidX DataStore, JUnit4 (tunnel module). `minSdk 24`.

**Spec:** `docs/design/spec-phase4-screen-anatomy.md`

## Execution status (paused 2026-06-17)

Executing via subagent-driven-development on `ui-refresh-dev`. Phase-4 base commit = `944fe569`.

| Task | Status | Commit |
|---|---|---|
| 1 — foundation (chevron + strings) | ✅ done, review-clean | `36edf4e2` |
| 2 — ObfuscationMode detection (TDD) | ✅ done, review-clean | `621c8400` |
| 3 — detail chips + protocol badge | ✅ done, review-clean | `a63d5439` |
| 4 — collapsible sections + persistence | ⏸ implemented, **review pending** | `adb3cabb` |
| 5–9 | ⬜ not started | — |

Resume: re-enter `superpowers:subagent-driven-development`, run the **Task 4 review first** (package at `.git/sdd/review-a63d5439..adb3cabb.diff`), then Tasks 5–9, final whole-branch review, finishing-a-development-branch. Full resume notes + minor-cleanup roll-up live in the local ledger `.git/sdd/progress.md`. Verify with `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain` (full APK needs NDK/go, won't build here).

## Global Constraints

- Stay XML + Material Components **1.11.0**, **no Compose**, `minSdk 24`.
- **No new heavyweight dependency.** Chips use `com.google.android.material.chip.ChipGroup` (already available). Do NOT add flexbox.
- Light + dark + **TV** all stay intact; preserve every existing `nextFocus*` and `contentDescription`.
- Do **not** rename the `tunnel_status_*` color tokens (values already correct).
- Do **not** remove or hide any AmneziaWG field/functionality — only restructure presentation.
- New user-visible strings go in `values/strings.xml`; keep copy short (20+ locales).
- Protocol badge labels: `WG` / `Amnezia` / `Proxy` (user confirmed "Proxy").
- App names: launcher label = **"AWG Proxy"**; in-app header = **"AmneziaWG Proxy"**.

**Build / verify commands (used throughout):**
- Compile (resources + Kotlin): `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
  — the **full APK needs the NDK + go submodule and does NOT build here**, so do NOT use `:ui:assembleDebug`. The resource-link + data-binding + Kotlin compile above is the standard verification used through phases 1–3 and catches the errors these tasks can introduce.
- Tunnel unit tests: `./gradlew :tunnel:test --console=plain`
- SDK is at `~/android-sdk`. App module is `:ui`; `:tunnel` is the native/config lib.

> **Verification note:** Layout/Kotlin-UI tasks below cannot be meaningfully unit-tested without instrumentation; their "test" step is a clean resource+Kotlin compile (command above) plus an explicit on-device check against the task's acceptance line. On-device checks are done by the user (Vitaly reviews each batch on-device and has caught real bugs — wait for feedback). The two pure-logic tasks (2 and 7) use real JUnit4 TDD.

---

### Task 1: Foundation — chevron drawable + strings

**Files:**
- Create: `ui/src/main/res/drawable/ic_chevron_down.xml`
- Modify: `ui/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: string resources `section_interface`, `section_obfuscation`, `section_peer`, `section_applications`, `detail_section_count`, `detail_public_endpoint`, `detail_endpoint`, `detail_interface_addresses`, `detail_allowed_ips`, `split_tunnel_summary`, `split_tunnel_all_apps`, `proto_badge_wg`, `proto_badge_amnezia`, `proto_badge_proxy`; drawable `ic_chevron_down`.

- [ ] **Step 1: Copy the chevron drawable from the asset pack**

Copy `~/Documents/amneziawg-refresh-assets/res/drawable/ic_chevron_down.xml` into `ui/src/main/res/drawable/ic_chevron_down.xml` verbatim.

- [ ] **Step 2: Add strings**

Add to `ui/src/main/res/values/strings.xml` (inside `<resources>`):

```xml
<string name="section_interface">Interface</string>
<string name="section_obfuscation">Obfuscation</string>
<string name="section_peer">Peer</string>
<string name="section_applications">Applications</string>
<string name="detail_section_count">%d parameters</string>
<string name="detail_public_endpoint">Server address</string>
<string name="detail_endpoint">Endpoint</string>
<string name="detail_interface_addresses">Addresses</string>
<string name="detail_allowed_ips">Allowed IPs</string>
<string name="split_tunnel_summary">%1$d of %2$d apps routed</string>
<string name="split_tunnel_all_apps">All apps routed</string>
<string name="proto_badge_wg" translatable="false">WG</string>
<string name="proto_badge_amnezia" translatable="false">Amnezia</string>
<string name="proto_badge_proxy" translatable="false">Proxy</string>
```

> If any string name already exists (e.g. `section_*` from a prior phase), reuse it and skip the duplicate — do not redefine.

- [ ] **Step 3: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL (resources compile, no duplicate-resource error).

- [ ] **Step 4: Commit**

```bash
git add ui/src/main/res/drawable/ic_chevron_down.xml ui/src/main/res/values/strings.xml
git commit -m "refresh: phase-4 foundation — chevron drawable + detail/split/badge strings"
```

---

### Task 2: Obfuscation-mode detection (pure logic, TDD)

**Files:**
- Create: `tunnel/src/main/java/org/amnezia/awg/config/ObfuscationMode.java`
- Test: `tunnel/src/test/java/org/amnezia/awg/config/ObfuscationModeTest.java`

**Interfaces:**
- Consumes: `org.amnezia.awg.config.Interface` getters (`getJunkPacketCount()` … `getSpecialJunkI1()`…`getSpecialJunkI5()`, `getImitateProtocol()` — all `Optional`).
- Produces: `enum ObfuscationMode { WG, AMNEZIA, PROXY }` with `static ObfuscationMode of(Interface iface)`.

- [ ] **Step 0: Confirm parse keys**

Open `tunnel/src/main/java/org/amnezia/awg/config/Interface.java` around lines 100–200 (the `parse` switch). Confirm the INI attribute keys for junk count and special-junk-1 (expected `jc` and `i1`) and imitate protocol (`imitateprotocol`, already confirmed). The test below uses `Jc`, `I1`, `ImitateProtocol`; if a key differs, adjust the test strings to match the switch.

- [ ] **Step 1: Write the failing test**

Create `tunnel/src/test/java/org/amnezia/awg/config/ObfuscationModeTest.java`:

```java
package org.amnezia.awg.config;

import static org.junit.Assert.assertEquals;

import org.amnezia.awg.crypto.KeyPair;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ObfuscationModeTest {

    private static Interface iface(final String... extraLines) throws Exception {
        final List<String> lines = new ArrayList<>();
        lines.add("PrivateKey = " + new KeyPair().getPrivateKey().toBase64());
        Collections.addAll(lines, extraLines);
        return Interface.parse(lines);
    }

    @Test
    public void plainWireGuard_isWg() throws Exception {
        assertEquals(ObfuscationMode.WG, ObfuscationMode.of(iface()));
    }

    @Test
    public void staticJunkOnly_isAmnezia() throws Exception {
        assertEquals(ObfuscationMode.AMNEZIA, ObfuscationMode.of(iface("Jc = 4")));
    }

    @Test
    public void dynamicSignaturePacket_isProxy() throws Exception {
        // I1 carrying the <…>-tagged dynamic builder format
        assertEquals(ObfuscationMode.PROXY, ObfuscationMode.of(iface("Jc = 4", "I1 = <b 0xf20c7b9a>")));
    }

    @Test
    public void imitateProtocolSet_isProxy() throws Exception {
        assertEquals(ObfuscationMode.PROXY, ObfuscationMode.of(iface("Jc = 4", "ImitateProtocol = quic")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :tunnel:test --tests org.amnezia.awg.config.ObfuscationModeTest`
Expected: FAIL — `ObfuscationMode` does not exist (compile error).

- [ ] **Step 3: Write the implementation**

Create `tunnel/src/main/java/org/amnezia/awg/config/ObfuscationMode.java`:

```java
package org.amnezia.awg.config;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Classifies an {@link Interface}'s obfuscation configuration into one of three
 * user-facing modes for the protocol badge:
 *   WG       — no AmneziaWG obfuscation params at all (plain WireGuard).
 *   AMNEZIA  — obfuscation present, but special-junk I1..I5 are all static and no
 *              traffic-imitation protocol is configured.
 *   PROXY    — any special-junk uses the dynamic {@code <…>}-tagged builder format,
 *              or a traffic-imitation protocol is configured.
 */
public enum ObfuscationMode {
    WG, AMNEZIA, PROXY;

    public static ObfuscationMode of(final Interface iface) {
        final List<Optional<String>> specialJunk = Arrays.asList(
                iface.getSpecialJunkI1(), iface.getSpecialJunkI2(), iface.getSpecialJunkI3(),
                iface.getSpecialJunkI4(), iface.getSpecialJunkI5());

        final boolean hasObfuscation =
                iface.getJunkPacketCount().isPresent() || iface.getJunkPacketMinSize().isPresent() ||
                iface.getJunkPacketMaxSize().isPresent() || iface.getInitPacketJunkSize().isPresent() ||
                iface.getResponsePacketJunkSize().isPresent() || iface.getCookieReplyPacketJunkSize().isPresent() ||
                iface.getTransportPacketJunkSize().isPresent() || iface.getInitPacketMagicHeader().isPresent() ||
                iface.getResponsePacketMagicHeader().isPresent() || iface.getUnderloadPacketMagicHeader().isPresent() ||
                iface.getTransportPacketMagicHeader().isPresent() ||
                specialJunk.stream().anyMatch(Optional::isPresent) ||
                iface.getImitateProtocol().isPresent();

        if (!hasObfuscation)
            return WG;

        final boolean dynamicSignature = specialJunk.stream()
                .filter(Optional::isPresent).map(Optional::get)
                .anyMatch(s -> s.contains("<"));

        if (dynamicSignature || iface.getImitateProtocol().isPresent())
            return PROXY;

        return AMNEZIA;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :tunnel:test --tests org.amnezia.awg.config.ObfuscationModeTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add tunnel/src/main/java/org/amnezia/awg/config/ObfuscationMode.java tunnel/src/test/java/org/amnezia/awg/config/ObfuscationModeTest.java
git commit -m "feat: ObfuscationMode detection (WG / Amnezia / Proxy) with tests"
```

---

### Task 3: Detail — obfuscation chips + protocol badge

**Files:**
- Modify: `ui/src/main/res/layout/tunnel_detail_fragment.xml` (obfuscation card body ~lines 433–858; tunnel-name block ~lines 171–193)
- Modify: `ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt`

**Interfaces:**
- Consumes: `ObfuscationMode.of(Interface)` (Task 2); `binding.config` (a `Config`, set after `getConfigAsync()`); `bg_stat_chip` drawable, `TextAppearance.Awg.Mono` (existing).
- Produces: populated `@id/obfuscation_chips` ChipGroup; `@id/proto_badge` TextView; Kotlin `populateObfuscation(config)`.

- [ ] **Step 1: Add the badge view next to the tunnel name**

In `tunnel_detail_fragment.xml`, in the tunnel-name block (around `@id/interface_name_text`, line ~181), add after the name TextView:

```xml
<TextView
    android:id="@+id/proto_badge"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginStart="@dimen/space_sm"
    android:background="@drawable/bg_stat_chip"
    android:backgroundTint="?attr/colorPrimaryContainer"
    android:paddingHorizontal="@dimen/space_sm"
    android:paddingVertical="2dp"
    android:textColor="?attr/colorOnPrimaryContainer"
    android:textAppearance="@style/TextAppearance.Awg.StatLabel"
    tools:text="Proxy" />
```

(Ensure `xmlns:tools` is declared on the root; it usually is.)

- [ ] **Step 2: Replace the obfuscation rows with a ChipGroup**

In `tunnel_detail_fragment.xml`, inside the obfuscation card body, replace the static AWG label/value rows (between the obfuscation header and `@id/amnezia_barrier`) with:

```xml
<com.google.android.material.chip.ChipGroup
    android:id="@+id/obfuscation_chips"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:chipSpacingHorizontal="@dimen/space_xs"
    app:chipSpacingVertical="@dimen/space_xs"
    app:layout_constraintTop_toBottomOf="@id/obfuscation_title"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent" />
```

Remove the now-unused per-param TextView ids and the `@id/amnezia_barrier` if it only referenced those rows (verify nothing else references them; data-binding expressions for the removed ids must be deleted too, or the binding class won't compile).

- [ ] **Step 3: Populate chips + badge in the fragment**

In `TunnelDetailFragment.kt`, after the config is loaded (where `binding.config` is set, ~line 82), call a new helper. Add:

```kotlin
private fun populateObfuscation(config: org.amnezia.awg.config.Config) {
    val iface = config.`interface`
    // Protocol badge
    val badge = binding.protoBadge
    badge.text = when (org.amnezia.awg.config.ObfuscationMode.of(iface)) {
        org.amnezia.awg.config.ObfuscationMode.WG -> getString(R.string.proto_badge_wg)
        org.amnezia.awg.config.ObfuscationMode.AMNEZIA -> getString(R.string.proto_badge_amnezia)
        org.amnezia.awg.config.ObfuscationMode.PROXY -> getString(R.string.proto_badge_proxy)
    }
    // Param chips: name + value pairs that are actually set
    val chips = buildList {
        iface.junkPacketCount.ifPresent { add("Jc $it") }
        iface.junkPacketMinSize.ifPresent { add("Jmin $it") }
        iface.junkPacketMaxSize.ifPresent { add("Jmax $it") }
        iface.initPacketJunkSize.ifPresent { add("S1 $it") }
        iface.responsePacketJunkSize.ifPresent { add("S2 $it") }
        iface.cookieReplyPacketJunkSize.ifPresent { add("S3 $it") }
        iface.transportPacketJunkSize.ifPresent { add("S4 $it") }
        iface.initPacketMagicHeader.ifPresent { add("H1 $it") }
        iface.responsePacketMagicHeader.ifPresent { add("H2 $it") }
        iface.underloadPacketMagicHeader.ifPresent { add("H3 $it") }
        iface.transportPacketMagicHeader.ifPresent { add("H4 $it") }
        listOf(iface.specialJunkI1, iface.specialJunkI2, iface.specialJunkI3,
               iface.specialJunkI4, iface.specialJunkI5).forEachIndexed { i, opt ->
            opt.ifPresent { add("I${i + 1}") }
        }
    }
    val group = binding.obfuscationChips
    group.removeAllViews()
    val inflater = layoutInflater
    for (label in chips) {
        val tv = TextView(group.context).apply {
            text = label
            setBackgroundResource(R.drawable.bg_stat_chip)
            setTextAppearance(R.style.TextAppearance_Awg_Mono)
            val padH = resources.getDimensionPixelSize(R.dimen.space_sm)
            setPadding(padH, padH / 2, padH, padH / 2)
        }
        group.addView(tv)
    }
}
```

> `ChipGroup` accepts arbitrary child views and flows/wraps them; using `bg_stat_chip` TextViews keeps the asset's exact chip look (Mono, `surfaceVariant`) without Material Chip's interactive sizing and without a new dependency. Visual parity is an on-device review item.

Call `populateObfuscation(it)` wherever the loaded config is applied to `binding` (the `getConfigAsync()` success path).

- [ ] **Step 4: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL (data-binding class regenerates; no reference to removed ids).

- [ ] **Step 5: On-device check**

Install, open a tunnel detail. Expected: AWG params render as compact wrapping chips; a single badge (WG/Amnezia/Proxy) shows next to the name matching the config (e.g. an AmneziaWG config with only Jc/S/H → "Amnezia"; one with `ImitateProtocol` or `<…>` I-values → "Proxy"; a plain config → "WG").

- [ ] **Step 6: Commit**

```bash
git add ui/src/main/res/layout/tunnel_detail_fragment.xml ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt
git commit -m "refresh: detail obfuscation chips + WG/Amnezia/Proxy badge (phase 4)"
```

---

### Task 4: Detail — collapsible sections with persistence

**Files:**
- Modify: `ui/src/main/res/layout/tunnel_detail_fragment.xml` (Interface, Obfuscation, Peer cards)
- Modify: `ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt`

**Interfaces:**
- Consumes: `Application.getPreferencesDataStore()` → `DataStore<Preferences>`; `androidx.datastore.preferences.core.booleanPreferencesKey`; `@id/obfuscation_chips` (Task 3) as the collapsed preview source.
- Produces: per-section `@id/<x>_header`, `@id/<x>_body`, `@id/<x>_chevron`, `@id/obfuscation_summary`; Kotlin `bindCollapsibleSection(...)`.

- [ ] **Step 1: Restructure each section card header + body**

For each of the three section cards (Interface, Obfuscation, Peer) in `tunnel_detail_fragment.xml`, wrap the card contents in a vertical LinearLayout of a header row + a body, following `~/Documents/amneziawg-refresh-assets/res/layout/include_detail_section.reference.xml`. For the Obfuscation card:

```xml
<LinearLayout
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="vertical" android:animateLayoutChanges="true">

    <LinearLayout
        android:id="@+id/obfuscation_header"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="horizontal" android:gravity="center_vertical"
        android:minHeight="@dimen/touch_min"
        android:clickable="true" android:focusable="true"
        android:background="?attr/selectableItemBackground"
        android:paddingEnd="@dimen/space_xs">
        <TextView
            android:id="@+id/obfuscation_title"
            android:layout_width="0dp" android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textAppearance="@style/TextAppearance.Awg.SectionHeader"
            android:text="@string/section_obfuscation" />
        <TextView
            android:id="@+id/obfuscation_summary"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:layout_marginEnd="@dimen/space_sm"
            android:textAppearance="@style/TextAppearance.Awg.FieldLabel"
            tools:text="8 parameters" />
        <ImageView
            android:id="@+id/obfuscation_chevron"
            android:layout_width="24dp" android:layout_height="24dp"
            android:src="@drawable/ic_chevron_down"
            android:contentDescription="@null" />
    </LinearLayout>

    <LinearLayout
        android:id="@+id/obfuscation_body"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical" android:paddingTop="@dimen/space_sm">
        <!-- existing obfuscation_chips ChipGroup from Task 3 moves in here -->
    </LinearLayout>
</LinearLayout>
```

Repeat for Interface (`interface_header`/`interface_body`/`interface_chevron`, title `@string/section_interface`) and Peer (`peer_header`/`peer_body`/`peer_chevron`, title `@string/section_peer`). Interface/Peer have no summary TextView. Keep the existing field rows inside each `*_body`.

- [ ] **Step 2: Add the collapsible binder in the fragment**

In `TunnelDetailFragment.kt` add:

```kotlin
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private fun bindCollapsibleSection(
    id: String, header: View, body: View, chevron: View, defaultExpanded: Boolean
) {
    val key = booleanPreferencesKey("detail_section_${id}_expanded")
    val store = org.amnezia.awg.Application.getPreferencesDataStore()
    lifecycleScope.launch {
        val expanded = store.data.map { it[key] ?: defaultExpanded }.first()
        applySection(body, chevron, expanded, animate = false)
        header.setOnClickListener {
            val now = body.visibility != View.VISIBLE
            TransitionManager.beginDelayedTransition(body.parent as ViewGroup, AutoTransition())
            applySection(body, chevron, now, animate = true)
            lifecycleScope.launch { store.edit { it[key] = now } }
        }
    }
}

private fun applySection(body: View, chevron: View, expanded: Boolean, animate: Boolean) {
    body.visibility = if (expanded) View.VISIBLE else View.GONE
    val target = if (expanded) 180f else 0f
    if (animate) chevron.animate().rotation(target).setDuration(200).start()
    else chevron.rotation = target
}
```

- [ ] **Step 3: Wire the three sections**

Where the view is set up (`onViewCreated` / after binding), call:

```kotlin
bindCollapsibleSection("interface", binding.interfaceHeader, binding.interfaceBody, binding.interfaceChevron, defaultExpanded = true)
bindCollapsibleSection("obfuscation", binding.obfuscationHeader, binding.obfuscationBody, binding.obfuscationChevron, defaultExpanded = false)
bindCollapsibleSection("peer", binding.peerHeader, binding.peerBody, binding.peerChevron, defaultExpanded = true)
```

In `populateObfuscation()` (Task 3), set the collapsed summary count:

```kotlin
binding.obfuscationSummary.text = resources.getString(R.string.detail_section_count, chips.size)
```

- [ ] **Step 4: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: On-device check**

Open detail. Expected: Interface & Peer expanded, Obfuscation collapsed showing "N parameters"; tapping a header animates open/closed and rotates the chevron ~200ms; leave the screen and return — each section's state is remembered.

- [ ] **Step 6: Commit**

```bash
git add ui/src/main/res/layout/tunnel_detail_fragment.xml ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt
git commit -m "refresh: collapsible detail sections with persisted state (phase 4)"
```

---

### Task 5: Detail — resolved Server address (IP:port)

**Files:**
- Modify: `ui/src/main/res/layout/tunnel_detail_fragment.xml` (connection-summary card ~lines 38–132)
- Modify: `ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt` (`updateStats()` ~lines 104–160)

**Interfaces:**
- Consumes: `binding.config.peers` → `Peer.getEndpoint()` → `InetEndpoint.getResolved()`; the existing `updateStats()` poll loop.
- Produces: `@id/public_endpoint_text` populated on the connected summary card.

- [ ] **Step 1: Add the value view to the summary card**

In the connection-summary card, in the status row, add a right-aligned value:

```xml
<TextView
    android:id="@+id/public_endpoint_text"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textAppearance="@style/TextAppearance.Awg.Mono"
    android:textColor="?attr/colorOnSurface"
    tools:text="201.51.6.117:443" />
```

(Place it per `SPEC-detail-editor-split.md` §1 "Connection summary card" — Row 1, right of the status label.)

- [ ] **Step 2: Resolve and set it off the main thread**

In `TunnelDetailFragment.kt`, the poll loop currently launches on the main dispatcher. Add the resolve on `Dispatchers.IO` and post back. Inside `updateStats()` (or alongside it in the loop), add:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private suspend fun updatePublicEndpoint() {
    val config = binding.config ?: return
    val peer = config.peers.firstOrNull() ?: return
    val text = withContext(Dispatchers.IO) {
        val configured = peer.endpoint
        configured.flatMap { it.resolved }            // InetEndpoint.getResolved()
            .map { it.toString() }                    // host:port
            .orElseGet { configured.map { it.toString() }.orElse("") }
    }
    binding.publicEndpointText.text = text
    binding.publicEndpointText.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
}
```

Call `updatePublicEndpoint()` from the poll loop (next to `updateStats()`), only meaningful when connected — the summary card is already gated on connection state, so it is fine to call unconditionally and let the empty-string branch hide it.

> `InetEndpoint.getResolved()` performs DNS (cached ~1 min) and must not run on the main thread — hence `Dispatchers.IO`. Kotlin sees the Java getter `getResolved()` as the property `resolved`.

- [ ] **Step 3: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: On-device check**

Connect a tunnel, open detail. Expected: the summary card shows the resolved `IP:port` next to the status; when disconnected the card (and value) is gone. A hostname endpoint resolves to a numeric IP; if resolution fails it falls back to the configured `host:port`.

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/res/layout/tunnel_detail_fragment.xml ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt
git commit -m "refresh: resolved Server address (IP:port) on detail summary (phase 4)"
```

---

### Task 6: Editor — obfuscation fields 2–3 per row

**Files:**
- Modify: `ui/src/main/res/layout/tunnel_editor_fragment.xml` (obfuscation card ~lines 273–646)

**Interfaces:**
- Consumes: existing `*_layout` `TextInputLayout` ids (unchanged ids, unchanged bindings).
- Produces: same fields, regrouped horizontally.

- [ ] **Step 1: Regroup the inputs**

In the editor's obfuscation card, lay the existing `TextInputLayout` fields out 2–3 per horizontal row (constraint chains or nested horizontal `LinearLayout`s with `layout_weight`), `@dimen/space_sm` horizontal gap, keeping the OBFUSCATION header. Suggested groupings: `junk_packet_min_size_layout` + `junk_packet_max_size_layout`; the four `*_packet_junk_size_layout`; the four `*_packet_magic_header_layout`; `special_junk_i1..i5_layout`. **Keep every field id and every data-binding expression intact** — only the container/positioning changes. Preserve `nextFocus*` chains (re-point them to follow the new visual order).

- [ ] **Step 2: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: On-device check**

Open the editor. Expected: obfuscation inputs are packed 2–3 per row, all legible (no black-on-black), all fields still editable and saving correctly; d-pad/tab focus moves sensibly on TV.

- [ ] **Step 4: Commit**

```bash
git add ui/src/main/res/layout/tunnel_editor_fragment.xml
git commit -m "refresh: editor obfuscation fields grouped 2-3 per row (phase 4)"
```

---

### Task 7: Split-tunnel summary (logic TDD + binding)

**Files:**
- Create: `tunnel/src/main/java/org/amnezia/awg/config/SplitTunnelSummary.java`
- Test: `tunnel/src/test/java/org/amnezia/awg/config/SplitTunnelSummaryTest.java`
- Modify: `ui/src/main/java/org/amnezia/awg/fragment/TunnelEditorFragment.kt`
- Modify: `ui/src/main/res/layout/tunnel_editor_fragment.xml` (the `@id/split_tunnel_summary` TextView already exists in the entry row)

**Interfaces:**
- Consumes: `Interface.getIncludedApplications()`, `getExcludedApplications()` (Sets); installed-app total from the editor.
- Produces: `SplitTunnelSummary.isAllApps(int,int)` and `routedCount(int,int,int)`; bound text on `@id/split_tunnel_summary`.

- [ ] **Step 1: Write the failing test**

Create `tunnel/src/test/java/org/amnezia/awg/config/SplitTunnelSummaryTest.java`:

```java
package org.amnezia.awg.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SplitTunnelSummaryTest {
    @Test public void noneSelected_isAllApps() {
        assertTrue(SplitTunnelSummary.isAllApps(0, 0));
        assertEquals(86, SplitTunnelSummary.routedCount(0, 0, 86));
    }
    @Test public void includeMode_routesOnlySelected() {
        assertFalse(SplitTunnelSummary.isAllApps(2, 0));
        assertEquals(2, SplitTunnelSummary.routedCount(2, 0, 86));
    }
    @Test public void excludeMode_routesRemainder() {
        assertFalse(SplitTunnelSummary.isAllApps(0, 5));
        assertEquals(81, SplitTunnelSummary.routedCount(0, 5, 86));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :tunnel:test --tests org.amnezia.awg.config.SplitTunnelSummaryTest`
Expected: FAIL — `SplitTunnelSummary` does not exist.

- [ ] **Step 3: Write the implementation**

Create `tunnel/src/main/java/org/amnezia/awg/config/SplitTunnelSummary.java`:

```java
package org.amnezia.awg.config;

/** Pure helpers for the split-tunnel "N of M apps routed" entry-row summary. */
public final class SplitTunnelSummary {
    private SplitTunnelSummary() {}

    public static boolean isAllApps(final int includedCount, final int excludedCount) {
        return includedCount == 0 && excludedCount == 0;
    }

    public static int routedCount(final int includedCount, final int excludedCount, final int totalApps) {
        if (includedCount > 0) return includedCount;
        if (excludedCount > 0) return Math.max(0, totalApps - excludedCount);
        return totalApps;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :tunnel:test --tests org.amnezia.awg.config.SplitTunnelSummaryTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Bind the summary text in the editor**

In `TunnelEditorFragment.kt`, where the split-tunnel entry row / app selection is set up and whenever the selection changes, set:

```kotlin
private fun updateSplitTunnelSummary(iface: org.amnezia.awg.config.Interface, totalApps: Int) {
    val inc = iface.includedApplications.size
    val exc = iface.excludedApplications.size
    binding.splitTunnelSummary.text = if (org.amnezia.awg.config.SplitTunnelSummary.isAllApps(inc, exc))
        getString(R.string.split_tunnel_all_apps)
    else
        getString(R.string.split_tunnel_summary,
            org.amnezia.awg.config.SplitTunnelSummary.routedCount(inc, exc, totalApps), totalApps)
}
```

`totalApps` = the installed-app count the editor already enumerates for the app picker; pass that value. Call this on load and after returning from the app-selection UI.

- [ ] **Step 6: Build + verify**

Run: `./gradlew :tunnel:test --console=plain` then `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: tests PASS; app builds. On device: the Applications row reads "All apps routed" by default and "N of M apps routed" after excluding/including apps.

- [ ] **Step 7: Commit**

```bash
git add tunnel/src/main/java/org/amnezia/awg/config/SplitTunnelSummary.java tunnel/src/test/java/org/amnezia/awg/config/SplitTunnelSummaryTest.java ui/src/main/java/org/amnezia/awg/fragment/TunnelEditorFragment.kt ui/src/main/res/layout/tunnel_editor_fragment.xml
git commit -m "refresh: live split-tunnel 'N of M apps routed' summary (phase 4)"
```

---

### Task 8: TV row — stats-strip parity

**Files:**
- Modify: `ui/src/main/res/layout/tv_tunnel_list_item.xml`

**Interfaces:**
- Consumes: `@layout/include_tunnel_stats` (existing); the list poller `refreshStatistics()` in `TunnelListFragment.kt` (already updates all UP tunnels).

- [ ] **Step 1: Include the strip**

In `tv_tunnel_list_item.xml`, at the same position the phone row uses (below the name/status block), add:

```xml
<include layout="@layout/include_tunnel_stats" />
```

Ensure the strip's root is non-focusable so d-pad never lands in it — if `include_tunnel_stats.xml`'s root is not already `focusable="false"`, set on the `<include>` parent or the strip root:

```xml
android:focusable="false"
android:descendantFocusability="blocksDescendants"
```

Keep the TV row's existing `nextFocusUp`/`nextFocusDown` pointing at sibling rows. Verify `include_tunnel_stats` view ids resolve through the TV row's binding (the data variable name must match the phone row's).

- [ ] **Step 2: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: On-device/emulator check (TV)**

On a TV layout: connected rows show the live down/up/handshake strip; d-pad focus moves row→row and never into the strip; status colors match the phone row.

- [ ] **Step 4: Commit**

```bash
git add ui/src/main/res/layout/tv_tunnel_list_item.xml
git commit -m "refresh: TV row live stats-strip parity (phase 4)"
```

---

### Task 9: App rename

**Files:**
- Modify: `ui/src/main/res/values/strings.xml`
- Modify: `ui/src/main/AndroidManifest.xml` (line 44 — launcher label)
- Modify: `ui/src/main/java/org/amnezia/awg/QuickTileService.kt` (line ~149)
- Modify: the main toolbar/header title source (in-app header)

**Interfaces:**
- Produces: `app_name` = "AWG Proxy" (launcher/short), `app_title` = "AmneziaWG Proxy" (in-app header).

- [ ] **Step 1: Update the strings**

In `ui/src/main/res/values/strings.xml`:

```xml
<string name="app_name" translatable="false">AWG Proxy</string>
<string name="app_title" translatable="false">AmneziaWG Proxy</string>
```

(Change the existing `app_name` value; add `app_title`.)

- [ ] **Step 2: Point the in-app header at `app_title`**

Find where the main screen toolbar title is set (the tunnel-list toolbar / `main_activity` toolbar — search for the toolbar title in `tunnel_list_fragment.xml` / `main_activity.xml` / the relevant fragment). Set its title to `@string/app_title`. Leave the launcher label (`AndroidManifest.xml:44 android:label="@string/app_name"`) and the Quick Tile label (`QuickTileService.kt:149 getString(R.string.app_name)`) on `app_name` ("AWG Proxy") — those are external short labels.

> Audit: re-grep `R.string.app_name` and `@string/app_name` after editing to confirm each remaining use is an external short label, not the in-app header. Map any newly-found use to the correct string.

- [ ] **Step 3: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: On-device check**

Launcher icon label reads "AWG Proxy"; the app's main header reads "AmneziaWG Proxy"; Quick Settings tile reads "AWG Proxy".

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/res/values/strings.xml ui/src/main/AndroidManifest.xml ui/src/main/java/org/amnezia/awg/QuickTileService.kt
git commit -m "refresh: rename — launcher 'AWG Proxy', header 'AmneziaWG Proxy' (phase 4)"
```

---

## Self-Review

**Spec coverage:** §4.0 → Task 1. §4.1 collapsible → Task 4. §4.2 chips+badge → Tasks 2+3. §4.3 editor grouping → Task 6. §4.4 IP:port → Task 5. §4.5 split summary → Task 7. §4.6 TV → Task 8. §4.7 rename → Task 9. All spec sections mapped.

**Open confirmations from spec:** O-1 ("Proxy" label) resolved in Task 1 strings. O-2 (flexbox) resolved — use ChipGroup, no dep (Task 3). O-3 (badge placement) resolved — beside tunnel name (Task 3 Step 1). O-4 (`<…>` sentinel) carried as Task 2 Step 0 confirm.

**Type consistency:** `ObfuscationMode.of(Interface)` defined Task 2, consumed Task 3. `SplitTunnelSummary.isAllApps/routedCount` defined Task 7, consumed same task. View ids (`obfuscation_chips`, `obfuscation_body`, `proto_badge`, `public_endpoint_text`, `split_tunnel_summary`) consistent across layout + Kotlin references.

**Known risks to watch during execution:** removing obfuscation row ids in Task 3 must also remove their data-binding expressions or the binding class won't compile; Task 2 Step 0 must confirm parse keys; Task 4 reads DataStore async so first paint uses the default until the stored value resolves (acceptable).
