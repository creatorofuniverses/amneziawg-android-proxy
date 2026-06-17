# Phase 6 — Compact Handshake + Custom Fonts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Roll out bundled Manrope + JetBrains Mono fonts app-wide, and replace the verbose handshake time in the detail view with a compact tiered relative format unified across all three render sites.

**Architecture:** Two independent changes. (A) Fonts: drop `.ttf` + font-family XML into `res/font/`, repoint the 12 `TextAppearance.Awg.*` styles, set Manrope as the app-wide theme default. (B) Handshake: add `QuantityFormatter.formatEpochAgoShort()`, add three new strings, and route the detail summary card, the peer "Latest handshake" row, and the list-row stats strip through it. Pure resource + Kotlin work; no new dependencies.

**Tech Stack:** Android (XML resources, Material 3, Kotlin), minSdk 24. Build module `:ui`. No Compose.

## Global Constraints

- **Branch:** `refresh-phase6` (do NOT branch off; these are two more phase-6 additions; not yet squash-merged into `refreshed_ui`).
- **Build module:** `:ui`. Verification build command: `./gradlew :ui:assembleDebug`.
- **No new Gradle dependencies.** Fonts are bundled `.ttf`; the `:ui` module has no JVM unit-test harness, so verification is **compile + on-device visual**, not unit tests.
- **Asset source paths (copy verbatim from here):**
  - Fonts: `~/Documents/amneziawg-refresh-assets/res/font/`
  - Licenses: `~/Documents/amneziawg-refresh-assets/licenses/`
- **OFL 1.1:** the font license text files MUST ship in the repo (license requirement).
- **i18n:** new `stat_ago_*` strings go only in default `values/strings.xml`; ~35 locale dirs fall back to English. Accepted.
- **Commit trailer:** end each commit message with
  `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `ui/src/main/res/font/*.ttf` (5) | Create | Bundled Manrope (400/500/600) + JetBrains Mono (400/500) |
| `ui/src/main/res/font/manrope_family.xml`, `jetbrains_mono_family.xml` | Create | Weight-mapped font families |
| `ui/src/main/res/values/styles_type.xml` | Modify | Repoint 12 `TextAppearance.Awg.*` styles to bundled fonts |
| `ui/src/main/res/values/themes.xml` | Modify | App-wide Manrope default (light) |
| `ui/src/main/res/values-night/themes.xml` | Modify | App-wide Manrope default (dark) |
| `licenses/Manrope-OFL.txt`, `licenses/JetBrainsMono-OFL.txt` | Create | OFL license text (repo root) |
| `ui/src/main/res/values/strings.xml` | Modify | Add 3 compact `stat_ago_*` strings; remove `stat_ago_minutes` |
| `ui/src/main/java/org/amnezia/awg/util/QuantityFormatter.kt` | Modify | Add `formatEpochAgoShort()` |
| `ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt` | Modify | Wire summary card + peer row to `formatEpochAgoShort` |
| `ui/src/main/java/org/amnezia/awg/databinding/BindingAdapters.kt` | Modify | Wire list-row strip to `formatEpochAgoShort` |

Tasks 1 and 2 are independent and may be done in either order.

---

### Task 1: Custom fonts (Manrope + JetBrains Mono) rollout

**Files:**
- Create: `ui/src/main/res/font/manrope_regular.ttf`, `manrope_medium.ttf`, `manrope_semibold.ttf`, `jetbrains_mono_regular.ttf`, `jetbrains_mono_medium.ttf`, `manrope_family.xml`, `jetbrains_mono_family.xml`
- Create: `licenses/Manrope-OFL.txt`, `licenses/JetBrainsMono-OFL.txt`
- Modify: `ui/src/main/res/values/styles_type.xml` (full rewrite — content below)
- Modify: `ui/src/main/res/values/themes.xml` (add 2 lines in `AmneziaWgTheme`)
- Modify: `ui/src/main/res/values-night/themes.xml` (add 2 lines in the app theme)

**Interfaces:**
- Produces: font resources `@font/manrope_regular|medium|semibold`, `@font/jetbrains_mono_family`, `@font/manrope_family`. No code interface.

- [ ] **Step 1: Copy font files into the project**

```bash
cd /home/kowalski/projects/vpn/amneziawg-android
mkdir -p ui/src/main/res/font
cp ~/Documents/amneziawg-refresh-assets/res/font/manrope_regular.ttf \
   ~/Documents/amneziawg-refresh-assets/res/font/manrope_medium.ttf \
   ~/Documents/amneziawg-refresh-assets/res/font/manrope_semibold.ttf \
   ~/Documents/amneziawg-refresh-assets/res/font/jetbrains_mono_regular.ttf \
   ~/Documents/amneziawg-refresh-assets/res/font/jetbrains_mono_medium.ttf \
   ~/Documents/amneziawg-refresh-assets/res/font/manrope_family.xml \
   ~/Documents/amneziawg-refresh-assets/res/font/jetbrains_mono_family.xml \
   ui/src/main/res/font/
ls ui/src/main/res/font/
```

Expected: the 7 files listed.

- [ ] **Step 2: Copy the OFL license files**

```bash
mkdir -p licenses
cp ~/Documents/amneziawg-refresh-assets/licenses/Manrope-OFL.txt \
   ~/Documents/amneziawg-refresh-assets/licenses/JetBrainsMono-OFL.txt \
   licenses/
ls licenses/
```

Expected: `JetBrainsMono-OFL.txt  Manrope-OFL.txt`.

- [ ] **Step 3: Rewrite `styles_type.xml` to point at the bundled fonts**

Replace the entire contents of `ui/src/main/res/values/styles_type.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- AmneziaWG Visual Refresh - typography.
     PROPORTIONAL TEXT = Manrope (bundled, OFL 1.1). MONOSPACE = JetBrains Mono
     (bundled) for keys / IPs / stat values so digits + hex align.
     Fonts live in res/font/ (manrope_regular|medium|semibold,
     jetbrains_mono_regular|medium + the two *_family.xml). Licenses in /licenses.

     Manrope weights: Regular 400 (body/labels), Medium 500 (field values),
     SemiBold 600 (titles, name, section headers, status, stat labels, buttons).
     M3 scale sizes are inherited from the parent textAppearance; we only set
     fontFamily + color + caps/letter-spacing. -->
<resources>

    <!-- Toolbar / screen title -->
    <style name="TextAppearance.Awg.Title" parent="TextAppearance.Material3.TitleLarge">
        <item name="android:textColor">?attr/colorOnSurface</item>
        <item name="android:fontFamily">@font/manrope_semibold</item>
        <item name="fontFamily">@font/manrope_semibold</item>
    </style>

    <!-- Tunnel name (list row + detail header) -->
    <style name="TextAppearance.Awg.TunnelName" parent="TextAppearance.Material3.TitleMedium">
        <item name="android:textColor">?attr/colorOnSurface</item>
        <item name="android:fontFamily">@font/manrope_semibold</item>
        <item name="fontFamily">@font/manrope_semibold</item>
    </style>

    <!-- Section header: INTERFACE / OBFUSCATION / PEER / APPLICATIONS -->
    <style name="TextAppearance.Awg.SectionHeader" parent="TextAppearance.Material3.LabelLarge">
        <item name="android:textColor">?attr/colorPrimary</item>
        <item name="android:textAllCaps">true</item>
        <item name="android:letterSpacing">0.08</item>
        <item name="android:fontFamily">@font/manrope_semibold</item>
        <item name="fontFamily">@font/manrope_semibold</item>
    </style>

    <!-- Field VALUE (addresses, keys, endpoint, DNS) -->
    <style name="TextAppearance.Awg.FieldValue" parent="TextAppearance.Material3.BodyLarge">
        <item name="android:textColor">?attr/colorOnSurface</item>
        <item name="android:fontFamily">@font/manrope_medium</item>
        <item name="fontFamily">@font/manrope_medium</item>
    </style>

    <!-- Field LABEL (above each value) -->
    <style name="TextAppearance.Awg.FieldLabel" parent="TextAppearance.Material3.BodySmall">
        <item name="android:textColor">?attr/colorOnSurfaceVariant</item>
        <item name="android:fontFamily">@font/manrope_regular</item>
        <item name="fontFamily">@font/manrope_regular</item>
    </style>

    <!-- Status label: Connected / Connecting / Disconnected (color set in code) -->
    <style name="TextAppearance.Awg.Status" parent="TextAppearance.Material3.LabelLarge">
        <item name="android:fontFamily">@font/manrope_semibold</item>
        <item name="fontFamily">@font/manrope_semibold</item>
    </style>

    <!-- Stat label: DOWNLOAD / UPLOAD / HANDSHAKE -->
    <style name="TextAppearance.Awg.StatLabel" parent="TextAppearance.Material3.LabelSmall">
        <item name="android:textColor">?attr/colorOnSurfaceVariant</item>
        <item name="android:textAllCaps">true</item>
        <item name="android:letterSpacing">0.05</item>
        <item name="android:fontFamily">@font/manrope_semibold</item>
        <item name="fontFamily">@font/manrope_semibold</item>
    </style>

    <!-- Stat VALUE + keys/IPs: JetBrains Mono (bundled) so digits/hex align -->
    <style name="TextAppearance.Awg.Mono" parent="TextAppearance.Material3.LabelLarge">
        <item name="android:fontFamily">@font/jetbrains_mono_family</item>
        <item name="fontFamily">@font/jetbrains_mono_family</item>
        <item name="android:textColor">?attr/colorOnSurface</item>
    </style>

    <!-- Accent monospace: download/upload RATES on a connected row.
         Teal is intentional (ties live throughput to the active state).
         Use Mono (neutral onSurface) for handshake, keys and IPs. -->
    <style name="TextAppearance.Awg.Mono.Accent" parent="TextAppearance.Awg.Mono">
        <item name="android:textColor">?attr/colorPrimary</item>
    </style>

    <!-- Primary button label (Add a tunnel / Save) -->
    <style name="TextAppearance.Awg.Button" parent="TextAppearance.Material3.LabelLarge">
        <item name="android:fontFamily">@font/manrope_semibold</item>
        <item name="fontFamily">@font/manrope_semibold</item>
    </style>

    <!-- Empty-state title -->
    <style name="TextAppearance.Awg.EmptyTitle" parent="TextAppearance.Material3.TitleLarge">
        <item name="android:textColor">?attr/colorOnSurface</item>
        <item name="android:fontFamily">@font/manrope_semibold</item>
        <item name="fontFamily">@font/manrope_semibold</item>
    </style>

    <!-- Empty-state body -->
    <style name="TextAppearance.Awg.EmptyBody" parent="TextAppearance.Material3.BodyMedium">
        <item name="android:textColor">?attr/colorOnSurfaceVariant</item>
        <item name="android:fontFamily">@font/manrope_regular</item>
        <item name="fontFamily">@font/manrope_regular</item>
    </style>

</resources>
```

> Note: `SectionHeader` keeps `letterSpacing 0.08` (the project's current value), not the asset pack's 0.12 — don't regress existing phase-6 tuning.

- [ ] **Step 4: Set Manrope as the app-wide default (light theme)**

In `ui/src/main/res/values/themes.xml`, inside the `<style name="AmneziaWgTheme" ...>` block, add these two lines immediately before the closing `</style>` (right after the `colorOutlineVariant` item):

```xml
        <!-- Visual refresh: app-wide default proportional face (Manrope). -->
        <item name="fontFamily">@font/manrope_regular</item>
        <item name="android:fontFamily">@font/manrope_regular</item>
```

- [ ] **Step 5: Set Manrope as the app-wide default (dark theme)**

Open `ui/src/main/res/values-night/themes.xml`, find the app theme `<style name="AmneziaWgTheme" ...>` block, and add the same two lines immediately before its closing `</style>`:

```xml
        <!-- Visual refresh: app-wide default proportional face (Manrope). -->
        <item name="fontFamily">@font/manrope_regular</item>
        <item name="android:fontFamily">@font/manrope_regular</item>
```

- [ ] **Step 6: Build to verify resources compile and fonts resolve**

Run: `./gradlew :ui:assembleDebug`
Expected: `BUILD SUCCESSFUL`. (A typo in a `@font/...` reference or font XML fails resource linking here.)

- [ ] **Step 7: Commit**

```bash
git add ui/src/main/res/font/ licenses/ \
        ui/src/main/res/values/styles_type.xml \
        ui/src/main/res/values/themes.xml \
        ui/src/main/res/values-night/themes.xml
git commit -m "$(cat <<'MSG'
refresh: bundle Manrope + JetBrains Mono and roll out app-wide

Add the bundled .ttf faces + font families, repoint the
TextAppearance.Awg.* styles, set Manrope as the app-wide theme
default (light + dark), and ship the OFL license texts.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
MSG
)"
```

---

### Task 2: Compact handshake format (unified across all three sites)

**Files:**
- Modify: `ui/src/main/res/values/strings.xml:253-255` (replace `stat_ago_minutes` with three new strings)
- Modify: `ui/src/main/java/org/amnezia/awg/util/QuantityFormatter.kt` (add `formatEpochAgoShort`)
- Modify: `ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt:329-330, 352`
- Modify: `ui/src/main/java/org/amnezia/awg/databinding/BindingAdapters.kt:280-286`

**Interfaces:**
- Consumes: existing strings `stat_ago_seconds` (`"%1$ds ago"`), `stat_ago_never` (`"—"`).
- Produces: `QuantityFormatter.formatEpochAgoShort(epochMillis: Long): String` — returns `"—"` for `epochMillis <= 0`, else compact relative age (`"23s ago"` / `"1m 14s ago"` / `"1h 4m ago"` / `"2d 3h ago"`). Used by `TunnelDetailFragment` and `BindingAdapters`.

- [ ] **Step 1: Replace the strings**

In `ui/src/main/res/values/strings.xml`, the current lines 253-255 are:

```xml
    <string name="stat_ago_seconds">%1$ds ago</string>
    <string name="stat_ago_minutes">%1$dm ago</string>
    <string name="stat_ago_never">—</string>
```

Replace those three lines with (keep `stat_ago_seconds` and `stat_ago_never`; drop `stat_ago_minutes`; add three finer tiers):

```xml
    <string name="stat_ago_seconds">%1$ds ago</string>
    <string name="stat_ago_min_sec">%1$dm %2$ds ago</string>
    <string name="stat_ago_hour_min">%1$dh %2$dm ago</string>
    <string name="stat_ago_day_hour">%1$dd %2$dh ago</string>
    <string name="stat_ago_never">—</string>
```

- [ ] **Step 2: Add `formatEpochAgoShort` to `QuantityFormatter.kt`**

In `ui/src/main/java/org/amnezia/awg/util/QuantityFormatter.kt`, add this function inside the `object QuantityFormatter`, immediately after the existing `formatEpochAgo(...)` function (before the final closing `}` of the object):

```kotlin
    /** Compact relative handshake age: "23s ago" / "1m 14s ago" / "1h 4m ago" / "2d 3h ago". */
    fun formatEpochAgoShort(epochMillis: Long): String {
        val context = Application.get().applicationContext
        if (epochMillis <= 0L)
            return context.getString(R.string.stat_ago_never)
        val s = ((System.currentTimeMillis() - epochMillis) / 1000L).coerceAtLeast(0L)
        return when {
            s < 60L    -> context.getString(R.string.stat_ago_seconds, s.toInt())
            s < 3600L  -> context.getString(R.string.stat_ago_min_sec, (s / 60L).toInt(), (s % 60L).toInt())
            s < 86400L -> context.getString(R.string.stat_ago_hour_min, (s / 3600L).toInt(), ((s % 3600L) / 60L).toInt())
            else       -> context.getString(R.string.stat_ago_day_hour, (s / 86400L).toInt(), ((s % 86400L) / 3600L).toInt())
        }
    }
```

(`Application`, `R`, and `System` are already imported/available in this file.)

- [ ] **Step 3: Wire the detail summary card**

In `ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt`, the current summary-card block (lines ~325-330) is:

```kotlin
            val latestHandshake = statistics.peers()
                .map { statistics.peer(it)?.latestHandshakeEpochMillis ?: 0L }
                .maxOrNull() ?: 0L
            binding.summaryHandshake.text =
                if (latestHandshake <= 0L) getString(R.string.stat_ago_never)
                else QuantityFormatter.formatEpochAgo(latestHandshake)
```

Replace the `binding.summaryHandshake.text = ...` assignment (the `if/else`) with the single call (the `<=0` case is handled inside the formatter):

```kotlin
            val latestHandshake = statistics.peers()
                .map { statistics.peer(it)?.latestHandshakeEpochMillis ?: 0L }
                .maxOrNull() ?: 0L
            binding.summaryHandshake.text = QuantityFormatter.formatEpochAgoShort(latestHandshake)
```

- [ ] **Step 4: Wire the peer "Latest handshake" row**

In the same file, line ~352, change:

```kotlin
                    peer.latestHandshakeText.text = QuantityFormatter.formatEpochAgo(peerStats.latestHandshakeEpochMillis)
```

to:

```kotlin
                    peer.latestHandshakeText.text = QuantityFormatter.formatEpochAgoShort(peerStats.latestHandshakeEpochMillis)
```

- [ ] **Step 5: Wire the list-row stats strip**

In `ui/src/main/java/org/amnezia/awg/databinding/BindingAdapters.kt`, the current block (lines ~278-286) is:

```kotlin
        val handshakeText = root.findViewById<TextView>(R.id.stat_handshake)
        val epoch = latestHandshakeEpochMillis(stats)
        handshakeText?.text = if (epoch <= 0) {
            ctx.getString(R.string.stat_ago_never)
        } else {
            val secs = ((System.currentTimeMillis() - epoch) / 1000L).coerceAtLeast(0L)
            if (secs < 60) ctx.getString(R.string.stat_ago_seconds, secs.toInt())
            else ctx.getString(R.string.stat_ago_minutes, (secs / 60L).toInt())
        }
```

Replace it with:

```kotlin
        val handshakeText = root.findViewById<TextView>(R.id.stat_handshake)
        val epoch = latestHandshakeEpochMillis(stats)
        handshakeText?.text = QuantityFormatter.formatEpochAgoShort(epoch)
```

Ensure `QuantityFormatter` is imported in this file (add `import org.amnezia.awg.util.QuantityFormatter` if the build reports it unresolved).

- [ ] **Step 6: Build to verify everything compiles**

Run: `./gradlew :ui:assembleDebug`
Expected: `BUILD SUCCESSFUL`. A leftover reference to `R.string.stat_ago_minutes` (now removed) would fail here — grep to confirm none remain:

```bash
grep -rn "stat_ago_minutes" ui/src/main/
```

Expected: no output.

- [ ] **Step 7: Commit**

```bash
git add ui/src/main/res/values/strings.xml \
        ui/src/main/java/org/amnezia/awg/util/QuantityFormatter.kt \
        ui/src/main/java/org/amnezia/awg/fragment/TunnelDetailFragment.kt \
        ui/src/main/java/org/amnezia/awg/databinding/BindingAdapters.kt
git commit -m "$(cat <<'MSG'
refresh: compact tiered handshake time across detail + list

Add QuantityFormatter.formatEpochAgoShort (23s / 1m 14s / 1h 4m /
2d 3h ago) and route the detail summary card, peer row, and
list-row stats strip through it, retiring the coarse two-tier
formatter and the stat_ago_minutes string.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
MSG
)"
```

---

## Final verification (on-device — manual)

After both tasks land, install the debug build and check:

1. **Handshake formatting** — connect a tunnel; in the **detail summary card**, the **peer "Latest handshake" row**, and the **list-row strip**, handshake age reads identically and compactly: `23s ago` when fresh, `1m 14s ago` at minutes, `1h 4m ago` if stale, `—` before the first handshake.
2. **Fonts** — Manrope renders on titles/labels/body and JetBrains Mono on keys/IPs/stat values across the list, detail, editor, and empty-state screens. Check dense rows (tunnel list, field rows) for any vertical-metric clipping from the font swap.
3. Light and dark themes both pick up Manrope (Step 4 + Step 5).

## Self-review notes

- **Spec coverage:** Part A (compact handshake: formatter + 3 strings + 3 wire-ups) → Task 2. Part B (fonts: res/font, styles_type repoint, app-wide default both themes, OFL licenses) → Task 1. Both covered.
- **Type consistency:** `formatEpochAgoShort(epochMillis: Long): String` defined in Task 2 Step 2; consumed identically in Steps 3-5 (detail summary, peer row, list strip).
- **No leftover `stat_ago_minutes`:** removed in Step 1, its sole consumer rewritten in Step 5, grep-checked in Step 6.
