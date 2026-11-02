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

The selected text and Unicode codepoints inserted into apps are unchanged.
For ASCII emoticons/kaomoji, the panel may show a semantically corresponding
3D Fluent face while still inserting the exact original emoticon string.
For codepoints without corresponding assets, Android's original Unicode
rendering is used.

MIT copyright notice: Copyright (c) Microsoft Corporation.

The full license text is included in the packaged APK at
`assets/emoji3d/LICENSE.txt`.
