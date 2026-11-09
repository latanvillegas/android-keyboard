# Real animated Fluent Emoji 3D: adaptive Android transport

## Behavior and compatibility

When the 3D relief setting is enabled, the keyboard continues to display the
existing Microsoft Fluent prerendered 3D illustrations. Sending a selected
emoji now follows a **single ordered** path:

1. Capture the base editor session (InputConnection and EditorInfo identity).
2. If the editor declares `image/gif`, look up the selected Unicode sequence
   in the packaged motion index (`assets/emoji3d-motion/index.txt`).
3. If a genuine Fluent animation exists, stage the original animation as an
   offline GIF in a private cache, grant the editor a temporary read-only URI
   and call `InputConnectionCompat.commitContent` with `image/gif`.
4. If no animation exists, the GIF was rejected, or GIF isn't supported, check
   `image/png` and stage the same 3D illustration used by the picker.
5. If neither option can be committed, insert the **original** Unicode
   sequence exactly once; preserve whitespace handling for ASCII emoticons.
6. If the editor session changes during asynchronous preparation, insert
   nothing into the newly focused field.

The app may present GIF/PNG content as an attachment, sticker preview, or
media item rather than an inline text character. `commitContent=true` means
the editor accepted the request; it is **not** a delivery receipt. The
receiving app may still require the user to confirm sending media.

A receiving chat advertising `image/gif` does not guarantee that the other
party's client will play animated media automatically; this needs empirical
per-app testing.

## What "real movement" means

The 30 initially bundled animated variants use **original Microsoft APNG
animation frames**, not a bounce, wiggle, fake expression or 2D transform of a
static PNG. They are packaged into looping transparent GIFs offline at build
time. The source repository has additional animation coverage, but only the
verified selected catalog is staged to avoid growing the keyboard APK by
gigabytes.

These are **prerendered animated 3D illustrations**, not interactive 3D models
such as GLB meshes. The selected catalog is defined in
`scripts/build_emoji3d_motion.py`, including the exact upstream source
paths. Emojis not in the motion index retain their exact Fluent static artwork
and/or Unicode fallback. Skin-tone variants are never silently substituted
with the default skin tone.

## Source and reproducibility

- Source: `microsoft/fluentui-emoji-animated` (MIT)
- Pinned source commit: `daa0365c09795789ed2bc6e8b228c97736cb6669`
- The build fetches **only** 30 selected Git LFS files. No full-size
  multi-GB animation repository is downloaded.
- `scripts/build_emoji3d_motion.py` converts the actual source APNG frames
  with Pillow 11.3.0, resizes to 128x128 while preserving alpha, bounds
  output to 48 GIF frames / 8 MiB per image, preserves the original total
  timing, verifies multiple distinct frames, and writes a source SHA-256
  manifest.
- `assets/emoji3d-motion/LICENSE.txt` includes the official Microsoft MIT
  license; `SOURCE.json` records provenance and per-animation hashes.
- Assets are packaged only at build time; the installed keyboard never
  downloads them.

The existing pinned source for static Fluent 3D (`assets/emoji3d`) remains
untouched.

## Security, performance and correctness

- Only the non-exported `${applicationId}.emoji3d` provider serves the files,
  with URI grant permissions to the current editor.
- Cache filenames are SHA-256 hashes of known asset keys, not user-provided
  filesystem paths. MIME and URI structure are validated by the provider.
- Exported files are written atomically and checked against the 8 MiB cap.
- A 64 MiB target cache evicts old content and expires unused files after
  24 hours, retaining recently shared media for a minimum of ten minutes.
- All file preparation runs on Dispatchers.IO. A mutex preserves tap order.
- Unicode fallback never fires after an accepted GIF/PNG commit, avoiding
  duplicate messages.

## Device validation matrix

| Scenario | Expected result |
| --- | --- |
| Animated catalog emoji; GIF supported; commit accepted | One looping GIF, no duplicate Unicode |
| GIF supported; commit rejected; PNG supported | One static 3D PNG |
| GIF not supported; PNG supported | One static 3D PNG |
| Neither MIME supported | One exact Unicode/ASCII insertion |
| Selected emoji lacks official motion | Existing static 3D or exact Unicode |
| Skin-tone/ZWJ variant lacks matching source | Exact variant retained in PNG/Unicode, no tone swap |
| 3D relief disabled | Original Unicode insertion |
| Switch chats during GIF/PNG preparation | No insertion in the newly focused chat |
| 20 rapid taps | One ordered output per tap |
| Receiver accepts GIF as attachment | Animated media may require manual send confirmation |
| Receiver claims GIF but displays static preview | Client-specific limitation; verify received message playback |

JVM transport tests and the GitHub Actions motion-asset validation workflow
must pass before merging. Compile and test Stable Release with all 30 generated
animations and the original 2,980 Fluent static WebP assets present.

Do not mark compatibility with WhatsApp, Telegram, Signal or another receiver
as verified without actually sending and receiving sample GIFs on devices.
