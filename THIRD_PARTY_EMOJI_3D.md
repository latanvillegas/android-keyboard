# Fluent Emoji 3D assets — third-party notice

The emoji 3D artwork used in the Release APK is the original,
transparent, full-color **Microsoft Fluent Emoji 3D** artwork,
distributed under the MIT License.

Original: https://github.com/microsoft/fluentui-emoji

Unicode-organized mirror: https://github.com/shuding/fluentui-emoji-unicode

Pinned mirror commit: `7a40f1a2d064d76e436813edc0f09b6c8cde5da8`

The GitHub Actions release pipeline downloads the pinned source archive and
converts the PNGs to transparent 128px WebP images. The build embeds all
2,980 codepoint-named 3D assets and the verbatim MIT license under
`assets/emoji3d/`. **No runtime network connection** is used.

The images are pre-rendered 3D artwork. They are not interactive GLB meshes.
Their original colors are preserved, without theme tinting, background
discs or simulated per-cell 3D cards.

The selected Unicode sequence remains the fallback when the receiving editor
does not support PNG insertion, the chosen asset is missing, the 3D setting is
disabled, or the editor rejects the content request. Compatible editors can
receive the same Fluent artwork as an image/png attachment through the private
content provider. This does not change or replace the original Fluent artwork.
For ASCII emoticons/kaomoji, the panel may show a semantically corresponding
3D Fluent face; the original ASCII string is retained for Unicode fallback.
The receiving app controls whether an inserted image is displayed inline or
as an attachment.

MIT copyright notice: Copyright (c) Microsoft Corporation.

The full license text is included in the packaged APK at
`assets/emoji3d/LICENSE.txt`.


## Authentic Fluent 3D animations (optional motion catalog)

A separate curated set of 30 emoji includes **original animated frame sequences**
from `microsoft/fluentui-emoji-animated`, which is MIT-licensed by Microsoft.

- Upstream: https://github.com/microsoft/fluentui-emoji-animated
- Immutable commit: `daa0365c09795789ed2bc6e8b228c97736cb6669`
- Frame source: 256px animated PNG (APNG) in Git LFS.
- Shipping format: transparent looping GIF, 128px and at most 48 retained
  original frames; source duration is preserved when sampling.
- The Release build uses selective Git LFS fetch to retrieve only this
  selection. No runtime download occurs.
- The complete Microsoft MIT notice is packaged as
  `assets/emoji3d-motion/LICENSE.txt`; `SOURCE.json` records source paths
  and SHA-256 values.

Not every Fluent emoji has an animation in the APK. Unsupported or missing
motion content is never replaced by a mismatched emoji or a fake motion effect;
the existing static 3D illustration and Unicode fallback remain available.
