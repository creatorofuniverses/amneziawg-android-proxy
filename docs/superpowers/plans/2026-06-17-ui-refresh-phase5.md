# UI Refresh Phase 5 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the detail + editor screens to the approved mockups, fix list/TV row spacing, and replace the stock app-picker with the designed full-screen split-tunneling panel.

**Architecture:** All XML layouts + Kotlin fragment glue in the `:ui` module; no Compose, no new dependency. Reuses existing styles (`TextAppearance.Awg.*`), tokens, and the phase-4 collapsible binder / `ObfuscationMode` / `SplitTunnelSummary`. The split-tunnel work restyles the existing `AppListDialogFragment` while preserving its `REQUEST_SELECTION` result contract.

**Tech Stack:** Android XML layouts, Material Components 1.11.0, data binding, Kotlin, AndroidX Fragment/DataStore.

**Spec:** `docs/superpowers/specs/2026-06-17-ui-refresh-phase5-design.md`

## Global Constraints

- XML + **Material Components 1.11.0**, **no Compose**, `minSdk 24`. **No new dependency; no flexbox.**
- Light + dark + **TV** intact; **preserve every existing `nextFocus*` and `contentDescription`**.
- Do **not** remove/hide any AmneziaWG field or its data-binding (`@{...}`/`@={...}`) expression — only restructure presentation. A clean data-binding compile is the proof.
- New strings go in default `ui/src/main/res/values/strings.xml`, short copy.
- Reuse existing styles/tokens: `TextAppearance.Awg.{SectionHeader,FieldLabel,FieldValue,Mono,Mono.Accent,Status,StatLabel,TunnelName}`; colors `awg_connected_fill`/`awg_connected_stroke`, `tunnel_status_connected/connecting/disconnected`; attrs `colorPrimary`, `colorPrimaryContainer`, `colorOnPrimaryContainer`, `colorOutlineVariant`, `colorOnSurface`, `colorOnSurfaceVariant`; drawable `ic_chevron_down`; dimens `space_xs/sm/lg`, `screen_padding`, `card_gap`, `touch_min`.
- **Verify command** (full APK needs NDK + go submodule, does NOT build here):
  `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain` → BUILD SUCCESSFUL, no new warnings. SDK at `~/android-sdk`. Tunnel tests: `./gradlew :tunnel:test --console=plain`.
- Layout/Kotlin tasks have **no unit test**; verification = clean compile + an on-device check by Vitaly per task acceptance line (he reviews each batch and catches real bugs — wait for feedback).
- **Asset-pack caveat:** `~/Documents/amneziawg-refresh-assets` is a proposal merged against code reality. In particular `res/layout/include_detail_section.reference.xml` is **STALE** — it shows the section header *inside* the card (the phase-4 pattern the user is rejecting). The authoritative source for the detail sections is `SPEC-detail-anatomy.md` (label OUTSIDE the card). Follow the spec/this plan, not that reference file.
- **Pre-existing unrelated failure** (do not attribute to this phase): `:tunnel` `BadConfigExceptionTest.throws_correctly_with_SYNTAX_ERROR_reason`.

---

### Task 1: Foundation — new icons + strings

**Files:**
- Create: `ui/src/main/res/drawable/ic_edit.xml`
- Create: `ui/src/main/res/drawable/ic_save.xml`
- Modify: `ui/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: drawables `@drawable/ic_edit`, `@drawable/ic_save`; strings `split_tunnel_title`, `split_tunnel_search_hint`, `split_tunnel_mode_helper`, `split_tunnel_include`, `split_tunnel_exclude`.

- [ ] **Step 1: Copy the two drawables verbatim**

Copy `~/Documents/amneziawg-refresh-assets/res/drawable/ic_edit.xml` → `ui/src/main/res/drawable/ic_edit.xml` and `~/Documents/amneziawg-refresh-assets/res/drawable/ic_save.xml` → `ui/src/main/res/drawable/ic_save.xml`, unchanged.

- [ ] **Step 2: Add strings**

Add inside `<resources>` of `ui/src/main/res/values/strings.xml` (only add those not already present — `split_tunnel_summary`, `split_tunnel_all_apps`, `section_*`, `detail_*` already exist from phase 4; check first):

```xml
<string name="split_tunnel_title">Split tunneling</string>
<string name="split_tunnel_search_hint">Search apps</string>
<string name="split_tunnel_mode_helper">Choose which apps use the tunnel.</string>
<string name="split_tunnel_include">Include apps</string>
<string name="split_tunnel_exclude">Exclude apps</string>
```

- [ ] **Step 3: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add ui/src/main/res/drawable/ic_edit.xml ui/src/main/res/drawable/ic_save.xml ui/src/main/res/values/strings.xml
git commit -m "refresh: phase-5 foundation — edit/save icons + split-tunnel strings"
```

---

### Task 2: Detail — section labels OUTSIDE cards + AMNEZIA pill on Obfuscation (Approach A)

**Files:**
- Modify: `ui/src/main/res/layout/tunnel_detail_fragment.xml` (the three section blocks: Interface lines ~147-487, Obfuscation ~489-562, Peer ~564-630)
- Modify: `ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt` (`onViewCreated` wiring ~63-70, `populateObfuscation` ~145-186)

**Interfaces:**
- Consumes: phase-4 `bindCollapsibleSection(id: String, header: View, body: View, chevron: View, defaultExpanded: Boolean)` (TunnelDetailFragment.kt:121); `ObfuscationMode.of(iface)`; data-binding vars `config`/`tunnel`/`fragment`.
- Produces: per section a label-row id (`interface_header`/`obfuscation_header`/`peer_header`) OUTSIDE the card, a card id (`interface_card`/`obfuscation_card`/`peer_card`) as the collapsible body, chevrons (`*_chevron`) and titles (`*_title`) on the label row; `proto_badge` relocated onto the obfuscation label row; `obfuscation_summary` on the obfuscation label row.

**Restructure pattern (apply per section).** Today each section is `MaterialCardView` → `[header row (title+chevron, +switch for interface) ]` + `[body]`, header INSIDE the card. Change to: a vertical wrapper holding a **label row OUTSIDE** + the **card** (card = body only). The label row is the collapse tap target; the binder hides the **whole card** when collapsed (label row stays visible).

- [ ] **Step 1: Restructure the Interface section**

Replace the Interface section so the title/switch/chevron sit in a label row above the card:

```xml
<!-- INTERFACE: label row (outside) + card (body) -->
<LinearLayout
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="vertical"
    android:layout_marginHorizontal="@dimen/screen_padding"
    android:layout_marginBottom="@dimen/card_gap"
    app:layout_constraintTop_toBottomOf="@id/connection_summary_card"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent">

    <LinearLayout
        android:id="@+id/interface_header"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="horizontal" android:gravity="center_vertical"
        android:minHeight="@dimen/touch_min"
        android:clickable="true" android:focusable="true"
        android:background="?attr/selectableItemBackground"
        android:paddingStart="@dimen/space_xs" android:paddingEnd="@dimen/space_xs">
        <TextView
            android:id="@+id/interface_title"
            android:layout_width="0dp" android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textAppearance="@style/TextAppearance.Awg.SectionHeader"
            android:text="@string/section_interface" />
        <!-- the existing tunnel enable switch rides the label row -->
        <org.amnezia.awg.widget.ToggleSwitch
            android:id="@+id/tunnel_switch"
            ...keep ALL existing attributes/bindings verbatim (app:checked, app:onBeforeCheckedChanged, nextFocus*, contentDescription)... />
        <ImageView
            android:id="@+id/interface_chevron"
            android:layout_width="24dp" android:layout_height="24dp"
            android:src="@drawable/ic_chevron_down"
            android:contentDescription="@null" />
    </LinearLayout>

    <com.google.android.material.card.MaterialCardView
        android:id="@+id/interface_card"
        style="@style/AmneziaWgTheme.MaterialCardView"
        android:layout_width="match_parent" android:layout_height="wrap_content">
        <LinearLayout
            android:id="@+id/interface_body"
            android:layout_width="match_parent" android:layout_height="wrap_content"
            android:orientation="vertical">
            <!-- existing field rows move here unchanged (Task 3 adds dividers/styling).
                 Convert the ConstraintLayout body to a vertical LinearLayout of rows;
                 keep EVERY id + @{...}/@={...} binding + nextFocus* + contentDescription. -->
        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>
</LinearLayout>
```

Move the existing interface field rows (`interface_name_label`/`interface_name_text`, `public_key_*`, `addresses_*`, `dns_servers_*`, `dns_search_domains_*`, `listen_port_*`, `mtu_*`, `applications_*`, `imitate_protocol_*`) into `interface_body`. **Remove `proto_badge` from the interface body** (it moves to the Obfuscation label row in Step 2). Preserve all bindings and focus attrs.

- [ ] **Step 2: Restructure the Obfuscation section (label row carries the pill + summary)**

```xml
<LinearLayout android:orientation="vertical"
    android:layout_marginHorizontal="@dimen/screen_padding"
    android:layout_marginBottom="@dimen/card_gap"
    app:layout_constraintTop_toBottomOf="@id/interface_card_wrapper_or_section" ...>

    <LinearLayout
        android:id="@+id/obfuscation_header"
        ...horizontal, center_vertical, minHeight=touch_min, clickable/focusable, selectableItemBackground, paddingStart/End=space_xs...>
        <TextView
            android:id="@+id/obfuscation_title"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:textAppearance="@style/TextAppearance.Awg.SectionHeader"
            android:text="@string/section_obfuscation" />
        <!-- AMNEZIA / PROXY pill, immediately right of the title -->
        <TextView
            android:id="@+id/proto_badge"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:layout_marginStart="@dimen/space_sm"
            android:background="@drawable/bg_proto_pill"
            android:paddingHorizontal="@dimen/space_sm" android:paddingVertical="2dp"
            android:textAppearance="@style/TextAppearance.Awg.StatLabel"
            android:textColor="?attr/colorOnPrimaryContainer"
            tools:text="AMNEZIA" />
        <Space android:layout_width="0dp" android:layout_height="0dp" android:layout_weight="1" />
        <TextView
            android:id="@+id/obfuscation_summary"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:layout_marginEnd="@dimen/space_sm"
            android:textAppearance="@style/TextAppearance.Awg.FieldLabel"
            tools:text="8 parameters" />
        <ImageView android:id="@+id/obfuscation_chevron" android:layout_width="24dp" android:layout_height="24dp"
            android:src="@drawable/ic_chevron_down" android:contentDescription="@null" />
    </LinearLayout>

    <com.google.android.material.card.MaterialCardView
        android:id="@+id/obfuscation_card" style="@style/AmneziaWgTheme.MaterialCardView" ...
        keep the card-level visibility @{...} binding that hides the card when no obfuscation params...>
        <LinearLayout android:id="@+id/obfuscation_body" android:orientation="vertical" ...>
            <com.google.android.material.chip.ChipGroup android:id="@+id/obfuscation_chips" ... />  <!-- unchanged -->
        </LinearLayout>
    </com.google.android.material.card.MaterialCardView>
</LinearLayout>
```

Create the pill background `ui/src/main/res/drawable/bg_proto_pill.xml` (full-radius `colorPrimaryContainer` fill):

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="?attr/colorPrimaryContainer" />
    <corners android:radius="999dp" />
</shape>
```

- [ ] **Step 3: Restructure the Peer section** the same way (label row `peer_header` with `peer_title` + `peer_chevron` outside; `peer_card` wrapping `peer_body`/`peers_layout`). No switch, no pill. Keep `peers_layout` binding-adapter attrs (`app:items`, `app:layout`) intact.

- [ ] **Step 4: Rebind the collapse in the fragment**

In `TunnelDetailFragment.kt` `onViewCreated` (~lines 63-70), pass the **card** as the collapsible body (so collapsing hides the whole card, the label row stays):

```kotlin
bindCollapsibleSection("interface", b.interfaceHeader, b.interfaceCard, b.interfaceChevron, defaultExpanded = true)
bindCollapsibleSection("obfuscation", b.obfuscationHeader, b.obfuscationCard, b.obfuscationChevron, defaultExpanded = false)
bindCollapsibleSection("peer", b.peerHeader, b.peerCard, b.peerChevron, defaultExpanded = true)
```

`bindCollapsibleSection`/`applySection` are unchanged (they already toggle the passed `body` View's visibility and animate `body.parent`). `populateObfuscation` already sets `b.protoBadge.text`/`b.obfuscationSummary.text`/chips — unchanged (the ids still resolve, now on the label row). Confirm `TransitionManager.beginDelayedTransition(body.parent as ViewGroup, ...)` scope (`body.parent` = the section's vertical wrapper) animates cleanly.

- [ ] **Step 5: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: BUILD SUCCESSFUL, no new warnings (a clean data-binding compile proves no field/binding was dropped).

- [ ] **Step 6: On-device check (Vitaly)**

Open a tunnel's detail. Expected: each section's name is a teal CAPS label ABOVE its card (not inside); the AMNEZIA/PROXY pill rides the OBFUSCATION label (not Interface); tapping a label row collapses/expands its card with the chevron rotating; Interface/Peer expanded, Obfuscation collapsed showing "N parameters"; state persists across navigation.

- [ ] **Step 7: Commit**

```bash
git add ui/src/main/res/layout/tunnel_detail_fragment.xml ui/src/main/res/drawable/bg_proto_pill.xml ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt
git commit -m "refresh: detail section labels outside cards + AMNEZIA pill on obfuscation (phase 5)"
```

---

### Task 3: Detail — two-line field rows with dividers

**Files:**
- Modify: `ui/src/main/res/layout/tunnel_detail_fragment.xml` (the `interface_body` and `peer_body` rows)

**Interfaces:**
- Consumes: the `*_body` LinearLayouts from Task 2.
- Produces: each field as a two-line row (label over value) with a 1dp `colorOutlineVariant` divider between rows.

- [ ] **Step 1: Convert each interface/peer field to a two-line row + divider**

For each existing field (label+value already exist as separate TextViews from phase 4), structure each row as a vertical block and insert a divider `View` between rows (not after the last). Canonical row:

```xml
<LinearLayout
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:orientation="vertical" android:paddingVertical="@dimen/space_sm"
    android:paddingHorizontal="@dimen/space_md">
    <TextView
        android:id="@+id/addresses_label"
        android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:textAppearance="@style/TextAppearance.Awg.FieldLabel"
        android:text="@string/detail_interface_addresses" />
    <TextView
        android:id="@+id/addresses_text"
        android:layout_width="match_parent" android:layout_height="wrap_content"
        android:textAppearance="@style/TextAppearance.Awg.Mono"
        android:singleLine="true" android:ellipsize="end"
        android:text="@{...existing binding verbatim...}" />
</LinearLayout>
<!-- divider between rows; OMIT after the last row -->
<View
    android:layout_width="match_parent" android:layout_height="1dp"
    android:background="?attr/colorOutlineVariant" />
```

Apply to: Interface (name, public_key [Mono + copy affordance], addresses [Mono], dns_servers, dns_search_domains, listen_port, mtu, applications, imitate_protocol) and Peer (endpoint [Mono], allowed_ips [Mono], keepalive, latest handshake). Keep every id + binding; values that are keys/IPs use `Mono`, others `FieldValue`. Preserve `nextFocus*`/`contentDescription`. (Listen port / MTU may stay on one shared row if desired — keep their current pairing.)

- [ ] **Step 2: Build** — `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain` → BUILD SUCCESSFUL.

- [ ] **Step 3: On-device check (Vitaly)** — Interface/Peer sections read as labeled two-line rows with hairline dividers, not a plain text blob; no value clipped; copy affordance on the public key works.

- [ ] **Step 4: Commit**

```bash
git add ui/src/main/res/layout/tunnel_detail_fragment.xml
git commit -m "refresh: detail interface/peer two-line field rows with dividers (phase 5)"
```

---

### Task 4: Detail — connection-summary card restyle

**Files:**
- Modify: `ui/src/main/res/layout/tunnel_detail_fragment.xml` (connection summary card, ids `connection_summary_card`, `summary_status_dot`, `summary_status`, `public_endpoint_text`, `summary_transfer`, `summary_handshake`)

**Interfaces:**
- Consumes: existing status/stat bindings + phase-4 `public_endpoint_text` population.
- Produces: a row-1 (dot + Status-styled label + right-aligned `IP:port`) and a row-2 3-column DOWNLOAD/UPLOAD/HANDSHAKE grid.

- [ ] **Step 1: Restyle to spec** (`SPEC-detail-anatomy.md §4`)

Card: `awg_connected_fill` bg + 1dp `awg_connected_stroke` + 16dp radius. Row 1: 8dp `summary_status_dot` (`@drawable/dot_status` tinted by status) + `space_sm` gap + `summary_status` using `@style/TextAppearance.Awg.Status` with the status color + `public_endpoint_text` pushed to the end (`Mono`, `colorOnSurfaceVariant`). Row 2 (12dp below): a 3-column grid, `space_lg` gap, each column = `StatLabel` (DOWNLOAD/UPLOAD/HANDSHAKE) over a value; Download/Upload values use `Mono.Accent`, Handshake uses `Mono`. Keep all existing stat bindings (`summary_transfer`, `summary_handshake`) — re-lay them into the 3-column grid; do not drop any binding. Card stays gated on connected/connecting (existing visibility binding).

- [ ] **Step 2: Build** → BUILD SUCCESSFUL.

- [ ] **Step 3: On-device check (Vitaly)** — connected: card shows colored dot + "Connected" in the list's status style + resolved IP:port on the right; a DOWNLOAD/UPLOAD/HANDSHAKE row with teal rates; disconnected: card gone.

- [ ] **Step 4: Commit**

```bash
git add ui/src/main/res/layout/tunnel_detail_fragment.xml
git commit -m "refresh: connection-summary card restyle — status + IP + 3-col stats (phase 5)"
```

---

### Task 5: Toolbar — detail edit pencil + editor Save filled button

**Files:**
- Modify: `ui/src/main/res/menu/tunnel_detail.xml` (item `menu_action_edit`)
- Modify: `ui/src/main/res/menu/config_editor.xml` (item `menu_action_save`)
- Create: `ui/src/main/res/layout/action_save_button.xml` (filled Save button action view)
- Modify: `ui/src/main/java/org/amnezia/awg/fragment/TunnelEditorFragment.kt` (`onViewCreated` ~128, `onMenuItemSelected` ~155)

**Interfaces:**
- Consumes: existing edit handler (MainActivity:81-88) and save handler (`TunnelEditorFragment.onMenuItemSelected`, `R.id.menu_action_save`).
- Produces: detail edit icon = `@drawable/ic_edit`; editor save = a filled MaterialButton action view that triggers the same save path.

- [ ] **Step 1: Point the detail edit item at the new pencil**

In `ui/src/main/res/menu/tunnel_detail.xml`, change `menu_action_edit`'s `android:icon` to `@drawable/ic_edit` (keep `showAsAction="always"`, title, id).

- [ ] **Step 2: Editor Save as a filled button action view**

Create `ui/src/main/res/layout/action_save_button.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.button.MaterialButton
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/action_save_button"
    style="@style/Widget.Material3.Button"
    android:layout_width="wrap_content" android:layout_height="wrap_content"
    android:layout_marginEnd="@dimen/space_sm"
    android:text="@string/save"
    app:icon="@drawable/ic_save" />
```

In `ui/src/main/res/menu/config_editor.xml`, give `menu_action_save` `app:actionLayout="@layout/action_save_button"` (keep `showAsAction="always"`, id, title for accessibility).

- [ ] **Step 3: Wire the action-view click to the existing save path**

In `TunnelEditorFragment.onViewCreated` (after `addMenuProvider`, ~line 128), find the menu item's action view and route its click through the existing handler:

```kotlin
// in onPrepareMenu or after the menu is created:
val saveItem = menu.findItem(R.id.menu_action_save)
saveItem.actionView?.findViewById<View>(R.id.action_save_button)?.setOnClickListener {
    onMenuItemSelected(saveItem)
}
```

(Implement via `MenuProvider.onPrepareMenu` so the action view exists. `onMenuItemSelected` already performs the save when `itemId == R.id.menu_action_save`.) If wiring the action-view click proves awkward, fall back to a plain `android:icon="@drawable/ic_save"` on the menu item (claude-design documents the filled button as *preferred*, the icon as acceptable) — note the fallback in the report.

- [ ] **Step 4: Build** → BUILD SUCCESSFUL.

- [ ] **Step 5: On-device check (Vitaly)** — detail toolbar shows the new pencil; editor toolbar shows a filled "Save" button that saves the tunnel (rename/create/edit all still work).

- [ ] **Step 6: Commit**

```bash
git add ui/src/main/res/menu/tunnel_detail.xml ui/src/main/res/menu/config_editor.xml ui/src/main/res/layout/action_save_button.xml ui/src/main/java/org/amnezia/awg/fragment/TunnelEditorFragment.kt
git commit -m "refresh: detail edit pencil + editor filled Save button (phase 5)"
```

---

### Task 6: Editor — obfuscation long fields full-width

**Files:**
- Modify: `ui/src/main/res/layout/tunnel_editor_fragment.xml` (obfuscation card, rows E-H, lines ~480-709)

**Interfaces:**
- Consumes: existing `*_layout`/`*_text` field ids + bindings (unchanged).
- Produces: the 4 magic-header fields and 5 special-junk fields each on their own full-width row; short numerics stay grouped.

- [ ] **Step 1: Make the long fields full-width**

Keep grouped (unchanged): Row A `junk_packet_count`; Row B `junk_packet_min_size` + `junk_packet_max_size`; Rows C/D the four `*_packet_junk_size` (2+2). Change **each** of these to its own full-width row (`TextInputLayout` `layout_width="match_parent"`, drop the weighted horizontal `LinearLayout` wrappers for these): `init_packet_magic_header_layout`, `response_packet_magic_header_layout`, `underload_packet_magic_header_layout`, `transport_packet_magic_header_layout`, `special_junk_i1_layout` … `special_junk_i5_layout`. Keep every `*_layout`/`*_text` id, hint, inputType, and `@={...}` binding verbatim — only the container/width changes.

- [ ] **Step 2: Re-point the `nextFocus*` chain to the new linear order**

New visual order (top→bottom): junk_packet_count → (min,max) → (init_junk,response_junk) → (cookie_junk,transport_junk) → init_magic → response_magic → underload_magic → transport_magic → i1 → i2 → i3 → i4 → i5 → `split_tunnel_entry`. Set `nextFocusForward`/`nextFocusDown` to follow this order and `nextFocusUp` the reverse; for the still-grouped rows keep left→right `nextFocusForward`. Every `nextFocus*` target id must exist.

- [ ] **Step 3: Build** → BUILD SUCCESSFUL (clean data-binding compile proves no field/binding dropped).

- [ ] **Step 4: On-device check (Vitaly)** — in the editor's Obfuscation card the magic-header and I1–I5 fields each span the full width; Jc/Jmin/Jmax and the junk-size fields stay grouped; d-pad/tab focus flows sensibly to the split-tunnel entry.

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/res/layout/tunnel_editor_fragment.xml
git commit -m "refresh: editor obfuscation magic-header + special-junk fields full-width (phase 5)"
```

---

### Task 7: List + TV — center status in the name↔divider gap

**Files:**
- Modify: `ui/src/main/res/layout/tunnel_list_item.xml` (`tunnel_info_layout`, `tunnel_name`, `tunnel_status_row` ~line 90-115)
- Modify: `ui/src/main/res/layout/tv_tunnel_list_item.xml` (`tunnel_name` block)

**Interfaces:**
- Consumes: existing name/status ids + `include_tunnel_stats`.
- Produces: more vertical breathing room so "Connected" sits centered between the name and the divider/stats below.

- [ ] **Step 1: Increase the name→status gap (phone)**

In `tunnel_list_item.xml`, the status row (`tunnel_status_row`) currently has `layout_marginTop="2dp"`. Increase it so the status label sits visually centered in the gap between the name and the divider — set `android:layout_marginTop="@dimen/space_md"` and add `android:layout_marginBottom="@dimen/space_xs"` on the status row (starting values; Vitaly tunes on-device). The header row keeps `gravity="center_vertical"` so the toggle stays centered against the taller name/status block. Do NOT re-introduce a `paddingTop` on the stats include (the README warns this caused the old double-gap bug).

- [ ] **Step 2: Apply matching vertical rhythm on TV**

In `tv_tunnel_list_item.xml`, give the `tunnel_name`/content block the same comfortable spacing (add top spacing above the transfer/stats content so the name isn't cramped). The TV row presents status via the shared stats strip + status tokens (phase-4 Task 8); keep `nextFocus`/focus model unchanged. Mark this as the item most needing the on-device TV check.

- [ ] **Step 3: Build** → BUILD SUCCESSFUL.

- [ ] **Step 4: On-device check (Vitaly)** — phone: in the connected state "Connected" is vertically centered between the name and the divider, not hugging the name; disconnected rows look balanced (slightly taller is fine). TV: name has breathing room, grid uniform.

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/res/layout/tunnel_list_item.xml ui/src/main/res/layout/tv_tunnel_list_item.xml
git commit -m "refresh: center connected status in list/TV row name-divider gap (phase 5)"
```

---

### Task 8: Split-tunnel panel — full-screen container + segmented Include/Exclude + restyled rows

**Files:**
- Modify: `ui/src/main/java/org/amnezia/awg/fragment/AppListDialogFragment.kt`
- Modify: `ui/src/main/res/layout/app_list_dialog_fragment.xml`
- Modify: `ui/src/main/res/layout/app_list_item.xml`

**Interfaces:**
- Consumes: existing adapter (`ObservableKeyedArrayList<String, ApplicationData>`, `app:items`/`app:layout`), `currentlySelectedApps`, `initiallyExcluded` (from `KEY_IS_EXCLUDED`).
- Produces: a full-screen styled panel; the `REQUEST_SELECTION` result contract (`KEY_SELECTED_APPS` String[], `KEY_IS_EXCLUDED` Boolean) is **unchanged**.

- [ ] **Step 1: Make the dialog full-screen with a toolbar**

In `AppListDialogFragment.kt`, set a full-screen dialog theme (e.g. `setStyle(STYLE_NORMAL, R.style.ThemeOverlay_Awg_FullScreenDialog)` in `onCreate`, or override `onCreateDialog` to a full-screen `Dialog`) and inflate the panel via `onCreateView` instead of `MaterialAlertDialogBuilder`. Add a `MaterialToolbar` (back arrow → dismiss + return result; title `@string/split_tunnel_title`). Keep `onCreateDialog`'s data-binding + adapter setup; move the positive/neutral/negative actions: back = apply+dismiss, "toggle all" becomes an overflow/secondary action (keep its existing logic).

- [ ] **Step 2: Replace the TabLayout with a segmented Include/Exclude toggle**

In `app_list_dialog_fragment.xml`, replace `tabs` (TabLayout) with a **Mode card** (`surfaceContainer`, 16dp): helper line (`@string/split_tunnel_mode_helper`) + a `MaterialButtonToggleGroup` (`app:singleSelection="true"`, `app:selectionRequired="true"`) of two `MaterialButton`s — `@string/split_tunnel_include` / `@string/split_tunnel_exclude`. Bind the checked button to the existing mode state (the old tab index → `isExcluded`): exclude selected ↔ `initiallyExcluded == true`. On toggle, run the same logic the tab-select listener ran (keep selections, recompute the summary). Selected segment styling: `primaryContainer` bg + `onPrimaryContainer` text + 1dp primary stroke (use a `Widget.Material3.Button.OutlinedButton`-based style or `?attr/materialButtonToggleGroupStyle`).

- [ ] **Step 3: Restyle the app row**

In `app_list_item.xml`: app icon 40dp with 9dp corner radius (wrap in a `ShapeableImageView` or apply a rounded outline); name uses `@style/TextAppearance.Awg.FieldValue`, `layout_weight=1`; trailing `selected_checkbox` kept as the M3 `CheckBox` (two-way `@={item.selected}`) — ensure its tint = `primary` checked / `outline` unchecked. Flat row, `paddingVertical=space_sm`, full-row click toggles the checkbox. Keep the `@{key}`/`app_icon`/`selected_checkbox` ids + bindings.

- [ ] **Step 4: Build** → BUILD SUCCESSFUL.

- [ ] **Step 5: On-device check (Vitaly)** — tapping the editor's Applications row opens a full-screen "Split tunneling" screen with a back arrow, an Include/Exclude segmented toggle (selecting one keeps the current app selections), and flat app rows with checkboxes; choosing apps + going back persists and the editor's summary updates.

- [ ] **Step 6: Commit**

```bash
git add ui/src/main/java/org/amnezia/awg/fragment/AppListDialogFragment.kt ui/src/main/res/layout/app_list_dialog_fragment.xml ui/src/main/res/layout/app_list_item.xml
git commit -m "refresh: full-screen split-tunnel panel with Include/Exclude toggle + restyled rows (phase 5)"
```

---

### Task 9: Split-tunnel panel — search filter + footer summary

**Files:**
- Modify: `ui/src/main/res/layout/app_list_dialog_fragment.xml` (add search field + footer)
- Modify: `ui/src/main/java/org/amnezia/awg/fragment/AppListDialogFragment.kt` (filter + summary)

**Interfaces:**
- Consumes: the adapter from Task 8; phase-4 `SplitTunnelSummary.routedCount/isAllApps`.
- Produces: a client-side app filter + a "N of M apps routed" footer.

- [ ] **Step 1: Add the search field**

In `app_list_dialog_fragment.xml`, above the `app_list` RecyclerView add a `TextInputLayout` (id `app_search_layout`) + `TextInputEditText` (id `app_search_text`) with a leading search icon and `@string/split_tunnel_search_hint`.

- [ ] **Step 2: Filter the list client-side**

In `AppListDialogFragment`, keep the full loaded `ObservableKeyedArrayList` and add a `doAfterTextChanged` on `app_search_text` that filters the displayed items by app name (case-insensitive `contains`). Preserve each item's `selected` state across filtering (filter a view of the backing list; do not lose selections). Empty query → full list.

- [ ] **Step 3: Add the footer summary**

Add a footer `TextView` (id `app_routed_summary`, `FieldLabel`, centered) pinned at the bottom. Update its text whenever selection or mode changes using `SplitTunnelSummary`: `isAllApps(included,excluded)` → `@string/split_tunnel_all_apps` else `@string/split_tunnel_summary, routedCount(included,excluded,total), total`. `total` = the app list size. The mode toggle (Task 8) and per-row checks both call the update.

- [ ] **Step 4: Build** → BUILD SUCCESSFUL.

- [ ] **Step 5: On-device check (Vitaly)** — typing in search filters the list (selections survive); the footer reads "N of M apps routed" and updates live as apps and Include/Exclude change.

- [ ] **Step 6: Commit**

```bash
git add ui/src/main/res/layout/app_list_dialog_fragment.xml ui/src/main/java/org/amnezia/awg/fragment/AppListDialogFragment.kt
git commit -m "refresh: split-tunnel panel search filter + 'N of M routed' footer (phase 5)"
```

---

## Self-Review

**Spec coverage:**
- §1 Detail re-architecture → Tasks 2 (labels outside + pill + collapse), 3 (field rows/dividers), 4 (summary card). ✓
- §2 Toolbar icons → Task 5. ✓
- §3 Editor full-width long fields → Task 6. ✓
- §4 List/TV spacing → Task 7. ✓
- §5 Split-tunnel panel → Tasks 8 (container+toggle+rows), 9 (search+footer). ✓
- Foundation (icons/strings) → Task 1. ✓

**Type/id consistency:** `bindCollapsibleSection(id,header,body,chevron,defaultExpanded)` used with the **card** as `body` in Task 2 (matches the phase-4 signature at TunnelDetailFragment.kt:121). `proto_badge`/`obfuscation_summary`/`obfuscation_chips` ids preserved (relocated, still resolved by `populateObfuscation`). `REQUEST_SELECTION`/`KEY_SELECTED_APPS`/`KEY_IS_EXCLUDED` contract unchanged across Tasks 8-9. `SplitTunnelSummary.isAllApps/routedCount` signatures match phase-4.

**Placeholder scan:** layout tasks cite exact ids/paths + canonical XML snippets; "repeat per section" instructions name every field id to move. The one deliberate open choice (editor Save filled-button vs icon, Task 5 Step 3) carries an explicit documented fallback, not a TBD.

**Dimens to verify during execution:** `space_md`, `space_lg`, `card_gap`, `screen_padding` are referenced — confirm they exist in `dimens.xml`; if a token is missing, add it in the task that first uses it.

**Open items confirmed in spec** (carry into execution): exact detail edit / editor save wiring (Task 5 — verified: detail edit handled in MainActivity:81-88, editor save in `onMenuItemSelected`); collapse animation scope with card-as-body (Task 2 Step 4); search filter approach preserving checkbox state (Task 9 Step 2).
