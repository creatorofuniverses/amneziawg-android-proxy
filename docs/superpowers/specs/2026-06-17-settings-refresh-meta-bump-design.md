# Settings screen refresh + version/metadata bump — design

**Date:** 2026-06-17
**Base branch:** `refreshed_ui` (current HEAD `c7b2e0a5`)
**Asset pack:** `~/Documents/amneziawg-refresh-assets/SPEC-settings.md` (treated as a proposal, reconciled against code reality)

Two related but independently-shippable pieces of work:

- **Part A — Settings screen restyle:** bring the settings screen into the Network Teal system. Pure visual; no settings added/removed/reordered, no keys or behavior changed.
- **Part B — Version + metadata bump:** a self-contained, `master`-safe commit (version 2.1.0, fork-distinct applicationId, attribution) that can be cherry-picked onto `master` independent of the visual refresh.

---

## Reality reconciliation (important)

The designer's `SPEC-settings.md` assumed a custom-layout screen with bespoke `MaterialCardView` groups and named only 4 rows. The real screen is an **androidx `PreferenceFragmentCompat`** (`SettingsActivity.kt:47`, `addPreferencesFromResource(R.xml.preferences)`) backed by `res/xml/preferences.xml` with **10 preferences** of mixed types:

| key | type | group (new) |
|---|---|---|
| `version` | `VersionPreference` (custom) | About |
| `restore_on_boot` | `CheckBoxPreference` | General |
| `dark_theme` | `CheckBoxPreference` | General |
| `multiple_tunnels` | `CheckBoxPreference` | General |
| `allow_remote_control_intents` | `CheckBoxPreference` | General |
| `quick_tile` | `QuickTilePreference` (custom) | General |
| `zip_exporter` | `ZipExporterPreference` (custom) | Actions |
| `log_viewer` | `Preference` → `LogViewerActivity` | Actions |
| `tools_installer` | `ToolsInstallerPreference` (custom, conditional) | Advanced |
| `kernel_module_enabler` | `KernelModuleEnablerPreference` (custom, conditional) | Advanced |

Consequences baked into this design:
- We restyle **within** the preference framework (keeps every preference's logic: async backend version, SAF zip export, quick-tile, root tools-installer, kernel-module, conditional visibility). We do **not** rewrite to a custom layout.
- The spec's "old multicolor WA glyph" is actually the mono `@drawable/ic_icon`; the spec's "Advanced expandable card" has no existing analog — we realize "Advanced" as a normal `PreferenceCategory` over the existing root/kernel prefs (no new expand/collapse behavior invented).

---

## Part A — Settings restyle

### A1 · Toolbar
`SettingsActivity` (`extends AppCompatActivity`) currently uses the default ActionBar. Add a content layout `res/layout/settings_activity.xml`: a flat `MaterialToolbar` (`?attr/colorSurface`, `app:elevation`/stateListAnimator 0dp, navigationIcon `@drawable/ic_arrow_back` tinted `colorOnSurface`, title `@string/settings` with `app:titleTextAppearance="@style/TextAppearance.Awg.Title"`) above a `FragmentContainerView` (id `settings_fragment_container`). `onCreate` sets this layout, calls `setSupportActionBar(toolbar)`, and adds `SettingsFragment` into the container. The existing `onOptionsItemSelected`/up→`finish()` logic is preserved (navigation icon click routes to it).

### A2 · Global preference theme + row layout
Add `styles-awg-preferences.xml` with `@style/Awg.PreferenceThemeOverlay` (parent `@style/PreferenceThemeOverlay`) and set `<item name="preferenceTheme">@style/Awg.PreferenceThemeOverlay</item>` on the settings activity theme. The overlay points:
- `preferenceStyle` / `dialogPreferenceStyle` → custom `@layout/preference_awg`
- `switchPreferenceCompatStyle` → `@layout/preference_awg` + `app:widgetLayout="@layout/preference_widget_switch"`
- `preferenceCategoryStyle` → `@layout/preference_awg_category`

`preference_awg.xml` row anatomy: `[leading icon 20dp, tint colorPrimary] · [title TitleMedium onSurface / summary BodySmall onSurfaceVariant, stacked] · [widget frame]`, 14dp horizontal gap, 13dp vertical padding, 48dp min height, whole row is the tap target. Icon is `gone` when a preference has no `android:icon`.

`preference_awg_category.xml`: a section header styled like `TextAppearance.Awg.SectionHeader` (teal, ALL CAPS, Manrope SemiBold, letterSpacing 0.08), padded for the 12dp inter-group gap.

### A3 · Grouped card backgrounds
Wrap the 10 prefs into four `PreferenceCategory` groups in `preferences.xml` (About / General / Actions / Advanced — per the table above), preserving order within each group and all keys.

A custom `RecyclerView.ItemDecoration` (`PreferenceGroupCardDecoration`, new Kotlin class, installed on the preference list in `onCreateRecyclerView`/`onViewCreated`) draws, per contiguous run of rows inside a category, a single rounded `surfaceContainer` card: 16dp outer radius (top corners on the first row, bottom corners on the last, square in between; a lone row gets all four), 1dp `outlineVariant` stroke, and 1dp `outlineVariant` inner dividers between rows. 12dp gap between cards, 16dp screen side padding. Colors resolve from theme attrs (`colorSurfaceContainer`, `colorOutlineVariant`) so light/dark both work. This decoration is the one non-trivial component; it is self-contained and testable in isolation. *Fallback if the decoration proves problematic on-device: a contiguous `surfaceContainer` row background without rounded outer corners — same grouping, square corners.*

### A4 · About row
Custom `@layout/preference_awg_about` bound by `VersionPreference`: a 48dp `surface` (#0E0F11) tile, 14dp radius, holding the 30dp `@drawable/ic_logo_empty_state` Signal mark (ring+wave teal, A theme-aware); then the existing title + summary (the async backend line); and a trailing **version pill** (`primaryContainer` bg, `onPrimaryContainer` text, full radius) showing `BuildConfig.VERSION_NAME`. Replaces the `android:icon="@drawable/ic_icon"` on the `version` preference. `VersionPreference` gets a small `onBindViewHolder` override to populate the pill text from `BuildConfig.VERSION_NAME`; its existing `getTitle`/`getSummary`/`onClick` are unchanged.

**Branding (rebrand rides with the restyle, per decision):** `version_title` (`strings.xml:278`) "AmneziaWG for Android v%s" → **"AmneziaWG Proxy v%s"**, matching the `app_title` rebrand. (This string change lives in Part A, NOT the cherry-pickable Part B commit — see Part B rationale.)

### A5 · Switches & icons
- Convert the 4 `CheckBoxPreference` (`restore_on_boot`, `dark_theme`, `multiple_tunnels`, `allow_remote_control_intents`) → `SwitchPreferenceCompat`. Same `android:key`, `android:defaultValue`, and summaryOn/Off — persistence and behavior identical; only the widget changes. `preference_widget_switch.xml` uses a teal `MaterialSwitch`/`SwitchCompat` reusing the app's existing switch track/thumb tint (the `awg_switch_*` color selectors; add them if not already present in the project).
- New teal-tintable vector drawables (2px stroke, `colorPrimary` tint): `ic_export` (tray + down arrow) on `zip_exporter`, `ic_log` (document with lines) on `log_viewer`. About uses the Signal tile (A4). Toggle rows and the remaining custom prefs render without a leading icon (title/subtitle only) — we do not invent glyphs for every row.

### A6 · Optional footer
Spec §5 footer ("Made for a free and open internet", `#4A4F55`, 11sp, centered) is **out of scope** for the first pass (nice-to-have; revisit on-device if desired).

### What does NOT change
Preference keys, defaults, summaries (except `version_title` text), click handlers, conditional visibility, the export/quick-tile/tools/kernel logic, the DataStore/SharedPreferences backing, and the IA (order within groups preserved).

---

## Part B — Version + metadata bump (separate, cherry-pickable commit)

One self-contained commit touching only version/identity/attribution files, so it applies cleanly on `master` (which lacks the refresh):

1. **Version** — `gradle.properties`: `amneziawgVersionCode 11 → 12`, `amneziawgVersionName 2.0.1 → 2.1.0`. The About-row pill (A4) and `VersionPreference.getTitle` pick this up via `BuildConfig.VERSION_NAME`.

2. **applicationId (fork-distinct, decoupled from namespace)** —
   - `gradle.properties`: keep `amneziawgPackageName=org.amnezia.awg` (drives `namespace` = the R/BuildConfig package, so **no code changes**); add `amneziawgApplicationId=org.amnezia.awg.proxy`.
   - `ui/build.gradle.kts`: keep `namespace = pkg` (line 20); change `applicationId = pkg` (line 22) → `applicationId = providers.gradleProperty("amneziawgApplicationId").get()`.
   - **Verified safe:** the `CONTROL_TUNNELS` permission (`${applicationId}.permission.…`), the `exported-log` provider authority (`${applicationId}.exported-log`), and the log content URI (`content://${BuildConfig.APPLICATION_ID}.exported-log/…`) all derive from applicationId and will track the new id automatically. The hardcoded `org.amnezia.awg.action.SET_TUNNEL_UP/DOWN/REFRESH_TUNNEL_STATES` intent actions are the **remote-control public API contract** and are intentionally left unchanged. `RootShell` uses runtime `getPackageName()` — tracks automatically.

3. **Attribution** — `README.md`: add a fork/maintainer notice stating this is an independent fork distributed as **AWG Proxy** under applicationId `org.amnezia.awg.proxy`, **preserving** the upstream AmneziaWG / WireGuard LLC attribution and links. The per-file `Copyright © 2017-2023 WireGuard LLC … Apache-2.0` headers are left intact (not rewriting 30+ files); Apache-2.0 is preserved.

**Why branding strings stay OUT of this commit:** the `app_name`/`app_title`/`version_title` "Proxy" rebrand exists only on `refreshed_ui` (from phase 4+), not on `master`. Putting `version_title` (or other branding strings) in the cherry-pickable commit would make `master` inconsistent (a "Proxy" version string with an un-renamed app). Branding edits therefore live in Part A (refresh-only); Part B carries only what is coherent on both branches.

---

## Integration & branch strategy

- **Part B** is committed as **one discrete commit directly on `refreshed_ui`** (it is a small, mechanical, standalone change that must remain a single cherry-pickable commit — squashing it into a feature branch would destroy that). It is cherry-picked onto `master` whenever the user chooses (not automatically).
- **Part A** is built on a feature branch `refresh-settings` cut off `refreshed_ui` *after* Part B lands, then squash-merged into `refreshed_ui` like prior phases.
- **Verification:** `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain` (the project's compile gate; assembleDebug avoided — heavy native build). On-device pass: settings screen renders grouped cards in light + dark; About row shows the Signal mark + "AmneziaWG Proxy v2.1.0" + pill; switches toggle and persist; export/log/quick-tile/advanced rows still function; and a debug install confirms the new applicationId installs alongside the official app and that the log-viewer share (provider authority) still works under `org.amnezia.awg.proxy`.

## Out of scope (explicitly)
- Custom-layout rewrite of the settings screen (rejected — high regression risk).
- The §5 footer line (A6).
- Touching the remote-control action-string API contract.
- Rewriting per-file copyright headers.
- Translating any new strings into the ~35 locales (English fallback, consistent with prior phases).
- Merging `refreshed_ui` → `master` (separate, user-initiated step).
