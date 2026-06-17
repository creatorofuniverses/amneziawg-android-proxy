# Design Refresh Brief — for Claude (web)

> Goal: a **visual refresh** of the existing AmneziaWG Android app — not a redesign.
> Retheme + polish typography, spacing, shape, and component styling on the screens
> that already exist. Pair this brief with `current-design-snapshot.md` and screenshots.

---

## Part 1 — How to deliver the result (so it can be implemented)

The target is **Android Material 3 XML resources**, not CSS/Figma/React. Deliver a
**spec**, not just a picture. A mockup image/HTML is welcome *as reference*, but the
implementable artifact is the token + component spec below. Structure the answer as:

### A. Design Tokens
Give exact values mapped to the **existing Material 3 role names**, light **and** dark:
- Colors by role: `primary`, `onPrimary`, `primaryContainer`, `secondary…`, `surface`,
  `surfaceVariant`, `outline`, `error…`, plus the 3 status colors. Hex, both themes.
- Shape: corner radius per component (in **dp**).
- Elevation: per component (dp / Material tonal level).
- Spacing: a scale on a **4dp grid** (e.g. 4/8/12/16/24).
- Type: map each text role to a Material `textAppearance*` token (Display/Headline/Title/
  Body/Label). If a custom font is proposed, flag it as a dependency (see boundaries).

> Format colors as a table: `role | light hex | dark hex | where it shows up`.

### B. Component Specs
For each shared component, describe the target in **Material attributes**, referencing the
real view IDs from the snapshot:
- Card (`tunnel_detail_card`), List row (`tunnel_list_item` / `tunnel_name` / `tunnel_status`),
  Toggle (`ToggleSwitch`), FAB (`create_fab`), Toolbar, Bottom sheet, Empty state, Inputs.
- Per component: shape, color roles, elevation, padding, typography token, state colors.

### C. Per-Screen Changes
One short section **per existing screen** (list, detail, editor, add-sheet, log). Describe
what changes — grouping, hierarchy, spacing, density — in words, optionally with an ASCII
wireframe. **Do not add new screens, tabs, onboarding, dashboards, or account flows.**

### D. Rationale & Before/After
Brief "why" per major change, and an explicit **non-goals / out-of-scope** acknowledgment
so scope creep is visible.

**One-line instruction to give Claude:** *"Return an Android Material 3 design spec
(tokens + component + per-screen) expressed in Material color-role names and dp/sp, mappable
to themes.xml / colors.xml / styles.xml / dimens / existing layouts. Stay in XML (no full
Compose migration); a load-bearing dep or two is OK, not a pile."*

---

## Part 2 — Project boundaries (medium, not hard)

The spirit: a **refresh**, not a ground-up rebuild. These are guardrails, not a straitjacket —
a proposal can stretch them with a good reason, but should not blow past all of them at once.

1. **No ground-up rewrite / no full Compose migration.** Stay in the existing XML +
   Material Components 1.11.0 world. Don't propose rebuilding the app from scratch in a new
   UI framework. (A tiny, isolated Compose interop island *could* be discussed if it's truly
   load-bearing — but the default is XML.)
2. **Dependencies: a couple, if load-bearing.** Adding one or two well-chosen libraries is
   fine when they clearly carry the design (e.g. a lightweight animation or icon lib). Avoid
   piling on many deps, heavyweight frameworks, or anything that drags in a large transitive
   tree. Each new dep should justify its weight.
3. **Keep `minSdk 24`.** Effects needing higher APIs (blur, Material You dynamic color =
   API 31+) are fine as additive enhancement with a graceful fallback below that level.
4. **Preserve the screen set & navigation.** Same fragments/activities. Restyling and
   re-laying-out within a screen is welcome; inventing new IA, onboarding, dashboards, or
   account flows is not the goal.
5. **Respect content density.** Detail/editor intentionally show many AmneziaWG obfuscation
   fields. Improve hierarchy/scannability; **do not hide or remove functionality.**
6. **Prefer theme-level changes; layout edits are fine when they pay off.** Favor edits to
   `themes.xml` / `colors.xml` / `styles.xml` / `dimens.xml` (global, consistent, cheap).
   Reworking a specific layout for real impact (e.g. the list row or detail card) is allowed —
   just be deliberate, not a blanket rewrite of every file.
7. **Light + dark + TV all stay intact.** Every color ships both themes. The TV variant
   relies on d-pad focus — keep contrast/focus states usable.
8. **Don't break accessibility/focus.** Layouts carry extensive `nextFocus*` and
   `contentDescription`. The refresh must not strip these.
9. **Keep the APK reasonable.** Modest new assets are fine; avoid large illustration sets or
   bulky raster packs.
10. **Localization-safe.** 20+ locales; avoid designs that depend on lots of new English copy
    or fixed-width text.

### Effort envelope
A **retheme + meaningful polish**: color/shape/elevation/typography tokens, list-row and card
refinement, empty-state, FAB, toolbar, bottom-sheet styling — plus targeted layout rework
where it clearly improves a screen, and a small dep or two if they earn their place. **Not** a
new navigation model, **not** a from-scratch rebuild.

### Color is open
No palette constraints. The current `primary` happens to be near-black `#15181B` (monochrome
look) while the origin `seed` is blue `#1a73e8`, but neither is fixed — propose whatever the
screenshots call for, in both light and dark.
