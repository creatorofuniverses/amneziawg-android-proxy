# Spec — UI refresh phase 4: screen anatomy (detail · editor · split) + protocol badge + rename

> Date: 2026-06-17 · Branch: `ui-refresh-dev` · Author: brainstorm (Claude + Vitaly)
> Pairs with: `design-refresh-brief.md`, `current-design-snapshot.md`, and the asset pack
> at `~/Documents/amneziawg-refresh-assets` (`SPEC-detail-editor-split.md`,
> `INTEGRATION-live-stats.md`, `README.md`).

## 1. Context

Phases 1–3 landed the token layer of the Network Teal refresh: palette (light+dark),
typography (`TextAppearance.Awg.*`), `dimens` (4dp grid), component styles, switch state
lists, launcher icons, status colors (named `tunnel_status_*`, values correct), the
connected/selected row backgrounds, the live per-row stats poller on the list, and the
split-tunnel entry **row** in the editor.

What remains is the **deeper anatomy of the three under-specced screens** that the asset
pack details but phases 1–3 only partially implemented, plus two product additions Vitaly
asked for in this round: a **3-way protocol badge** and an **app rename**.

This is a refresh, not a redesign: no new screens, no navigation changes, stays in XML +
Material Components 1.11.0, `minSdk 24`, light/dark/TV all intact. No Compose. No palette
change.

## 2. Scope

In scope (all confirmed):

1. Collapsible detail sections (full: animate + persist) — §4.1
2. Obfuscation read-only chips on detail + **3-way protocol badge** (WG / Amnezia / Proxy) — §4.2
3. Editor obfuscation fields regrouped 2–3 per row — §4.3
4. Connection-summary resolved Server address `IP:port` — §4.4
5. Split-tunnel live summary ("N of M apps routed") + optional detail mirror — §4.5
6. TV row stats-strip parity — §4.6
7. **App rename**: launcher "AWG Proxy" / in-app header "AmneziaWG Proxy" — §4.7
8. Foundation: `ic_chevron_down` + missing strings — §4.0

Out of scope / non-goals: new screens/tabs/onboarding/dashboards; Compose migration;
palette change (Network Teal already shipped); renaming the `tunnel_status_*` color tokens
(values already correct — no churn); replacing last-handshake-ago with true uptime; any new
heavyweight dependency.

## 3. Packaging

One spec (this doc). Implementation lands as small phased commits on `ui-refresh-dev`,
matching the phase-1–3 cadence. Suggested commit order follows §4 (foundation → badge/chips
→ collapsible → editor → IP:port → split summary → TV → rename), so each commit is
independently reviewable on-device.

## 4. Design

### 4.0 Foundation

- Add `drawable/ic_chevron_down.xml` from the asset pack (used by collapsible headers).
- Add the missing strings to `values/strings.xml` (asset pack `strings-awg-detail.xml` /
  `strings-awg-split.xml` are the source):
  - `section_interface`, `section_obfuscation`, `section_peer`, `section_applications`
  - `detail_section_count` (e.g. `"%d parameters"`)
  - `detail_public_endpoint`, `detail_endpoint`, `detail_interface_addresses`, `detail_allowed_ips`
  - `split_tunnel_summary` (`"%1$d of %2$d apps routed"`), `split_tunnel_all_apps` (`"All apps routed"`)
  - Protocol badge labels: `proto_badge_wg` = `"WG"`, `proto_badge_amnezia` = `"Amnezia"`,
    `proto_badge_proxy` = `"Proxy"` (see open confirmation O-1 on the exact "Proxy" label).
- Reuse the existing `dot_status` equivalent (`ic_status_dot`) and `bg_stat_chip` —
  already present, no new drawables beyond the chevron.

### 4.1 Collapsible detail sections (full)

Target: `tunnel_detail_fragment.xml` Interface / Obfuscation / Peer cards become collapsible,
per `include_detail_section.reference.xml`.

- Each section card: a vertical `LinearLayout` of a tappable **header row** + a **body**.
- Header row (`@id/section_header`, `minHeight=touch_min`, `selectableItemBackground`,
  `clickable`+`focusable`): `SectionHeader` title (start, weight 1) · optional
  collapsed-only summary (`FieldLabel`) · `ic_chevron_down` (end, 24dp).
- Body (`@id/section_body`): the existing field rows / chips; visibility toggled
  `visible`/`gone`.
- Toggle: `TransitionManager.beginDelayedTransition(card, AutoTransition())` (~200ms) +
  chevron rotate 0↔180 over the same duration (`ViewPropertyAnimator.rotation`).
- Defaults: **Interface** and **Peer** expanded; **Obfuscation** collapsed, header summary
  = `detail_section_count` with the live param count (e.g. "8 parameters"); optional chip
  preview when collapsed.
- Persistence: reuse the existing DataStore (`Application.getPreferencesDataStore()` +
  `booleanPreferencesKey("detail_section_<id>_expanded")`). Read async (Flow → `.first()`
  in the fragment's lifecycle scope) and apply before/at first layout to avoid a visible
  snap; write on each toggle. `<id>` ∈ {`interface`, `obfuscation`, `peer`}.
- Editor sections stay **non-collapsible** (you're editing everything; collapsing hides
  inputs) — headers remain for grouping only.

### 4.2 Obfuscation chips + 3-way protocol badge (detail)

**Chips.** Replace the read-only AWG param rows in the Obfuscation card body with a
wrapping grid of chips (`Jc 3`, `Jmin 50`, `Jmax 1000`, `S1…`, `H1…H4`, `I1…I5` where set):
each chip = `bg_stat_chip` (`surfaceVariant`, 7dp radius), label `TextAppearance.Awg.Mono`
11sp. Use a wrapping container — **FlexboxLayout if `com.google.android.flexbox` is already
on the classpath; otherwise a simple wrap layout (no new dependency)** (see open
confirmation O-2). These chips double as the collapsed-state preview for §4.1.

**Protocol badge.** A pill rendered next to the section/tunnel identity, derived from the
`Interface` config. Three mutually-exclusive states:

| Badge | Condition |
|---|---|
| **WG** | No AmneziaWG obfuscation params set at all — every `junkPacket*`, `*PacketJunkSize`, `*MagicHeader`, `specialJunkI1..I5`, and `imitateProtocol` is `Optional.empty()`. Plain WireGuard. |
| **Amnezia** | Obfuscation params present, BUT all of `specialJunkI1..I5` are **static** (none contains the dynamic `<…>` tag format) AND `getImitateProtocol().isEmpty()`. |
| **Proxy** | Otherwise — any of `specialJunkI1..I5` uses the `<…>`-tagged dynamic format, OR `getImitateProtocol().isPresent()`. |

Detection helper (new, in the UI/binding layer; reads `Interface` getters only):

```kotlin
enum class ProtoMode { WG, AMNEZIA, PROXY }

fun Interface.protoMode(): ProtoMode {
    val specialJunk = listOf(specialJunkI1, specialJunkI2, specialJunkI3,
                             specialJunkI4, specialJunkI5).mapNotNull { it.orElse(null) }
    val hasObfuscation = junkPacketCount.isPresent || junkPacketMinSize.isPresent ||
        junkPacketMaxSize.isPresent || initPacketJunkSize.isPresent ||
        responsePacketJunkSize.isPresent || cookieReplyPacketJunkSize.isPresent ||
        transportPacketJunkSize.isPresent || initPacketMagicHeader.isPresent ||
        responsePacketMagicHeader.isPresent || underloadPacketMagicHeader.isPresent ||
        transportPacketMagicHeader.isPresent || specialJunk.isNotEmpty() ||
        imitateProtocol.isPresent
    val dynamicSignature = specialJunk.any { it.contains('<') }   // <…>-tagged builder format
    return when {
        !hasObfuscation -> ProtoMode.WG
        dynamicSignature || imitateProtocol.isPresent -> ProtoMode.PROXY
        else -> ProtoMode.AMNEZIA
    }
}
```

Placement: the badge is shown for **all three** modes. Because WG configs have no
Obfuscation section, the badge sits on the detail header area (beside the tunnel name /
on the Interface or connection identity), not only on the Obfuscation header (open
confirmation O-3). Pill styling: `primaryContainer` bg / `onPrimaryContainer` text,
full-radius, ~9–11sp, per the asset's AMNEZIA-pill spec.

> Note: the `<…>` substring test is a pragmatic discriminator for the dynamic packet-builder
> format. Confirm against a real AWG 2.0 `I*` value during implementation; widen the test if
> the format uses a different sentinel.

### 4.3 Editor obfuscation — 2–3 fields per row

Target: `tunnel_editor_fragment.xml` Obfuscation card (the `*_layout` `TextInputLayout`
chain ending at `@id/amnezia_barrier`).

- Regroup the editable number/text fields into rows of **2–3** (constraint chains or nested
  rows), 9dp horizontal gap, keeping the OBFUSCATION (AMNEZIA) header. Natural pairs:
  Jmin/Jmax; the four `*PacketJunkSize`; the four `*MagicHeader`; `I1…I5`.
- Inputs keep the existing outlined style + the black-on-black fix
  (`AmneziaWgTheme.TextInputEditText`, text = `onSurface`). No collapsing in the editor.

### 4.4 Connection-summary resolved Server address (IP:port)

Target: the connected-only summary card in `tunnel_detail_fragment.xml`.

- Add a `detail_public_endpoint` value (`TextAppearance.Awg.Mono`, right-aligned by the
  status row), showing the resolved `IP:port` actually connected to.
- Data: not in `Statistics`. Resolve in the **existing detail stats coroutine** (already
  polling on a background dispatcher):
  `peer.endpoint.flatMap { it.getResolved() }` → `InetEndpoint` → `host:port`.
  `getResolved()` does DNS (cached ~1 min) and must stay off the main thread (it already is).
  Post the string to the view on the main thread.
- Fallback: if resolution returns empty, show the configured `peer.getEndpoint()` string.
- Visibility: only when connected/connecting; hidden (with the whole card) when down.

### 4.5 Split-tunnel live summary

- Bind `@id/split_tunnel_summary` (already in the editor's entry row) to live text:
  `split_tunnel_all_apps` ("All apps routed") when no apps excluded/included, else
  `split_tunnel_summary` formatted "N of M apps routed" (M = installed-app count, N derived
  from the include/exclude set per current split-tunnel semantics).
- Optionally mirror the entry row read-only on `tunnel_detail_fragment.xml` under an
  Applications header (no navigation change — taps still open the existing app picker).

### 4.6 TV row stats-strip parity

Target: `tv_tunnel_list_item.xml`.

- `<include layout="@layout/include_tunnel_stats"/>` at the same position as the phone row.
- Strip root `focusable="false"` + `descendantFocusability="blocksDescendants"` so d-pad
  never lands inside it; keep the row's existing `nextFocus*`.
- Reuse `tunnel_status_*` tokens so TV and phone read identically. Bind from the same
  list-fragment poller (already polling all UP tunnels).

### 4.7 App rename

- **External / launcher label** → "AWG Proxy". Keep `app_name` (manifest `android:label`,
  `translatable="false"`) but change its value to `"AWG Proxy"`.
- **In-app header / toolbar title** → "AmneziaWG Proxy". Add a new
  `app_title` (or `app_name_full`) string = `"AmneziaWG Proxy"` and point the main
  toolbar/header at it.
- Audit all current `@string/app_name` references (manifest, toolbars, about/log screens,
  notification) and route each to the correct one of the two strings — launcher/notification
  short label vs. in-app full title. Document the mapping in the implementing commit.

## 5. Risks / watch-items

- **R-1 (IP:port):** the only item touching backend-shaped logic. Contained to the detail
  poller; uses existing public `Peer`/`InetEndpoint` API. DNS off-main-thread already
  satisfied. Low risk.
- **R-2 (flexbox):** chips want a wrapping container. If flexbox isn't already a dependency,
  fall back to a plain wrap layout — do NOT add a heavyweight dep for chips alone.
- **R-3 (collapse persistence timing):** reading DataStore is async; apply expanded state
  before first paint to avoid a snap. Acceptable to render with the static default for one
  frame if the async read resolves immediately after.
- **R-4 (proto detection):** the `<…>` discriminator is heuristic; verify against a real
  AWG 2.0 `I*` value (O-3 below).
- **R-5 (rename fan-out):** `app_name` may be referenced in more places than the manifest
  (notifications, about, log export). Audit before assuming a clean two-string split.

## 6. Open confirmations (resolve during review or implementation)

- **O-1 — "Proxy" label text.** Vitaly wrote "Pproxy". Spec assumes the user-facing label
  is **"Proxy"**. Confirm the exact casing/spelling (Proxy / PProxy / Pproxy).
- **O-2 — flexbox availability.** Verify whether `com.google.android.flexbox` is already on
  the classpath before choosing the chip container (R-2).
- **O-3 — badge placement for WG mode.** Spec places the badge on the detail header/identity
  area so WG (no Obfuscation section) still shows it. Confirm that placement vs. badge only
  on the Obfuscation header.
- **O-4 — dynamic-signature sentinel.** Confirm the `<…>` tag test against a real AWG 2.0
  `I1..I5` value (R-4).

## 7. Acceptance criteria

- Detail: Interface/Peer expand by default, Obfuscation collapses with an "N parameters"
  summary; state survives leaving and re-entering the screen; chevron animates.
- Detail: AWG params render as chips; a single correct badge (WG/Amnezia/Proxy) shows per
  config per §4.2 truth table.
- Detail (connected): summary card shows the resolved `IP:port`; hidden when disconnected.
- Editor: obfuscation fields laid 2–3 per row; inputs legible (no black-on-black); no
  functionality removed.
- List + TV: connected rows show the live stats strip; TV d-pad focus moves row→row,
  never into the strip.
- Split-tunnel entry row shows accurate live "N of M apps routed" / "All apps routed".
- Launcher shows "AWG Proxy"; in-app header shows "AmneziaWG Proxy"; no dangling
  `app_name` reference shows the wrong one.
- Light, dark, and TV all intact; existing `nextFocus*` / `contentDescription` preserved.
