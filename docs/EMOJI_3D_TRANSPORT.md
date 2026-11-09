# High-fidelity real Fluent Emoji 3D animation and adaptive transport

## User-visible behavior

With the full media pack installed, the keyboard displays original 128px Fluent previews. Without it, the picker displays Unicode fallback and no Microsoft media is bundled into the Core APK.
The receiving app, not the keyboard preview, determines how attached media
are presented and whether animations play.

Each tap is processed in order and offered to the currently focused Android
editor in this priority:

1. **Animated WebP** (`image/webp`) when advertised and bundled: genuine
   Microsoft Fluent APNG motion encoded losslessly at **up to 256 x 256 px**,
   **24-bit RGB + 8-bit alpha (RGBA)**.
2. **GIF** (`image/gif`) when Animated WebP is unavailable or rejected:
   genuine Fluent frame-by-frame motion, up to **256 x 256 px**. The format's
   inherent per-frame palette of at most 256 colors can reduce fidelity.
3. **Static PNG** (`image/png`): a lossless PNG generated from a separate
   **256 x 256 px lossless RGBA Fluent WebP** sharing master, for every emoji
   covered by the official static 3D catalog.
4. **Unicode/ASCII**: the original exact selected character or emoticon when
   rich content cannot be offered.

If the 3D relief setting is disabled, the keyboard directly inserts original
Unicode. If any graphic is missing, the cascade falls back automatically;
a missing skin tone or ZWJ variant is never silently substituted with a
different Unicode sequence. A mutex preserves tap order and an editor session
token blocks output into a newly focused input field.

Important: Android `commitContent() == true` confirms that the editor
accepted the MIME/URI **request**, not that the receiving chat delivered the
message. Some apps display media as an attachment requiring a send press,
flatten animated WebP to a static image, resize it, or recompress it. MIME
declaration alone cannot guarantee that motion or pixel fidelity survives.
True 3D interactive models require receiving-app support not supplied by the
Android emoji standard.

## Sources and reproducibility

The assets are built from pinned original sources on CI, published in a
separate installable ZIP and removed from the Core APK before Gradle packaging.
The installed keyboard imports this offline ZIP from the system file picker;
it never downloads media silently.

### Static 3D catalog

- Source: `shuding/fluentui-emoji-unicode` (original Microsoft Fluent 3D,
  MIT license), commit
  `7a40f1a2d064d76e436813edc0f09b6c8cde5da8`.
- `scripts/build_emoji3d_assets.py` creates **two independent catalogs**:
  `emoji3d/` contains 128px WebP previews and `emoji3d-share/` contains 256px lossless RGBA WebP. Both catalogs reside only in the optional offline ZIP.
- The high-resolution sharing decoder does not expand the 16 MiB UI
  scrolling-cache budget. Android decodes high-resolution only when needed.
- The separate ZIP includes indexes, source metadata and MIT licenses in both folders.

### Genuine animated 3D catalog

- Source: `microsoft/fluentui-emoji-animated`, MIT license, immutable
  commit `daa0365c09795789ed2bc6e8b228c97736cb6669`.
- 30 selected real motion sequences from the **original APNG frames** are
  obtained via selective Git LFS checkout, not the multi-gigabyte repository.
- `scripts/build_emoji3d_motion.py` decodes the composited original frames
  and preserves total animation duration. It can retain up to **96 source
  frames** in lossless 256px Animated WebP and up to **48 in GIF**.
- Each GIF is derived from the original RGBA sequence rather than re-encoding
  already palettized GIF frames.
- Both variants are indexed by the exact Unicode metadata supplied by
  Microsoft. `SOURCE.json` records counts, frame totals, byte sizes and
  upstream SHA-256 hashes.
- Animated WebP is not a different static emoji, a screen transform, or an
  interactive 3D mesh: it carries actual prerendered facial/gesture motion.

## Security and resource bounds

- The provider is `exported=false`, grants URI read permission to the
  focused editor, and validates the URI scheme, authority, token, suffix,
  media signature and read-only mode.
- Allowed MIME types: `image/webp`, `image/gif`, `image/png`.
- Files are created atomically in private cache using names derived from
  SHA-256 of the artwork ID, format, and cache version.
- Each shared file is limited to 16 MiB; cache eviction targets 64 MiB and
  preserves recently shared resources for at least 10 minutes. Older files
  expire after 24 hours.
- All decoding and file copies execute on `Dispatchers.IO`; no network
  dependencies are required during keyboard operation.

## Validation matrix (Android devices)

| Receiver reports | Expected attempt order |
| --- | --- |
| image/webp, image/gif, image/png | WebP -> GIF -> PNG -> Unicode |
| image/gif and image/png | GIF -> PNG -> Unicode |
| image/png only | PNG -> Unicode |
| image/webp only | WebP -> Unicode |
| no image MIME support | Unicode/ASCII |
| missing animation | High-resolution PNG if available |
| stale editor session | Do not write to the replacement editor |
| 3D disabled | Unicode/ASCII only |

Automation checks actual 256px output, transparent alpha, multiple distinct
frames and valid MIME containers for all 30 animations; JVM tests confirm MIME
priority and fallback. The Core APK verification rejects any embedded Fluent media. A separate ZIP is verified using every SHA-256 and the correct indexes; the final signed APK and collection are distinct build artifacts.

Before merging, test real outbound and received messages in WhatsApp,
Telegram, Signal and Google Messages: verify actual animation playback,
attachment sending flow, color detail and transparency, not merely that
the sender accepted the content URI. A provider cannot override a
third-party app's media recompression.
