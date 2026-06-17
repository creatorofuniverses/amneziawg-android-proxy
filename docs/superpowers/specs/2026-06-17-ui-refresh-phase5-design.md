# UI Refresh — Phase 5 design (detail polish, editor fields, list/TV spacing, split-tunnel panel)

**Status:** approved design, ready for implementation plan.
**Branch:** `ui-refresh-dev` (off `refreshed_ui`; phases 1–4 already squash-merged as `b0038bac`).
**Driven by:** Vitaly's on-device review of phase 4 (4 feedback points) + claude-design's refreshed asset pack at `~/Documents/amneziawg-refresh-assets` (`SPEC-detail-anatomy.md`, `SPEC-detail-editor-split.md`, `README.md`, new `ic_edit.xml`/`ic_save.xml`). Assets are a **proposal, merged against code reality** — not ground truth.

## Goal

Close the gap between phase 4 and the approved mockups on the **detail** and **editor** screens, fix list/TV row spacing, and replace the stock app-picker with the designed split-tunneling panel. No new product capability — the include/exclude mode and live stats already exist; this surfaces and styles them.

## Global constraints (carried from phase 4; binding)

- XML + **Material Components 1.11.0**, **no Compose**, `minSdk 24`. **No new heavyweight dependency; no flexbox** (chips/grids use `ChipGroup` / weighted `LinearLayout` / `ConstraintLayout`).
- Light + dark + **TV** all stay intact; **preserve every existing `nextFocus*` and `contentDescription`**.
- Do **not** remove or hide any AmneziaWG field/functionality — only restructure presentation. Every field id + data-binding (`@{...}`/`@={...}`) expression is preserved unless explicitly moved.
- New user-visible strings go in `values/strings.xml`; keep copy short (20+ locales). `app_name`/`app_title` already set ("AWG Proxy" / "AmneziaWG Proxy") — unchanged.
- Reuse existing tokens/styles: `styles_type.xml` (`TextAppearance.Awg.*` — `SectionHeader`, `FieldLabel`, `FieldValue`, `Mono`, `Mono.Accent`, `Status`, `StatLabel`), `awg_connected_fill`/`awg_connected_stroke`, `status_connected/connecting/disconnected`, `dimens.xml` (`space_*`, `touch_min`), `ic_chevron_down`.
- **Verify command** (full APK needs NDK + go submodule and does NOT build here):
  `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
  Tunnel unit tests: `./gradlew :tunnel:test --console=plain`. SDK at `~/android-sdk`.
- Layout/Kotlin-UI changes are verified by a clean resource+Kotlin compile **plus an explicit on-device check by Vitaly** (he reviews each batch on-device and catches real bugs — wait for feedback). Pure-logic additions use real JUnit4 TDD.
- **Known pre-existing failure (not ours):** `:tunnel` `BadConfigExceptionTest.throws_correctly_with_SYNTAX_ERROR_reason` fails on this branch lineage; untouched by the refresh. Do not attribute to phase 5.

---

## Section 1 — Detail view re-architecture (Approach A: label-row owns the collapse)

**Files:** `ui/src/main/res/layout/tunnel_detail_fragment.xml`, `ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt`.

The key correction from `SPEC-detail-anatomy.md`: section names are **standalone labels OUTSIDE/above the card**, not rows inside it. Each section = a vertical wrapper of `[label row] + [MaterialCardView body]`, 12dp between blocks, 14–16dp side padding.

**Label row** (one per section; replaces phase-4's in-card header, and is the collapse tap target):
- Section title: **reuse the existing `TextAppearance.Awg.SectionHeader` style verbatim** (already teal / CAPS / Medium / letter-spaced from phase 4 — do not invent a new size; the two source specs disagree on 11 vs 14sp, the existing style is authoritative). `marginStart=4dp`, ~7dp above its card.
- **Chevron** (`ic_chevron_down`, rotates 180° when open) at the **end** of the label row.
- **Collapsed summary** (Obfuscation only): `FieldLabel`, e.g. "8 parameters", shown between title and chevron when collapsed.
- Tap target ≥ `touch_min`; `selectableItemBackground`.

**AMNEZIA/PROXY pill** (Obfuscation label row only): rides the label row immediately right of the title, ~7dp gap. Background `colorPrimaryContainer`, text `colorOnPrimaryContainer`, ~9sp bold CAPS, full corner radius (999dp), padding ~2dp×7dp. Static (not tappable). The pill text reuses phase-4's `ObfuscationMode` (`WG`/`AMNEZIA`/`PROXY`). **Moves from the interface/name area (phase 4) to the Obfuscation label row.** Not shown on Interface or Peer.

**Card body** (per section): `MaterialCardView`, `surfaceContainer`, 16dp radius, 1dp `outlineVariant` stroke, flat; padding 4dp top/bottom · 14dp left/right. Body = a vertical list of **field rows** (replaces phase-4 plain key/value text):
- Two-line stacked row: line 1 = label (`FieldLabel`, `onSurfaceVariant`); line 2 = value (`FieldValue`, `onSurface`; keys/IPs use `Mono` + `ellipsize=end` + `singleLine`, public key gets a copy affordance).
- `paddingVertical≈9dp`; **1dp `outlineVariant` divider between rows, none after the last.**
- **Interface fields:** Addresses · DNS servers · Public key · (Listen port / MTU if present) — plus existing Applications + imitate_protocol rows preserved (phase-3c grouping).
- **Peer fields:** Endpoint · Allowed IPs · Persistent keepalive · Latest handshake — same two-line row pattern.
- **Obfuscation body:** the read-only AWG param **chips** (phase-4 `ChipGroup`, `Jc 3` `Jmin 50` … `H1…H4` / `I1…I5`), unchanged.

**Connection-summary card** (top of detail; connected/connecting only): restyle to `SPEC-detail-anatomy.md §4`.
- Card: `awg_connected_fill` bg, 1dp `awg_connected_stroke`, 16–18dp radius, ~15dp padding.
- Row 1: 8dp status **dot** (`status_connected`/`status_connecting`) + 8dp gap + **status label styled like the tunnels list** (`TextAppearance.Awg.Status`, status color — this is the point-1 sub-item: not plain default) + right-aligned resolved **`IP:port`** in `Mono`, `onSurfaceVariant` (phase-4 `public_endpoint_text`, kept).
- Row 2 (12dp below): 3-column grid (16dp gap) DOWNLOAD / UPLOAD / HANDSHAKE — `StatLabel` over value; Download/Upload values `Mono.Accent` (teal), Handshake `Mono` (neutral).
- Disconnected: whole card omitted (existing behavior).

**Collapse behavior preserved:** phase-4 `bindCollapsibleSection(id, header, body, chevron, defaultExpanded)` + DataStore keys `detail_section_<id>_expanded` reused verbatim — the `header` argument is rebound to the new **label row**; `body` to the card. Defaults unchanged (Interface/Peer expanded, Obfuscation collapsed). `populateObfuscation()` still sets the collapsed-summary count and builds the chips. Animation: `TransitionManager.beginDelayedTransition(parent, AutoTransition())` ~200ms + chevron rotate, as phase 4.

---

## Section 2 — Toolbar edit/save icons

**Files:** `ui/src/main/res/drawable/ic_edit.xml` (new), `ui/src/main/res/drawable/ic_save.xml` (new), the detail + editor toolbar/menu sources.

- Copy `ic_edit.xml` and `ic_save.xml` from the asset pack verbatim into `res/drawable`.
- **Detail toolbar:** surface **edit (pencil)** as a visible action icon (`showAsAction="always"`) alongside overflow (⋮) — per spec the pencil is the primary action. (Wire to the existing edit action; verify current menu/toolbar wiring during planning.)
- **Editor toolbar:** **Save** as a filled primary text button on the end (claude-design's `ic_save` note explicitly prefers a filled text button over the floppy icon), close (✕) on start, "Edit tunnel" title. If a filled MaterialButton in the toolbar is impractical, fall back to the `ic_save` action icon — decide in planning, but the filled button is preferred.

---

## Section 3 — Editor obfuscation field layout

**File:** `ui/src/main/res/layout/tunnel_editor_fragment.xml` (obfuscation card).

Adjust phase-4 Task 6's reflow:
- **Full-width (one `TextInputLayout` per row):** the 4 magic-header fields (`init_/response_/underload_/transport_packet_magic_header_layout`) + the 5 special-junk fields (`special_junk_i1..i5_layout`) — they hold long string/hex values.
- **Stay grouped 2–3/row:** `junk_packet_count` + `junk_packet_min_size` + `junk_packet_max_size`; the 4 junk-size fields (`*_packet_junk_size_layout`).
- Keep the OBFUSCATION (AMNEZIA) header, OutlinedBox inputs (`onSurface` input text — black-on-black fix), every field id + `@={...}` binding intact. **Re-point `nextFocus*` chains** to the new visual order (full-width rows are simple linear top-to-bottom; grouped rows left-to-right then down). Editor sections remain **non-collapsible** (you edit everything).

---

## Section 4 — List + TV row name↔status spacing

**Files:** `ui/src/main/res/layout/tunnel_list_item.xml`, `ui/src/main/res/layout/tv_tunnel_list_item.xml`.

- Increase the vertical gap between the tunnel **name** and the **Connected/Disconnected** status so that, in the **connected** state, the status label sits **vertically centered** in the space between the name and the divider/stats below — instead of hugging the name (phase 4 / README had it ~4dp below the name, which reads as top-aligned).
- The name+status block gets a comfortable consistent height; **disconnected rows may be a bit taller** to match (Vitaly approved). Phone and TV kept consistent.
- Status keeps `status_connected/connecting/disconnected` tokens + `TextAppearance.Awg.Status`; preserve the live stats strip (`include_tunnel_stats`), `nextFocus*`, and `contentDescription`. Do not re-introduce the double-margin bug the README warns about (single 16dp gap above the divider).

---

## Section 5 — Split-tunneling panel restyle (full-screen dialog)

**Files:** `ui/src/main/java/org/amnezia/awg/fragment/AppListDialogFragment.kt` + its layout(s); new row layout; new strings.

Restyle the existing app-picker into the designed panel (`SPEC-detail-editor-split.md §3`). **It is a restyle, not new capability:** `Interface` already holds mutually-exclusive `includedApplications`/`excludedApplications`, and `AppListDialogFragment` already tracks the mode (`KEY_IS_EXCLUDED`, include/exclude plural strings) and returns via `REQUEST_SELECTION`. Keep that result contract so the editor integration and phase-4 `SplitTunnelSummary` ("N of M routed") keep working.

**Container:** convert to a **full-screen styled dialog** (own toolbar w/ back arrow) — chosen for long app lists + search; least disruption to the `REQUEST_SELECTION` flow.

**Structure (top → bottom):**
1. **Toolbar** — back arrow + "Split tunneling".
2. **Mode card** (`surfaceContainer`, 16dp): one helper line + a **segmented Include/Exclude toggle**. Selected segment = `primaryContainer` bg + `onPrimaryContainer` text + 1dp primary border; unselected = `surfaceVariant`/`onSurfaceVariant`. Bound to the existing mode (`KEY_IS_EXCLUDED`). This is the disclosure that was previously hidden.
3. **Search field** — `TextInputLayout` + leading search icon, "Search apps"; filters the loaded app list client-side (new).
4. **App rows** — real app icon (32–40dp, 9dp radius) + name (`FieldValue`, flex) + trailing **M3 checkbox** (checked = filled `primary`; unchecked = 1.5dp `outline`). Flat rows (no card), 9dp vertical padding, full-row tap toggles, 48dp touch target.
5. **Footer summary** — "N of M apps routed" (`FieldLabel`, centered), reusing `SplitTunnelSummary`.

**Behavior:** toggling Include/Exclude flips the check meaning, **keeps selections**, relabels the summary; "nothing checked + exclude" = all-apps (default). Persist to config exactly as today; the editor's Applications row summary updates on return. The segmented toggle must enforce the config invariant (included **or** excluded non-empty, never both).

---

## Components / boundaries

- **No new pure-logic util required.** `ObfuscationMode` (pill) and `SplitTunnelSummary` (footer/editor) already exist and are reused. If the segmented-toggle mode mapping grows non-trivial branching, consider a tiny pure helper for "selection set ↔ (mode, set)" with JUnit4 TDD — decide in planning; only if it earns its keep.
- Detail/editor layouts are the large files; keep each section's label-row + body as a self-contained block. The split-tunnel panel's app-row is its own small layout.

## Out of scope / non-goals

- No change to tunnel connect/disconnect logic, the backend, or config parsing/serialization.
- No new translations beyond the few new strings (search hint, split-tunnel mode labels/helper) — added in default `values/strings.xml`.
- The optional Manrope display font and per-field typography on all ~75 fields remain deferred (low gain).
- Debug-variant `app_name` ("AmneziaWG β") rebrand is a separate user decision, not part of phase 5.

## Verification

- Each layout/Kotlin task: clean `:ui:processDebugResources :ui:compileDebugKotlin` (warning-free) + Vitaly on-device check against the task's acceptance line.
- Any new pure helper: JUnit4 RED→GREEN in `:tunnel` (or `:ui` test if Android-coupled).
- Data-binding integrity: a clean compile proves no orphaned `@{...}`/dropped id, since the binding class fails otherwise. Spot-check that every moved field keeps its binding.

## Open items to confirm during planning

1. Exact current wiring of the **detail edit action** and **editor save action** (menu item vs toolbar) — to place the new icons without breaking existing handlers.
2. Whether the **collapse animation** still reads well when only the card body (not the outside label) animates — confirm `beginDelayedTransition` scope is the section wrapper.
3. The **search filter** interaction with the existing app-list adapter (filter in-adapter vs filtered list) — pick the simpler that preserves checkbox state across filtering.
