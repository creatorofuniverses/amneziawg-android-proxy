# Settings Refresh + Version/Meta Bump Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the settings screen into the Network Teal system (within the existing `PreferenceFragmentCompat`) and ship a separate, cherry-pickable version/metadata bump that gives the fork its own identity.

**Architecture:** Two independent pieces. Part B (Task 1) is a standalone meta commit on `refreshed_ui` (version 2.1.0, fork-distinct applicationId via namespace/applicationId decoupling, README attribution) — kept discrete so it cherry-picks onto `master`. Part A (Tasks 2–6) is the settings restyle on a `refresh-settings` branch: flat MaterialToolbar, a global preference theme + custom row/category/switch layouts, the 10 prefs regrouped into four `PreferenceCategory` cards, a Signal-mark About row, and a `RecyclerView.ItemDecoration` that draws the grouped rounded cards.

**Tech Stack:** Android XML resources, Material 3 (Material Components 1.11.0), androidx.preference 1.2.1, Kotlin, minSdk 24, build module `:ui`.

## Global Constraints

- **Verify command (this env):** `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain` → `BUILD SUCCESSFUL`. Do NOT use `assembleDebug` (heavy native build). No JVM unit-test harness in `:ui` — verification is compile + on-device visual.
- **No new Gradle dependencies.**
- **No settings added/removed/reordered; no preference keys, defaults, or behavior changed** (Part A is visual-only). The only string content change is `version_title`.
- **applicationId = `org.amnezia.awg.proxy`; namespace stays `org.amnezia.awg`** (code/R/BuildConfig package unchanged).
- **versionCode 12, versionName 2.1.0.**
- **Do NOT touch** the remote-control intent action strings `org.amnezia.awg.action.SET_TUNNEL_UP/DOWN`/`REFRESH_TUNNEL_STATES` (public API contract), and do NOT rewrite per-file `Copyright © 2017-2023 WireGuard LLC` / Apache-2.0 headers.
- **Branding strings stay out of the Task 1 (meta) commit** — `version_title` rebrand lives in Task 4 (Part A only), to keep the meta commit coherent on `master`.
- **Commit trailer:** end each commit message with `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- **Theme tokens:** cards `?attr/colorSurfaceContainer`; stroke/divider `?attr/colorOutlineVariant`; icon tint `?attr/colorPrimary`; About tile `?attr/colorSurface`. All resolve per light/dark.

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `gradle.properties` | Modify | versionCode/Name; add `amneziawgApplicationId` |
| `ui/build.gradle.kts` | Modify | wire `applicationId` to the new property |
| `README.md` | Modify | fork attribution notice |
| `ui/src/main/res/layout/settings_activity.xml` | Create | toolbar + fragment container |
| `ui/src/main/java/org/amnezia/awg/activity/SettingsActivity.kt` | Modify | host layout, MaterialToolbar, drop `initialExpandedChildrenCount` |
| `ui/src/main/res/values/styles-awg-preferences.xml` | Create | preference theme overlay + row/category/switch styles + pref text appearances |
| `ui/src/main/res/values/styles.xml` (AppThemeBase) | Modify | add `preferenceTheme` |
| `ui/src/main/res/layout/preference_awg.xml` | Create | standard row (icon/title/summary/widget) |
| `ui/src/main/res/layout/preference_awg_category.xml` | Create | section header |
| `ui/src/main/res/layout/preference_widget_switch.xml` | Create | teal MaterialSwitch widget |
| `ui/src/main/res/layout/preference_awg_about.xml` | Create | About row (Signal tile + title/summary + pill) |
| `ui/src/main/res/drawable/ic_export.xml` | Create | export icon |
| `ui/src/main/res/drawable/ic_log.xml` | Create | log icon |
| `ui/src/main/res/xml/preferences.xml` | Modify | wrap in 4 categories; checkbox→switch; icons; About layout |
| `ui/src/main/res/values/strings.xml` | Modify | `version_title` rebrand; `version_pill` |
| `ui/src/main/java/org/amnezia/awg/preference/VersionPreference.kt` | Modify | bind the version pill |
| `ui/src/main/java/org/amnezia/awg/preference/PreferenceGroupCardDecoration.kt` | Create | grouped rounded-card backgrounds |

---

### Task 1: Version + metadata bump (Part B — standalone, cherry-pickable)

Committed on `refreshed_ui` BEFORE the `refresh-settings` branch is cut, so it stays a discrete commit.

**Files:**
- Modify: `gradle.properties:1-3`
- Modify: `ui/build.gradle.kts:6,20,22`
- Modify: `README.md`

**Interfaces:**
- Produces: applicationId `org.amnezia.awg.proxy` at install time; `BuildConfig.VERSION_NAME = "2.1.0"`. No code symbols.

- [ ] **Step 1: Bump version + add the applicationId property**

In `gradle.properties`, the current lines 1–3 are:
```properties
amneziawgVersionCode=11
amneziawgVersionName=2.0.1
amneziawgPackageName=org.amnezia.awg
```
Replace with:
```properties
amneziawgVersionCode=12
amneziawgVersionName=2.1.0
amneziawgPackageName=org.amnezia.awg
amneziawgApplicationId=org.amnezia.awg.proxy
```

- [ ] **Step 2: Point applicationId at the new property (decouple from namespace)**

In `ui/build.gradle.kts`, after the existing line 6 `val pkg: String = providers.gradleProperty("amneziawgPackageName").get()`, add:
```kotlin
val appId: String = providers.gradleProperty("amneziawgApplicationId").get()
```
Leave line 20 `namespace = pkg` unchanged. Change line 22 from:
```kotlin
        applicationId = pkg
```
to:
```kotlin
        applicationId = appId
```

- [ ] **Step 3: Add the fork attribution to README**

Insert this block in `README.md` immediately after the first paragraph (after the existing "This is an Android GUI for AmneziaWG." line):
```markdown

## Fork notice

This is an independent fork, distributed as **AWG Proxy** under the application ID
`org.amnezia.awg.proxy`, and is **not affiliated with or endorsed by** the original
AmneziaWG / Amnezia VPN developers or WireGuard LLC. It installs alongside the
official app rather than replacing it. All upstream code remains under its original
Apache-2.0 license and copyright (© WireGuard LLC); see `COPYING`.
```

- [ ] **Step 4: Verify the build and the resolved identity**

Run: `./gradlew :ui:processDebugResources :ui:processDebugManifest --console=plain`
Expected: `BUILD SUCCESSFUL`. Then confirm the merged manifest carries the new id:
```bash
grep -o 'package="[^"]*"\|org.amnezia.awg.proxy.exported-log\|org.amnezia.awg.proxy.permission' ui/build/intermediates/merged_manifest/debug/processDebugManifest/AndroidManifest.xml | sort -u
```
Expected: the `exported-log` authority and `CONTROL_TUNNELS` permission now read `org.amnezia.awg.proxy.*`, while the R/BuildConfig package stays `org.amnezia.awg`.

- [ ] **Step 5: Commit (discrete, on `refreshed_ui`)**

```bash
git add gradle.properties ui/build.gradle.kts README.md
git commit -m "$(cat <<'MSG'
chore: bump to 2.1.0 and fork-distinct applicationId

Version 2.0.1 -> 2.1.0 (code 11 -> 12). Decouple applicationId from
namespace: keep the org.amnezia.awg code/R/BuildConfig package, but
install as org.amnezia.awg.proxy so this fork coexists with the official
app. README gains a fork/attribution notice; upstream Apache-2.0
copyright preserved.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
MSG
)"
```

> After this commit, the controller cuts `git checkout -b refresh-settings` for Tasks 2–6.

---

### Task 2: Settings toolbar + activity host

**Files:**
- Create: `ui/src/main/res/layout/settings_activity.xml`
- Modify: `ui/src/main/java/org/amnezia/awg/activity/SettingsActivity.kt:30-37`

**Interfaces:**
- Produces: layout with `@id/toolbar` (MaterialToolbar) and `@id/settings_fragment_container`; the fragment is hosted in the container (was `android.R.id.content`).

- [ ] **Step 1: Create the activity layout**

Create `ui/src/main/res/layout/settings_activity.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="?attr/colorSurface">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:background="?attr/colorSurface"
        android:elevation="0dp"
        app:navigationIcon="@drawable/ic_arrow_back"
        app:navigationIconTint="?attr/colorOnSurface"
        app:title="@string/settings"
        app:titleTextAppearance="@style/TextAppearance.Awg.Title" />

    <androidx.fragment.app.FragmentContainerView
        android:id="@+id/settings_fragment_container"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
</LinearLayout>
```

- [ ] **Step 2: Host the layout + toolbar in SettingsActivity**

In `SettingsActivity.kt`, replace the `onCreate` body (lines 30–37) with:
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        if (supportFragmentManager.findFragmentById(R.id.settings_fragment_container) == null) {
            supportFragmentManager.commit {
                add(R.id.settings_fragment_container, SettingsFragment())
            }
        }
    }
```
(Leave `onOptionsItemSelected` unchanged.)

- [ ] **Step 3: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add ui/src/main/res/layout/settings_activity.xml ui/src/main/java/org/amnezia/awg/activity/SettingsActivity.kt
git commit -m "$(cat <<'MSG'
refresh(settings): flat MaterialToolbar + fragment container

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
MSG
)"
```

---

### Task 3: Preference theme, row/category/switch layouts, icons

**Files:**
- Create: `ui/src/main/res/values/styles-awg-preferences.xml`
- Modify: `ui/src/main/res/values/styles.xml` (the `AppThemeBase` style)
- Create: `ui/src/main/res/layout/preference_awg.xml`
- Create: `ui/src/main/res/layout/preference_awg_category.xml`
- Create: `ui/src/main/res/layout/preference_widget_switch.xml`
- Create: `ui/src/main/res/drawable/ic_export.xml`, `ui/src/main/res/drawable/ic_log.xml`

**Interfaces:**
- Produces: `@style/Awg.PreferenceThemeOverlay`, layouts `@layout/preference_awg` / `@layout/preference_awg_category` / `@layout/preference_widget_switch`, drawables `@drawable/ic_export` / `@drawable/ic_log`. Consumed by Tasks 4–6.
- Row layout exposes the framework ids `@android:id/icon`, `@android:id/title`, `@android:id/summary`, `@android:id/widget_frame`. Switch widget id is `@android:id/switch_widget`.

- [ ] **Step 1: Preference theme overlay + styles**

Create `ui/src/main/res/values/styles-awg-preferences.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Awg.PreferenceThemeOverlay" parent="@style/PreferenceThemeOverlay">
        <item name="preferenceStyle">@style/Awg.Preference</item>
        <item name="switchPreferenceCompatStyle">@style/Awg.SwitchPreference</item>
        <item name="preferenceCategoryStyle">@style/Awg.PreferenceCategory</item>
    </style>

    <style name="Awg.Preference" parent="@style/Preference.Material">
        <item name="android:layout">@layout/preference_awg</item>
    </style>

    <style name="Awg.SwitchPreference" parent="@style/Preference.SwitchPreferenceCompat.Material">
        <item name="android:layout">@layout/preference_awg</item>
        <item name="android:widgetLayout">@layout/preference_widget_switch</item>
    </style>

    <style name="Awg.PreferenceCategory" parent="@style/Preference.Category.Material">
        <item name="android:layout">@layout/preference_awg_category</item>
        <item name="allowDividerAbove">false</item>
        <item name="allowDividerBelow">false</item>
    </style>

    <!-- Settings row title: Manrope Medium, TitleMedium size, onSurface -->
    <style name="TextAppearance.Awg.PrefTitle" parent="TextAppearance.Material3.TitleMedium">
        <item name="android:textColor">?attr/colorOnSurface</item>
        <item name="android:fontFamily">@font/manrope_medium</item>
        <item name="fontFamily">@font/manrope_medium</item>
    </style>
</resources>
```

- [ ] **Step 2: Wire the overlay into the app theme**

In `ui/src/main/res/values/styles.xml`, find the `AppThemeBase` style and add inside it:
```xml
        <item name="preferenceTheme">@style/Awg.PreferenceThemeOverlay</item>
```

- [ ] **Step 3: Custom row layout**

Create `ui/src/main/res/layout/preference_awg.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:minHeight="48dp"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:background="?android:attr/selectableItemBackground"
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:paddingTop="13dp"
    android:paddingBottom="13dp">

    <ImageView
        android:id="@android:id/icon"
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:layout_marginEnd="14dp"
        android:tint="?attr/colorPrimary"
        android:visibility="gone"
        tools:ignore="UseAppTint" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">
        <TextView
            android:id="@android:id/title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textAppearance="@style/TextAppearance.Awg.PrefTitle" />
        <TextView
            android:id="@android:id/summary"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textAppearance="@style/TextAppearance.Awg.FieldLabel" />
    </LinearLayout>

    <FrameLayout
        android:id="@android:id/widget_frame"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:gravity="center_vertical"
        android:orientation="vertical" />
</LinearLayout>
```
Add `xmlns:tools="http://schemas.android.com/tools"` to the root element (needed for `tools:ignore`).

- [ ] **Step 4: Category header layout**

Create `ui/src/main/res/layout/preference_awg_category.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<TextView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@android:id/title"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:paddingStart="20dp"
    android:paddingEnd="20dp"
    android:paddingTop="20dp"
    android:paddingBottom="8dp"
    android:textAppearance="@style/TextAppearance.Awg.SectionHeader" />
```

- [ ] **Step 5: Teal switch widget**

Create `ui/src/main/res/layout/preference_widget_switch.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.materialswitch.MaterialSwitch
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@android:id/switch_widget"
    style="@style/AmneziaWgTheme.Switch"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:background="@null"
    android:clickable="false"
    android:focusable="false" />
```

- [ ] **Step 6: Icons**

Create `ui/src/main/res/drawable/ic_export.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorPrimary">
    <path android:fillColor="@android:color/white"
        android:pathData="M12,3L12,14M12,14L8.5,10.5M12,14L15.5,10.5"
        android:strokeColor="@android:color/white" android:strokeWidth="2"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
    <path android:fillColor="@android:color/white"
        android:pathData="M5,15L5,18A2,2 0 0,0 7,20L17,20A2,2 0 0,0 19,18L19,15"
        android:strokeColor="@android:color/white" android:strokeWidth="2"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
</vector>
```
Create `ui/src/main/res/drawable/ic_log.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorPrimary">
    <path android:fillColor="@android:color/white"
        android:pathData="M6,3L14,3L19,8L19,21L6,21Z"
        android:strokeColor="@android:color/white" android:strokeWidth="2"
        android:strokeLineCap="round" android:strokeLineJoin="round" />
    <path android:fillColor="@android:color/white"
        android:pathData="M9,12L15,12M9,16L15,16"
        android:strokeColor="@android:color/white" android:strokeWidth="2"
        android:strokeLineCap="round" />
</vector>
```
(`fillColor` is required by AAPT for paths; the `?attr/colorPrimary` tint overrides at render, matching the existing stroke icons in the pack.)

- [ ] **Step 7: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add ui/src/main/res/values/styles-awg-preferences.xml ui/src/main/res/values/styles.xml ui/src/main/res/layout/preference_awg.xml ui/src/main/res/layout/preference_awg_category.xml ui/src/main/res/layout/preference_widget_switch.xml ui/src/main/res/drawable/ic_export.xml ui/src/main/res/drawable/ic_log.xml
git commit -m "$(cat <<'MSG'
refresh(settings): preference theme, row/category/switch layouts, icons

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
MSG
)"
```

---

### Task 4: Regroup preferences, switch conversion, icons, rebrand string

**Files:**
- Modify: `ui/src/main/res/xml/preferences.xml` (full rewrite — content below)
- Modify: `ui/src/main/java/org/amnezia/awg/activity/SettingsActivity.kt` (drop `initialExpandedChildrenCount`)
- Modify: `ui/src/main/res/values/strings.xml:278` (+ new `version_pill`)

**Interfaces:**
- Produces: the 10 prefs grouped into 4 `PreferenceCategory` blocks; `version` uses the default row here (Task 5 swaps it to the About layout). Switches replace the 4 checkboxes (same keys/defaults). New strings `settings_cat_*` + rebranded `version_title`. This task compiles and commits standalone.

- [ ] **Step 1: Rewrite preferences.xml with categories + switches + icons**

Replace the entire contents of `ui/src/main/res/xml/preferences.xml` with:
```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.preference.PreferenceScreen xmlns:android="http://schemas.android.com/apk/res/android"
    android:key="settings">

    <PreferenceCategory android:title="@string/settings_cat_about">
        <org.amnezia.awg.preference.VersionPreference android:key="version" />
    </PreferenceCategory>

    <PreferenceCategory android:title="@string/settings_cat_general">
        <SwitchPreferenceCompat
            android:defaultValue="false"
            android:key="restore_on_boot"
            android:singleLineTitle="false"
            android:summaryOff="@string/restore_on_boot_summary_off"
            android:summaryOn="@string/restore_on_boot_summary_on"
            android:title="@string/restore_on_boot_title" />
        <SwitchPreferenceCompat
            android:defaultValue="false"
            android:key="dark_theme"
            android:singleLineTitle="false"
            android:summaryOff="@string/dark_theme_summary_off"
            android:summaryOn="@string/dark_theme_summary_on"
            android:title="@string/dark_theme_title" />
        <SwitchPreferenceCompat
            android:defaultValue="false"
            android:key="multiple_tunnels"
            android:singleLineTitle="false"
            android:summaryOff="@string/multiple_tunnels_summary_off"
            android:summaryOn="@string/multiple_tunnels_summary_on"
            android:title="@string/multiple_tunnels_title" />
        <SwitchPreferenceCompat
            android:defaultValue="false"
            android:key="allow_remote_control_intents"
            android:singleLineTitle="false"
            android:summaryOff="@string/allow_remote_control_intents_summary_off"
            android:summaryOn="@string/allow_remote_control_intents_summary_on"
            android:title="@string/allow_remote_control_intents_title" />
        <org.amnezia.awg.preference.QuickTilePreference android:key="quick_tile" />
    </PreferenceCategory>

    <PreferenceCategory android:title="@string/settings_cat_actions">
        <org.amnezia.awg.preference.ZipExporterPreference
            android:key="zip_exporter"
            android:icon="@drawable/ic_export" />
        <Preference
            android:key="log_viewer"
            android:icon="@drawable/ic_log"
            android:singleLineTitle="false"
            android:summary="@string/log_viewer_pref_summary"
            android:title="@string/log_viewer_pref_title" />
    </PreferenceCategory>

    <PreferenceCategory android:title="@string/settings_cat_advanced">
        <org.amnezia.awg.preference.ToolsInstallerPreference android:key="tools_installer" />
        <org.amnezia.awg.preference.KernelModuleEnablerPreference android:key="kernel_module_enabler" />
    </PreferenceCategory>
</androidx.preference.PreferenceScreen>
```

- [ ] **Step 2: Drop the `initialExpandedChildrenCount` collapse in SettingsActivity**

In `SettingsActivity.kt` `onCreatePreferences`, delete these four lines (grouping replaces the flat "show more" collapse; the conditional visibility/removal logic stays and still works because `findPreference` is recursive and `pref.parent` is now the category):
- `preferenceScreen.initialExpandedChildrenCount = 5` (was line 51)
- `--preferenceScreen.initialExpandedChildrenCount` (was line 56, in the quick_tile block)
- `--preferenceScreen.initialExpandedChildrenCount` (was line 61, in the dark_theme block)
- `++preferenceScreen.initialExpandedChildrenCount` (was line 75, in the awgQuick block)

Leave every `removePreference` / `isVisible` line and the rest of the method intact.

- [ ] **Step 3: Category-title strings + rebrand + pill string**

In `ui/src/main/res/values/strings.xml`, change line 278 from:
```xml
    <string name="version_title">AmneziaWG for Android v%s</string>
```
to:
```xml
    <string name="version_title">AmneziaWG Proxy v%s</string>
    <string name="settings_cat_about">About</string>
    <string name="settings_cat_general">General</string>
    <string name="settings_cat_actions">Actions</string>
    <string name="settings_cat_advanced">Advanced</string>
```
(`version_pill` is added in Task 5, alongside its only consumer.)

- [ ] **Step 4: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`. (Standalone — the `version` row uses the default layout until Task 5.)

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/res/xml/preferences.xml ui/src/main/java/org/amnezia/awg/activity/SettingsActivity.kt ui/src/main/res/values/strings.xml
git commit -m "$(cat <<'MSG'
refresh(settings): group into categories, switches, icons, Proxy rebrand

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
MSG
)"
```

---

### Task 5: About row (Signal mark + version pill)

**Files:**
- Create: `ui/src/main/res/layout/preference_awg_about.xml`
- Create: `ui/src/main/res/drawable/bg_about_tile.xml`, `ui/src/main/res/drawable/bg_version_pill.xml`
- Modify: `ui/src/main/res/xml/preferences.xml` (point the `version` pref at the About layout)
- Modify: `ui/src/main/res/values/strings.xml` (add `version_pill`)
- Modify: `ui/src/main/java/org/amnezia/awg/preference/VersionPreference.kt`

**Interfaces:**
- Consumes: `@drawable/ic_logo_empty_state` (exists), the `version` pref + `settings_cat_*` from Task 4.
- Produces: the `version` preference renders the About card; pill `@id/version_pill` text = `BuildConfig.VERSION_NAME`.

- [ ] **Step 1: About row layout**

Create `ui/src/main/res/layout/preference_awg_about.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:background="?android:attr/selectableItemBackground"
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:paddingTop="14dp"
    android:paddingBottom="14dp">

    <FrameLayout
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:layout_marginEnd="14dp"
        android:background="@drawable/bg_about_tile">
        <ImageView
            android:layout_width="30dp"
            android:layout_height="30dp"
            android:layout_gravity="center"
            android:src="@drawable/ic_logo_empty_state"
            android:contentDescription="@null" />
    </FrameLayout>

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">
        <TextView
            android:id="@android:id/title"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textAppearance="@style/TextAppearance.Awg.TunnelName" />
        <TextView
            android:id="@android:id/summary"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:textAppearance="@style/TextAppearance.Awg.FieldLabel" />
    </LinearLayout>

    <TextView
        android:id="@+id/version_pill"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:background="@drawable/bg_version_pill"
        android:paddingStart="10dp"
        android:paddingEnd="10dp"
        android:paddingTop="3dp"
        android:paddingBottom="3dp"
        android:textAppearance="@style/TextAppearance.Awg.StatLabel"
        android:textColor="?attr/colorOnPrimaryContainer" />
</LinearLayout>
```
Then create `ui/src/main/res/drawable/bg_about_tile.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="?attr/colorSurface" />
    <corners android:radius="14dp" />
</shape>
```
and `ui/src/main/res/drawable/bg_version_pill.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="?attr/colorPrimaryContainer" />
    <corners android:radius="999dp" />
</shape>
```

- [ ] **Step 2: Point the version pref at the About layout + add the pill string**

In `ui/src/main/res/xml/preferences.xml`, change the version preference (in the About category) from:
```xml
        <org.amnezia.awg.preference.VersionPreference android:key="version" />
```
to:
```xml
        <org.amnezia.awg.preference.VersionPreference
            android:key="version"
            android:layout="@layout/preference_awg_about" />
```
In `ui/src/main/res/values/strings.xml`, add next to `version_title`:
```xml
    <string name="version_pill" translatable="false">v%s</string>
```

- [ ] **Step 3: Bind the pill in VersionPreference**

In `ui/src/main/java/org/amnezia/awg/preference/VersionPreference.kt`, add these imports near the others:
```kotlin
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
```
Then add this override inside the class (e.g. after `getTitle()`):
```kotlin
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.version_pill) as? TextView)?.text =
            context.getString(R.string.version_pill, BuildConfig.VERSION_NAME)
    }
```

- [ ] **Step 4: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/res/layout/preference_awg_about.xml ui/src/main/res/drawable/bg_about_tile.xml ui/src/main/res/drawable/bg_version_pill.xml ui/src/main/res/xml/preferences.xml ui/src/main/res/values/strings.xml ui/src/main/java/org/amnezia/awg/preference/VersionPreference.kt
git commit -m "$(cat <<'MSG'
refresh(settings): Signal-mark About row with version pill

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
MSG
)"
```

---

### Task 6: Grouped rounded-card ItemDecoration

**Files:**
- Create: `ui/src/main/java/org/amnezia/awg/preference/PreferenceGroupCardDecoration.kt`
- Modify: `ui/src/main/java/org/amnezia/awg/activity/SettingsActivity.kt` (install decoration + remove default dividers + side padding)

**Interfaces:**
- Consumes: the `PreferenceCategory` grouping from Task 4; theme attrs `colorSurfaceContainer`/`colorOutlineVariant`.
- Produces: per-category rounded `surfaceContainer` cards with inner dividers, drawn behind the rows.

- [ ] **Step 1: The decoration**

Create `ui/src/main/java/org/amnezia/awg/preference/PreferenceGroupCardDecoration.kt`:
```kotlin
/*
 * Copyright © 2017-2023 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.amnezia.awg.preference

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

/**
 * Draws each contiguous run of rows inside a PreferenceCategory as a single
 * rounded surfaceContainer card (top corners on the first row, bottom on the
 * last) with a 1dp outlineVariant stroke and inner dividers. Category headers
 * are skipped. The card is drawn behind transparent rows.
 */
class PreferenceGroupCardDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private val density = context.resources.displayMetrics.density
    private val radius = 16f * density
    private val stroke = 1f * density
    private val sidePad = (16f * density).toInt()
    private val groupGap = (12f * density).toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainer, 0)
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutlineVariant, 0)
    }

    private fun adapter(parent: RecyclerView) = parent.adapter as? PreferenceGroupAdapter
    private fun itemAt(parent: RecyclerView, pos: Int): Preference? {
        val a = adapter(parent) ?: return null
        if (pos < 0 || pos >= a.itemCount) return null
        return a.getItem(pos)
    }
    private fun isRow(p: Preference?) = p != null && p !is PreferenceCategory

    override fun getItemOffsets(outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        // Add the inter-group gap above the first row of each card.
        if (isRow(itemAt(parent, pos)) && !isRow(itemAt(parent, pos - 1))) {
            outRect.top = groupGap
        }
    }

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val left = (parent.paddingLeft + sidePad).toFloat()
        val right = (parent.width - parent.paddingRight - sidePad).toFloat()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val pos = parent.getChildAdapterPosition(child)
            val pref = itemAt(parent, pos)
            if (!isRow(pref)) continue
            val first = !isRow(itemAt(parent, pos - 1))
            val last = !isRow(itemAt(parent, pos + 1))
            val top = child.top.toFloat()
            val bottom = child.bottom.toFloat()
            val rect = RectF(left, top, right, bottom)
            val rTop = if (first) radius else 0f
            val rBot = if (last) radius else 0f
            val path = Path().apply {
                addRoundRect(
                    rect,
                    floatArrayOf(rTop, rTop, rTop, rTop, rBot, rBot, rBot, rBot),
                    Path.Direction.CW
                )
            }
            c.drawPath(path, fill)
            // Inner divider under non-last rows.
            if (!last) c.drawLine(left + radius, bottom, right - radius, bottom, line)
            // Outer stroke.
            val half = stroke / 2f
            val strokeRect = RectF(left + half, top + half, right - half, bottom - half)
            val rTopS = if (first) radius else 0f
            val rBotS = if (last) radius else 0f
            val strokePath = Path().apply {
                addRoundRect(
                    strokeRect,
                    floatArrayOf(rTopS, rTopS, rTopS, rTopS, rBotS, rBotS, rBotS, rBotS),
                    Path.Direction.CW
                )
            }
            c.drawPath(strokePath, line)
        }
    }
}
```

- [ ] **Step 2: Install it + remove default dividers + side padding**

In `SettingsActivity.kt`, inside `class SettingsFragment`, add this override:
```kotlin
        override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setDivider(null)
            setDividerHeight(0)
            listView.clipToPadding = false
            listView.setPaddingRelative(
                listView.paddingStart, listView.paddingTop,
                listView.paddingEnd, (16 * resources.displayMetrics.density).toInt()
            )
            listView.addItemDecoration(
                org.amnezia.awg.preference.PreferenceGroupCardDecoration(requireContext())
            )
        }
```

- [ ] **Step 3: Build**

Run: `./gradlew :ui:processDebugResources :ui:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add ui/src/main/java/org/amnezia/awg/preference/PreferenceGroupCardDecoration.kt ui/src/main/java/org/amnezia/awg/activity/SettingsActivity.kt
git commit -m "$(cat <<'MSG'
refresh(settings): grouped rounded-card decoration

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
MSG
)"
```

---

## Final verification (on-device — manual)

1. **Settings chrome** — flat surface toolbar, Manrope "Settings" title, back arrow returns.
2. **About card** — Signal mark in the surface tile, "AmneziaWG Proxy v2.1.0" title, backend line, and a `v2.1.0` pill; tapping still opens the website.
3. **Grouped cards** — About / General / Actions / Advanced render as rounded `surfaceContainer` cards with teal caps headers, inner dividers, light + dark.
4. **Switches** — the four toggles are teal switches; toggling persists (kill + reopen).
5. **Actions** — `ic_export` on Export, `ic_log` on Log; export writes a zip; log opens `LogViewerActivity` and its share works under the new `org.amnezia.awg.proxy` provider authority.
6. **Conditional rows** — quick_tile / dark_theme / zip_exporter / tools_installer / restore_on_boot / multiple_tunnels / kernel_module appear or hide exactly as before (no regression from the category move).
7. **Fork identity** — debug build installs as `org.amnezia.awg.proxy`, coexisting with any official install.

## Self-review notes
- **Spec coverage:** A1 toolbar→T2; A2 theme/row layout→T3; A3 grouping+decoration→T4+T6; A4 About row→T5; A5 switches+icons→T3+T4; version_title rebrand→T4; Part B→T1. A6 footer is out-of-scope per spec.
- **Sequencing:** every task compiles + commits standalone. T4 leaves the `version` row on the default layout; T5 swaps it to the About layout and adds `version_pill` (its only consumer), so no forward reference breaks a per-task gate.
- **No behavior change:** keys/defaults/summaries preserved in the T1 preferences rewrite; only `version_title` text changes (T4); `initialExpandedChildrenCount` removal is the one deliberate UX change (grouping replaces the flat collapse), called out in T4 Step 2.
- **Type consistency:** `PreferenceGroupCardDecoration(context)` constructor used identically in T6 Step 1/2; pill id `@id/version_pill` defined in T5 layout and read in T5 VersionPreference + (no other readers).
