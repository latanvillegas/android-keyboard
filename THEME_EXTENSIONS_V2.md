# FUTO Keyboard fork — Theme Extensions V2

**Goal:** Extend the official TOML/ZIP theme format without changing its V1
fields, breaking the official theme editor, or modifying typing behavior.
The existing `theme.txt`, `[colors]`, `[[matchrules.border]]`,
`[[matchrules.icon]]`, `[asset]`, and `[options]` remain supported.

## Optional TOML fields

A V1 theme does not need to declare ANY of these fields.

```toml
[options]
suggestion_text_color = "#F7FBFFFF"
suggestion_bar_color = "#121A3DDA"
suggestion_font_scale = 1.1
action_icon_color = "#F4F8FFFF"
action_button_background_color = "#24436FCC"
action_button_foreground_color = "#FAFDFFFF"
expanded_actions_bar_color = "#142249EE"

# Existing V1-fork field; still works if no V2 entry is provided.
mic_icon = "icons/mic.png"
mic_icon_scale = 1.4

[[options.action_icons]]
name = "mic_fill"
asset = "icons/mic.png"
scale = 1.4
tint = false

[[options.action_icons]]
name = "globe"
asset = "icons/globe.webp"
scale = 1.1
tint = false

[[options.action_icons]]
name = "undo"
asset = "icons/undo.png"
scale = 1.0
tint = true
```

**Identifier convention:** `name` is the exact entry name of the Android
drawable resource used by the action. Examples: `mic_fill`, `undo`,
`redo`, `copy`, `clipboard`, `settings`, `globe`, `themes`.
Missing names do not affect other actions. The ActionBar resolves these
at runtime by Android drawable entry name, rather than numeric resource ID.

- `scale` is bounded to 0.2–5x and additionally bounded by the action bar.
- `tint = false` preserves the original transparency and asset colors.
- `tint = true` allows `action_icon_color` or the action's normal tint.
- At most 64 icon replacements are loaded from a ZIP.
- If loading fails, the original built-in icon remains visible.
- Legacy `mic_icon` is still the microphone fallback.
- Icon images are decoded once per theme scheme, not repeatedly per frame.
- No changes to the keyboard's geometry or configured typing languages.

## Optional JSON fields for the in-app editor

```json
{
  "actionIcons": {
    "mic_fill": "action-icons/mic_fill.png",
    "undo": "action-icons/undo.png"
  },
  "actionIconScales": {
    "mic_fill": 1.4,
    "undo": 1.0
  },
  "actionIconTints": {
    "mic_fill": false,
    "undo": true
  },
  "actionIconColor": "#F4F8FFFF",
  "actionButtonBackgroundColor": "#24436FCC",
  "actionButtonForegroundColor": "#FAFDFFFF",
  "expandedActionsBarColor": "#142249EE"
}
```

The JSON fragment above contains only the extension fields. A complete JSON
theme still needs the mandatory V1 palette fields.

## In-app visual editor

Under the in-app theme editor the user can:

1. Pick an action (microphone, undo, redo, copy, clipboard, settings,
   languages, themes, close).
2. Choose a transparent PNG or WebP image; it is normalized to at most 256px
   and 2 MiB. The editor stores it within the theme ZIP.
3. Adjust the selected action icon's scale, restore the original, and preview
   the expanded action strip.
4. Customize general action icon color, action button background and
   foreground, and the expanded strip background.

These are optional settings. The default theme appearance is unchanged.
The web-based FUTO theme editor may drop extension fields when exporting;
keep the original theme ZIP or TOML.

## Import safety and compatibility

ZIPs are validated before installation:

- 48 MiB compressed maximum, 160 MiB total uncompressed maximum;
- 512 entries, 16 MiB per asset, 2 MiB per config maximum;
- no duplicate entries, absolute paths, `..` traversal or backslashes;
- valid ZIP and theme config required;
- archive is atomically replaced only after successful validation.

Official V1 themes continue to load with unchanged colors and icon symbols.
A malformed V2 asset cannot prevent built-in icon rendering. Imported theme
images are independent of the separately configured Microsoft Fluent 3D emoji
artwork.

## Translation and release checks

The Settings UI exposes System / Spanish / English independently of the
typing language. Android 13+ uses the platform's per-app locale API; on
Android 12 and earlier, the override is intentionally limited to settings.
The `spanish-audit.json` file in Release artifacts enumerates untranslated
strings and formatting-placeholder mismatches.

The workflow compiles `Stable Release` and runs JVM security regression
tests. It does not produce a Debug APK. The build remains test-signed until
the owner's production keystore is configured.

## Suggestion candidate backgrounds (V2.1, optional)

Each of the three dynamic suggestion candidates can now use the same 9-slice
PNG borders already supported for normal keyboard keys. No image is baked
with a suggestion word. Existing themes and prediction behavior are unchanged.

Add these optional fields inside the existing `[options]` table:

```toml
[options]
suggestion_item_border = "key_normal_3d.png"
suggestion_item_pressed_border = "key_normal_pressed.png"
suggestion_item_background_color = "#121924FF"
suggestion_item_pressed_background_color = "#0B2639FF"
suggestion_item_radius = 12.0
suggestion_item_inset_horizontal = 3.0
suggestion_item_inset_vertical = 2.0
suggestion_item_hide_dividers = true
```

The files specified by `suggestion_item_border` and
`suggestion_item_pressed_border` must be present in the theme ZIP and may
reuse existing `[[asset.border]]` entries (with their `slicing`, `padding`,
`gap`, and `target_density` values). If only a normal border is supplied,
it remains visible while pressed. If either PNG fails to decode, the respective
color fallback (if provided) remains usable. Transparent key PNGs are rendered
over the configured color fill. Touch targets are not reduced by the visual
insets. Values for radius and insets are clamped to safe ranges.

For the native JSON format, use corresponding camelCase keys:
`suggestionItemBorder`, `suggestionItemPressedBorder`,
`suggestionItemBackgroundColor`, `suggestionItemPressedBackgroundColor`,
`suggestionItemRadius`, `suggestionItemInsetHorizontal`,
`suggestionItemInsetVertical`, and `suggestionItemHideDividers`.
JSON border PNGs use the same one-pixel Android nine-patch marker format as
the existing JSON `keyBackgrounds`.

The V2.1 options only affect visual presentation of suggestions. The
suggestions are still selected and predicted by the normal keyboard engine.

## Unified suggestion glass and action-button skins (V2.2, optional)

The continuous suggestion strip is rendered as one 9-slice surface behind all
candidate words. Its width follows the actual keyboard layout in both portrait
and landscape. Each suggestion retains its own hitbox, long-press action and
prediction semantics. When enabled, individual candidate separators are hidden.
The image no longer needs to be baked into the keyboard wallpaper.

Add these optional fields within the existing `[options]` table:

```toml
[options]
suggestion_bar_border = "suggestions/aurora_unified_glass.png"
suggestion_bar_inset_horizontal = 2.0
suggestion_bar_inset_vertical = 2.0

# Fallback skin for the small round action buttons.
action_button_border = "buttons/aurora_orb.png"
action_button_pressed_border = "buttons/aurora_orb_pressed.png"

# Optional overrides for the left expander and microphone.
expand_button_border = "buttons/aurora_orb_expand.png"
expand_button_pressed_border = "buttons/aurora_orb_expand_pressed.png"
mic_button_border = "buttons/aurora_orb_mic.png"
mic_button_pressed_border = "buttons/aurora_orb_mic_pressed.png"
```

All eight image settings are optional; where omitted, the existing round
background color is rendered. `expand_button_*` and `mic_button_*` override
the generic `action_button_*` skins when provided. Images use the same
`[[asset.border]]` metadata (`slicing`, `padding`, `target_density`) as
keycaps; the action buttons retain circular bounds and full touch targets.
The two states reflect real pointer press events, not the action-bar expansion
state. Icon replacements and tint settings continue to work independently.

In the JSON format, the corresponding optional keys are:
`suggestionBarBorder`, `suggestionBarInsetHorizontal`,
`suggestionBarInsetVertical`, `actionButtonBorder`,
`actionButtonPressedBorder`, `expandButtonBorder`,
`expandButtonPressedBorder`, `micButtonBorder` and
`micButtonPressedBorder`. JSON PNGs use their existing Android nine-patch
1-pixel marker convention. All TOML border assets use regular images and
their `[[asset.border]]` metadata.

The `SettingsActivity` already observes `THEME_KEY` and applies
`UixThemeAuto`, so no additional global settings-theme override is needed.
This extension only changes keyboard chrome; existing settings colors and
theme previews continue to follow the selected theme.

