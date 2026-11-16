# Microsoft Fluent Emoji 3D — third-party notice

This project uses original Microsoft Fluent Emoji 3D artwork under the MIT
License. The app itself remains functional with native Unicode emojis even
when no Fluent 3D media is installed.

- Primary artwork: https://github.com/microsoft/fluentui-emoji
- Unicode-organized source: https://github.com/shuding/fluentui-emoji-unicode
- Pinned static source: 7a40f1a2d064d76e436813edc0f09b6c8cde5da8
- Original animations: https://github.com/microsoft/fluentui-emoji-animated
- Pinned animation source: daa0365c09795789ed2bc6e8b228c97736cb6669
- Copyright (c) Microsoft Corporation; license: MIT.

## Runtime packaging

The Core APK does NOT include Microsoft Fluent bitmap or GIF catalogs.
The optional external offline file fluent3d-full-offline-collection.zip
contains all four original media catalogs:

1. 2,980 128px transparent WebP illustrations for the emoji picker.
2. 2,980 256px lossless RGBA WebP masters for PNG sharing.
3. 30 original Animated WebP 256px lossless RGBA sequences.
4. 30 GIF 256px compatible animated sequences.

Only the original Microsoft 3D artwork is included; no interactive 3D
meshes or invented frame motion. The per-source LICENSE.txt and SOURCE.json
files are included in the external ZIP, with SHA-256 inventory verification.
There is no automatic network access in the installed keyboard.

The source animation is imported from original Microsoft APNG using pinned
Git LFS paths and preserves overall motion duration. WebP retains full
per-pixel RGBA lossless color; GIF itself cannot reproduce 24-bit RGB+alpha
fidelity and is offered as a compatibility option, not a quality-equivalent
substitute.

The receiving app controls whether inserted files remain animated, are
recompressed, or require the user to confirm sending. The keyboard uses
Android commitContent for supported MIME types and exact Unicode fallback
when content cannot be sent. This does not alter the original artwork.
