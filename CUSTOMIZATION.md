# Advanced FUTO Keyboard customization

Under Keyboard settings -> Resize, new controls provide persistent independent
suggestion bar height (24-200 dp), suggestion font size (8-144 sp), and action
icon scale (0.2-5x). Unset options preserve the original FUTO appearance.

The keyboard resizer uses physical viewport limits instead of fixed fractions
of display height and keeps at least one dp to avoid invalid geometry.

## Theme microphone icon

A theme ZIP may contain a PNG at `icons/microphone.png` and add to `theme.txt`:

```toml
[options]
mic_icon = "icons/microphone.png"
mic_icon_scale = 1.5
```

The renderer uses the PNG without color tint when assigned to a microphone
action. Existing themes that omit this setting keep the built-in icon.
Voice recognition code and microphone permissions are unchanged.
JSON theme equivalents: `actionMicIcon` and `actionMicIconScale`.
SVG input is not supported by the existing bitmap decoder; use PNG.

Validation required: native build, portrait/landscape, split/floating/one-handed,
long suggestions, ZIP import/export, rotation and IME/inset behavior.

## Suggested words bar: local sizing and theme appearance

The prediction font size is editable exclusively under **Keyboard settings
→ Resize**. It ranges from 8 to 144 sp and defaults to 18 sp. The setting is
not duplicated on the main keyboard settings screen. Changing it does not
resize key labels or keyboard geometry.

The built-in **Create custom theme** editor now has three new controls:
suggestion font scale (50–300% of the user's chosen size), suggestion text
color, and suggestion bar background. A reset button on each color picker
restores the underlying theme's own palette. The preview uses the real action
bar and the saved JSON theme ZIP persists the options.

Optional values for third-party TOML theme ZIPs:

```toml
[options]
suggestion_font_scale = 1.25
suggestion_text_color = "#FFFFFFFF"
suggestion_bar_color = "#101124FF"
suggestion_divider_color = "#00D8FFFF"
```

TOML hex colors use RGBA order, matching the existing theme parser. JSON
equivalents are `suggestionFontScale`, `suggestionTextColor`,
`suggestionBarColor` and `suggestionDividerColor`. Missing attributes
leave existing themes unchanged. Invalid non-finite scale values fall back to
1.0, and rendered prediction text size is clamped to 4–144 sp.

The public FUTO-hosted web theme editor is not part of this fork: these
controls are in the **in-app theme editor** and imported theme files.

## Clean suggestion strip and Release-only builds

The horizontal top and bottom action-bar separators are disabled globally,
regardless of active theme and including keyboard-resize previews. Vertical
suggestion-word dividers and action icons are unchanged.

The fork's GitHub Actions configuration produces only the optimized
`assembleStableRelease` APK; the former Debug APK workflow was removed.
Without a configured production keystore, the Release APK is test-signed
with the repository's development key (as documented in `RELEASE_BUILD.md`).

## Key glyph sizing, emoji cell sizing, and optional 3D relief

In **Keyboard → Resize**, key labels (letters, number row and secondary
number hints) scale independently by 50–220%, default 100%. Key geometry
and the split/floating settings are unchanged.

Emoji and ASCII emoticon cells in the picker can be sized from 28 to 72 dp,
default 42 dp; emoji suggestions also scale relative to the default. The
3D relief toggle is on by default and applies a shaded raised surface
plus soft glyph shadow in the emoji picker.

This is a **2.5D renderer effect**, not replacement 3D emoji art assets.
Unicode codepoints, skin tone behavior, and inserted text remain unchanged.

The GitHub Actions pipeline builds **Release only**, never Debug.

## Actual Microsoft Fluent 3D artwork (Release)

The previous painted per-emoji rounded-rectangle and fake glyph shadows have
been removed from the emoji grid. **Keyboard themes and theme backgrounds are
unchanged**. The same immutable, full-color transparent Microsoft Fluent Emoji
3D artwork is now shown in the emoji picker, search results, skin-tone
variants and emoji-only predictions. The original Unicode or ASCII text is
always inserted; only its preview image changes.

The build packages 2,980 Microsoft Fluent Emoji 3D sprites, resized to
128-pixel WebP with alpha and without tinting. The source archive is pinned to
`shuding/fluentui-emoji-unicode@7a40f1a2d064d76e436813edc0f09b6c8cde5da8`,
which reorganizes Microsoft's MIT-licensed Fluent Emoji by Unicode.

The default-on switch formerly called "3D relief" now enables the **real
prerendered 3D artwork**. Disabling it returns to system Unicode rendering
without artificial circles. Some unsupported emojis fall back gracefully.
ASCII/kaomoji text is optionally represented by semantically corresponding
3D face artwork; the typed ASCII text remains unmodified. This is
prerendered 3D artwork, not interactive 3D mesh rendering.

No runtime network requests are made for emoji art. Release artifacts include
the source provenance and the full MIT license. See
`THIRD_PARTY_EMOJI_3D.md` and
`scripts/build_emoji3d_assets.py`.
