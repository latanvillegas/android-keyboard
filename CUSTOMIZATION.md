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
