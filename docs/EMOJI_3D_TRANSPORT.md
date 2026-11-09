# Adaptive Fluent Emoji 3D transport

## Contract

The emoji picker continues to display the existing prerendered Fluent 3D
WebP assets when the relief setting is enabled. No new artwork is generated.

Selecting an emoji uses one ordered output path:

1. Capture the base editor session (input connection + EditorInfo identity).
2. When 3D is disabled, or the editor does not advertise `image/png`, insert
   the original Unicode string (ASCII emoticons retain whitespace handling).
3. When 3D is enabled and PNG is advertised, resolve the same Fluent asset
   used by the picker, prepare a transparent PNG in the private cache on
   Dispatchers.IO and offer a read-only `content://` URI to the editor.
4. If the artwork does not exist, PNG preparation fails or the editor explicitly
   rejects `commitContent`, insert the original Unicode string once.
5. If `commitContent` returns true, do **not** insert any Unicode fallback.
6. If the editor session changes while preparing the PNG, do not insert
   anything into the newly focused editor.

Image insertion may appear as an attachment or preview instead of an inline
text glyph, depending on the receiving application. A true `commitContent`
return value means the editor accepted the content request; it does **not**
prove that a message was transmitted or delivered.

## Storage and permissions

- The provider uses `${applicationId}.emoji3d`, `exported=false` and
  `grantUriPermissions=true`. The existing `typeUri` path grants
  `FLAG_GRANT_READ_URI_PERMISSION` to the editor's package.
- PNGs are addressed by SHA-256 of the resolved asset key, never by a
  caller-controlled file path. The provider checks URI authority, scheme,
  path and fixed-length hex identifier.
- Output creation is atomic (temporary file, then rename), and serialized.
- Individual PNG output is limited to 8 MiB; the on-demand cache targets
  64 MiB and expires unused files after 24 hours. Recently shared files are
  retained for at least ten minutes to allow asynchronous reads.
- No network requests or external storage access are needed.

## Manual Android integration matrix

| Test | Expected behavior |
| --- | --- |
| 3D on; editor advertises image/png; asset present; commit accepted | One PNG insertion; no Unicode duplicate |
| 3D on; editor advertises image/png; asset present; commit rejected | One Unicode insertion |
| 3D on; editor has no image MIME capability | One Unicode insertion |
| 3D on; Fluent asset missing | One Unicode insertion |
| 3D off; any editor | Original Unicode/ASCII behavior |
| Skin-tone or ZWJ variant with artwork | Exact variant exported; never replace with default skin tone |
| Skin-tone or ZWJ variant without artwork | Exact selected Unicode sequence |
| Switch app/field while PNG is rendering | No insertion in newly focused editor |
| Rapid tapping of 20 emojis | Each tap handled once, in queue order |
| Repeated image selection | Reuse prepared PNG without unbounded duplicate files |
| Editor opens content URI after returning true | Provider exposes complete, readable image/png |
| Editor rejects or throws | No uncaught exception, Unicode fallback |
| Image receiver does not render as inline emoji | May appear as attachment; design remains intact |

JVM unit tests cover disabled 3D, unsupported editors, stale sessions, ASCII
fallback and repeated/concurrent Unicode inserts. Full image commit behavior
must be verified on devices using actual messaging applications; the emulator
cannot establish compatibility for third-party apps.

## Validation before merging

Run the existing project's Gradle unit-test and Stable Release compilation
pipeline, including packaging of the pinned Fluent asset source, then perform
the Android integration matrix on devices. Do not merge based solely on
`commitContent` returning true or the appearance of an image inside the
keyboard picker.
