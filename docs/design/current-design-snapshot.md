# AmneziaWG Android — Current Design Snapshot

> Paste this whole file into claude.ai (web) alongside **screenshots** of the running app.
> It tells Claude exactly what the app is built on and what the current design tokens are,
> so any proposal it returns is implementable without guessing.

## Tech & constraints (read first)

- **Platform:** Native Android, **XML layouts + data/view binding**. **NOT Jetpack Compose.**
- **Design system:** Material 3 (`Theme.Material3.Light` / `.Dark`), Material Components **1.11.0**.
- **SDK:** `minSdk 24`, `targetSdk 35`, `compileSdk 35`.
- **Surfaces:** phone (portrait), tablet (`sw600dp` two-pane), **Android TV** (d-pad focus — every view has `nextFocus*`).
- **Themes:** full light + dark (`values/` + `values-night/`). Every color needs both.
- **Localization:** 20+ locales via `strings.xml`.

## Screen inventory (these are fixed — do not invent new ones)

| Screen | Layout file | Notes |
|---|---|---|
| Tunnel list (home) | `tunnel_list_fragment.xml` + `tunnel_list_item.xml` | RecyclerView of rows, per-row toggle, FAB to add, empty state |
| Tunnel detail | `tunnel_detail_fragment.xml` + `tunnel_detail_peer.xml` | Scroll of label/value rows in a card; very dense (AWG fields) |
| Tunnel editor | `tunnel_editor_fragment.xml` + `tunnel_editor_peer.xml` | Text inputs mirroring the detail fields |
| Add-tunnel sheet | `add_tunnels_bottom_sheet.xml` | Bottom sheet: import / scan QR / create |
| App shell | `main_activity.xml` (+ `layout-sw600dp/`) | Single FragmentContainer |
| Log viewer | `log_viewer_activity.xml` + `log_viewer_entry.xml` | Monospace log list |

## Color tokens — LIGHT (`values/colors.xml`)

Material 3 roles. **`primary` is near-black, not the blue seed** — a deliberate fork choice.

```
seed                      #1a73e8   (origin seed; NOT used as primary)
primary                   #15181B   onPrimary            #FFFFFF
primaryContainer          #D8E2FF   onPrimaryContainer   #001A41
secondary                 #565E71   onSecondary          #FFFFFF
secondaryContainer        #DBE2F9   onSecondaryContainer #131B2C
tertiary                  #715574   onTertiary           #FFFFFF
tertiaryContainer         #FBD7FC   onTertiaryContainer  #29132D
error                     #BA1A1A   errorContainer       #FFDAD6
background / surface      #FEFBFF   onBackground/Surface #1B1B1F
surfaceVariant            #E1E2EC   onSurfaceVariant     #44474F
outline                   #74777F   outlineVariant       #C4C6D0
```

## Color tokens — DARK (`values-night/colors.xml`)

```
primary                   #15181B   onPrimary            #002E68
primaryContainer          #004493   onPrimaryContainer   #D8E2FF
secondary                 #BFC6DC   secondaryContainer   #3F4759
tertiary                  #DEBCDF   tertiaryContainer    #583E5B
error                     #FFB4AB   errorContainer       #93000A
background / surface      #1B1B1F   onBackground/Surface #E3E2E6
surfaceVariant            #44474F   onSurfaceVariant     #C4C6D0
outline                   #8E9099
```

## Status colors (semantic, both themes)

```
                     light       dark
connected            #4CAF50     #66BB6A
connecting           #FF9800     #FFB74D
disconnected         #F44336     #EF5350
```

## Shape / spacing / type (`values/styles.xml` + layouts)

- **Card:** `Widget.Material3.CardView.Elevated`, `cornerRadius 4dp`, `contentPadding 8dp`.
- **Toolbar:** `Widget.Material3.Toolbar`, background `?attr/colorSurface`.
- **Bottom sheet:** `ThemeOverlay.Material3.BottomSheetDialog`, non-floating, transparent nav bar.
- **List row:** `paddingHorizontal 16dp`, `paddingVertical 8dp`; name = `textAppearanceBodyLarge`, status = `textAppearanceBodySmall`.
- **Detail card:** margins `8dp` sides / `16dp` top; field rows stacked with `marginTop 8dp`; labels default body, values `textAppearanceBodyLarge`.
- **FAB:** standard `FloatingActionButton`, `srcCompat=ic_action_add_white`, bottom|end.
- **Empty state:** app icon at `alpha 0.333`, 140dp, + 20sp placeholder text.
- **Type:** Material 3 type scale via `?attr/textAppearance*` (no custom font).
- **Spacing:** ad-hoc 4dp grid (`8dp` / `16dp` most common).

## Key theme/style source (verbatim)

`values/themes.xml` maps every Material role to the `md_theme_light_*` colors above (parent `Theme.Material3.Light`); `values-night/themes.xml` does the same with `md_theme_dark_*` (parent `Theme.Material3.Dark`).

`values/styles.xml`:
```xml
<style name="AppThemeBase" parent="AmneziaWgTheme">
    <item name="materialCardViewStyle">@style/AmneziaWgTheme.MaterialCardView</item>
    <item name="toolbarStyle">@style/AmneziaWgTheme.Toolbar</item>
    <item name="bottomSheetDialogTheme">@style/AmneziaWgTheme.BottomSheetDialog</item>
    <item name="android:statusBarColor">@null</item>
</style>
<style name="AmneziaWgTheme.MaterialCardView" parent="Widget.Material3.CardView.Elevated">
    <item name="cornerRadius">4dp</item>
    <item name="contentPadding">8dp</item>
</style>
```
