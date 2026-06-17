# Phase 6 additions — Compact handshake time + custom fonts

**Date:** 2026-06-17
**Branch:** `refresh-phase6` (continues the existing phase-6 work; not yet squash-merged into `refreshed_ui`)
**Asset pack:** `~/Documents/amneziawg-refresh-assets` (Network Teal refresh)

Two additions on top of the phase-6 UI refresh:

1. **Redesigned detail-view handshake time** — replace the verbose ICU "4 minutes, 27 seconds ago" with a compact tiered relative format.
2. **Custom fonts** — bundle Manrope (proportional) + JetBrains Mono (keys/IPs/stats) and roll them out app-wide.

---

## Part A — Compact handshake format

### Current state

Handshake age is rendered in **three** places, with **two** different formatters:

| Location | File:line | Current format |
|---|---|---|
| Detail summary card (`summary_handshake`) | `ui/.../fragment/TunnelDetailFragment.kt:330` | `QuantityFormatter.formatEpochAgo` → verbose ICU, e.g. "4 min, 27 sec ago" |
| Peer "Latest handshake" row (`latest_handshake_text`) | `ui/.../fragment/TunnelDetailFragment.kt:352` | same verbose `formatEpochAgo` |
| List-row live-stats strip (`stat_handshake`) | `ui/.../databinding/BindingAdapters.kt:284-285` | ad-hoc compact, two-tier: `23s ago` / `5m ago` — **caps at minutes** (a 3 h handshake prints "180m ago") |

- Verbose formatter: `ui/src/main/java/org/amnezia/awg/util/QuantityFormatter.kt:31-62` (`formatEpochAgo`, ICU `MeasureFormat` + `ListFormatter`).
- Existing strings (`ui/src/main/res/values/strings.xml`):
  - `stat_ago_seconds` = `"%1$ds ago"` (253) — reuse
  - `stat_ago_minutes` = `"%1$dm ago"` (254) — retired by this change
  - `stat_ago_never` = `"—"` (255) — reuse
  - `latest_handshake` (143), `latest_handshake_ago` = `"%s ago"` (144) — used only by the verbose formatter

### Change

**1. New formatter** in `QuantityFormatter.kt`, alongside (not replacing) `formatEpochAgo`:

```kotlin
/** Compact relative handshake age: "23s ago" / "1m 14s ago" / "1h 4m ago" / "2d 3h ago". */
fun formatEpochAgoShort(epochMillis: Long): String {
    val ctx = Application.get().applicationContext
    if (epochMillis <= 0L) return ctx.getString(R.string.stat_ago_never)
    val s = (System.currentTimeMillis() - epochMillis) / 1000
    return when {
        s < 60     -> ctx.getString(R.string.stat_ago_seconds, s.toInt())
        s < 3600   -> ctx.getString(R.string.stat_ago_min_sec,  (s / 60).toInt(), (s % 60).toInt())
        s < 86400  -> ctx.getString(R.string.stat_ago_hour_min, (s / 3600).toInt(), ((s % 3600) / 60).toInt())
        else       -> ctx.getString(R.string.stat_ago_day_hour, (s / 86400).toInt(), ((s % 86400) / 3600).toInt())
    }
}
```

Tier rules (finest unit *pair* only):

| Age | Output | String |
|---|---|---|
| `≤ 0` | `—` | `stat_ago_never` (exists) |
| `< 60s` | `23s ago` | `stat_ago_seconds` (exists) |
| `< 60m` | `1m 14s ago` | `stat_ago_min_sec` (**new**) |
| `< 24h` | `1h 4m ago` | `stat_ago_hour_min` (**new**) |
| `≥ 24h` | `2d 3h ago` | `stat_ago_day_hour` (**new**) |

**2. New strings** in `ui/src/main/res/values/strings.xml`:

```xml
<string name="stat_ago_min_sec">%1$dm %2$ds ago</string>
<string name="stat_ago_hour_min">%1$dh %2$dm ago</string>
<string name="stat_ago_day_hour">%1$dd %2$dh ago</string>
```

Remove `stat_ago_minutes` (254) — no remaining consumer after the wire-up below.

**3. Wire-up — all three sites use `formatEpochAgoShort`:**

- `TunnelDetailFragment.kt:329-330` (summary card) — the `≤0 → stat_ago_never` branch is now handled inside the formatter, so the call collapses to `binding.summaryHandshake.text = QuantityFormatter.formatEpochAgoShort(latestHandshake)`.
- `TunnelDetailFragment.kt:352` (peer row) — `peer.latestHandshakeText.text = QuantityFormatter.formatEpochAgoShort(...)`. Row stays a `FieldValue` field row; only the text source changes.
- `BindingAdapters.kt:281-285` (list-row strip) — replace the inline two-tier `when` with a single call to `QuantityFormatter.formatEpochAgoShort(handshakeEpochMillis)`. Retires the coarse "180m ago" path so handshake reads identically across list, summary card, and peer row.

The summary-card layout already matches the spec (`TextAppearance.Awg.StatLabel` label + `TextAppearance.Awg.Mono` value, `tunnel_detail_fragment.xml:174`); no layout edits needed for Part A.

### Notes / accepted trade-offs

- **i18n:** the three new strings live only in the default `strings.xml`; the ~35 locale dirs fall back to English ("1m 14s ago"). Acceptable for numeric + "ago"; not blocking.
- **a11y:** the verbose `formatEpochAgo` remains in the codebase. Optional (not in scope now): set `contentDescription` on the handshake views to the verbose string for screen readers. Deferred.
- `latest_handshake_ago` (144) is retained — still referenced by `formatEpochAgo`.

---

## Part B — Custom fonts (full rollout)

### Current state

- **No `res/font/` directory** — all text uses system `sans-serif` / `sans-serif-medium` / `monospace`.
- Project typography lives in `ui/src/main/res/values/styles_type.xml` (the project's name for the asset pack's `styles-awg-type.xml`), merged in phase 3. It defines 12 `TextAppearance.Awg.*` styles, all pointing at system fonts, with a commented Manrope-titles-only placeholder.
- `themes.xml` / `values-night/themes.xml` set no custom `fontFamily` (Material 3 defaults).

### Change

**1. Add `ui/src/main/res/font/`** — copy from the asset pack:
- `manrope_regular.ttf` (400), `manrope_medium.ttf` (500), `manrope_semibold.ttf` (600)
- `jetbrains_mono_regular.ttf` (400), `jetbrains_mono_medium.ttf` (500)
- `manrope_family.xml`, `jetbrains_mono_family.xml` (weight-mapping font families)

**2. Repoint `styles_type.xml`** — update each `TextAppearance.Awg.*` from system fonts to the bundled faces (this is exactly the asset pack's `styles-awg-type.xml` mapping). Set both `android:fontFamily` and `fontFamily` on each:

| Style(s) | Font |
|---|---|
| `Title`, `TunnelName`, `SectionHeader`, `Status`, `StatLabel`, `Button`, `EmptyTitle` | `@font/manrope_semibold` |
| `FieldValue` | `@font/manrope_medium` |
| `FieldLabel`, `EmptyBody` | `@font/manrope_regular` |
| `Mono`, `Mono.Accent` | `@font/jetbrains_mono_family` |

(`Mono.Accent` inherits from `Mono`; only color differs.)

**3. App-wide default** — add to the app theme in **both** `ui/src/main/res/values/themes.xml` and `ui/src/main/res/values-night/themes.xml`:

```xml
<item name="fontFamily">@font/manrope_regular</item>
<item name="android:fontFamily">@font/manrope_regular</item>
```

So any view not using an `Awg` textAppearance still inherits Manrope. Material components keep their weight from their own textAppearance.

**4. Licenses** — add `Manrope-OFL.txt` + `JetBrainsMono-OFL.txt` (OFL 1.1; from the asset pack `/licenses`) to a tracked `licenses/` directory at the repo root. OFL requires shipping the license text. (Optional, deferred: surface attribution in the app's about/licenses screen.)

### Notes / accepted trade-offs

- **APK size:** ~370 KB increase (5 `.ttf` ≈ 97 KB Manrope ×3 + 274 KB JetBrains ×2). Accepted.
- **Metrics:** Manrope's vertical metrics differ slightly from Roboto; dense rows (tunnel list, field rows) may shift a hair. Verify on-device.
- **Scope:** full rollout (Manrope app-wide + JetBrains Mono for keys/IPs/stats/handshake), per the asset-pack intent.

---

## Integration & verification

- **Branch:** `refresh-phase6`.
- **Build:** debug build of `:ui` must compile (`./gradlew :ui:assembleDebug`).
- **On-device visual pass (Vitaly):**
  - Handshake at fresh (`<1m`), minutes (`1m 14s`), hours (`1h 4m`), and stale (`2d 3h`) ages, in the summary card, peer row, and list-row strip — all three read identically.
  - Font rendering across list / detail / editor / empty state; check dense-row spacing for metric shifts.
- **No new tests required** (resource + formatter change); `formatEpochAgoShort` tier boundaries are simple enough to eyeball, but a small unit test on the four tier boundaries is a cheap optional add.

## Out of scope (explicitly deferred)

- a11y verbose `contentDescription` on handshake views.
- Surfacing font attribution in the about screen.
- Translating the new `stat_ago_*` strings into the ~35 locales.
- Any other phase-6 asset-pack item not listed above.
