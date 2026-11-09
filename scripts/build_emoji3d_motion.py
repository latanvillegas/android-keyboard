#!/usr/bin/env python3
"""Package genuine Microsoft Fluent animated APNG frames as offline GIF stickers.

Source: microsoft/fluentui-emoji-animated, pinned to an immutable commit, MIT.
This script intentionally uses *the original animation frames*: no motion
effects are synthesized from static artwork.

Only a curated set is checked out with Git LFS, avoiding the multi-GB source.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import shutil
from pathlib import Path

from PIL import Image

UPSTREAM_SHA = "daa0365c09795789ed2bc6e8b228c97736cb6669"
SIZE = 128
MAX_FRAMES = 48
MAX_GIF_BYTES = 8 * 1024 * 1024

# Unicode key -> official APNG path. These are exact tree paths at UPSTREAM_SHA.
# Variants not listed here intentionally fall back to exact static Fluent artwork.
SOURCES = {
    "1f600": "assets/Grinning face/animated/grinning_face_animated.png",
    "1f603": "assets/Grinning face with big eyes/animated/grinning_face_with_big_eyes_animated.png",
    "1f604": "assets/Grinning face with smiling eyes/animated/grinning_face_with_smiling_eyes_animated.png",
    "1f601": "assets/Beaming face with smiling eyes/animated/beaming_face_with_smiling_eyes_animated.png",
    "1f602": "assets/Face with tears of joy/animated/face_with_tears_of_joy_animated.png",
    "1f923": "assets/Rolling on the floor laughing/animated/rolling_on_the_floor_laughing_animated.png",
    "1f60a": "assets/Smiling face with smiling eyes/animated/smiling_face_with_smiling_eyes_animated.png",
    "1f970": "assets/Smiling face with hearts/animated/smiling_face_with_hearts_animated.png",
    "1f60d": "assets/Smiling face with heart-eyes/animated/smiling_face_with_heart-eyes_animated.png",
    "1f618": "assets/Face blowing a kiss/animated/face_blowing_a_kiss_animated.png",
    "1f609": "assets/Winking face/animated/winking_face_animated.png",
    "1f642": "assets/Slightly smiling face/animated/slightly_smiling_face_animated.png",
    "1f643": "assets/Upside-down face/animated/upside-down_face_animated.png",
    "1f62d": "assets/Loudly crying face/animated/loudly_crying_face_animated.png",
    "1f622": "assets/Crying face/animated/crying_face_animated.png",
    "1f621": "assets/Pouting face/animated/pouting_face_animated.png",
    "1f620": "assets/Angry face/animated/angry_face_animated.png",
    "1f914": "assets/Thinking face/animated/thinking_face_animated.png",
    "1f928": "assets/Face with raised eyebrow/animated/face_with_raised_eyebrow_animated.png",
    "1f62e": "assets/Face with open mouth/animated/face_with_open_mouth_animated.png",
    "1f632": "assets/Astonished face/animated/astonished_face_animated.png",
    "1f631": "assets/Face screaming in fear/animated/face_screaming_in_fear_animated.png",
    "1f644": "assets/Face with rolling eyes/animated/face_with_rolling_eyes_animated.png",
    "1f60e": "assets/Smiling face with sunglasses/animated/smiling_face_with_sunglasses_animated.png",
    "1f973": "assets/Partying face/animated/partying_face_animated.png",
    "1f61b": "assets/Face with tongue/animated/face_with_tongue_animated.png",
    "1f92a": "assets/Zany face/animated/zany_face_animated.png",
    "1f634": "assets/Sleeping face/animated/sleeping_face_animated.png",
    "2764": "assets/Red heart/animated/red_heart_animated.png",
    "1f496": "assets/Sparkling heart/animated/sparkling_heart_animated.png",
}


def palettize(rgba: Image.Image) -> Image.Image:
    """Reserve palette index 0 for transparency; preserve per-frame RGB colors."""
    rgb = Image.new("RGB", rgba.size, (0, 0, 0))
    rgb.paste(rgba, mask=rgba.getchannel("A"))
    quantized = rgb.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    palette = quantized.getpalette()[: 255 * 3]
    palette.extend([0] * (255 * 3 - len(palette)))
    indexed = quantized.tobytes()
    alpha = rgba.getchannel("A").tobytes()
    shifted = bytes(
        (index + 1 if opacity >= 128 else 0)
        for index, opacity in zip(indexed, alpha)
    )
    output = Image.frombytes("P", rgba.size, shifted)
    output.putpalette([0, 0, 0] + palette)
    output.info["transparency"] = 0
    return output


def convert_apng(source: Path, destination: Path) -> dict:
    with Image.open(source) as image:
        total = getattr(image, "n_frames", 1)
        first = 1 if image.info.get("default_image", False) else 0
        usable = total - first
        if usable < 2:
            raise ValueError(f"Not a genuine frame animation: {source}")

        stride = max(1, math.ceil(usable / MAX_FRAMES))
        frames = []
        delays = []
        for index in range(first, total):
            image.seek(index)
            duration = int(float(image.info.get("duration", 80) or 80))
            duration = max(20, min(500, duration))
            if (index - first) % stride != 0:
                delays[-1] += duration
                continue

            rgba = image.convert("RGBA")
            rgba.thumbnail((SIZE, SIZE), Image.Resampling.LANCZOS)
            canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
            left = (SIZE - rgba.width) // 2
            top = (SIZE - rgba.height) // 2
            canvas.alpha_composite(rgba, (left, top))
            frames.append(palettize(canvas))
            delays.append(duration)

        if len(frames) < 2:
            raise ValueError(f"Animated frames collapsed: {source}")

        destination.parent.mkdir(parents=True, exist_ok=True)
        frames[0].save(
            destination,
            format="GIF",
            save_all=True,
            append_images=frames[1:],
            loop=0,
            duration=delays,
            disposal=2,
            transparency=0,
            optimize=False,
        )

    size = destination.stat().st_size
    if not 1 <= size <= MAX_GIF_BYTES:
        destination.unlink(missing_ok=True)
        raise ValueError(f"GIF size {size} exceeds limit for {source}")

    # Verify that the packaged result has multiple distinct frames, rather than
    # a container with identical still images.
    with Image.open(destination) as check:
        if check.format != "GIF" or check.n_frames < 2:
            raise ValueError(f"GIF lost its animation: {destination}")
        check.seek(0)
        first_bytes = check.convert("RGBA").tobytes()
        distinct = False
        for i in range(1, check.n_frames):
            check.seek(i)
            if check.convert("RGBA").tobytes() != first_bytes:
                distinct = True
                break
        if not distinct:
            raise ValueError(f"Animation contains no visible changes: {source}")
        output_frames = check.n_frames

    return {
        "frames": output_frames,
        "duration_ms": sum(delays),
        "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        "output_bytes": size,
    }


def package(source_root: Path, destination: Path) -> int:
    license_file = source_root / "LICENSE"
    if not license_file.is_file() or "MIT License" not in license_file.read_text():
        raise FileNotFoundError("Official Microsoft animated emoji MIT license missing")
    destination.mkdir(parents=True, exist_ok=True)
    entries: dict[str, dict] = {}
    for key, relative in SOURCES.items():
        path = source_root / relative
        if not path.is_file():
            raise FileNotFoundError(f"Required pinned Fluent animation missing: {relative}")
        output = destination / f"{key}.gif"
        detail = convert_apng(path, output)
        entries[key] = {"source": relative, **detail}
        print(f"{key}: {detail['frames']} genuine frames, {detail['output_bytes']:,} bytes")

    (destination / "index.txt").write_text(
        "\n".join(sorted(entries)) + "\n", encoding="utf-8"
    )
    shutil.copyfile(license_file, destination / "LICENSE.txt")
    (destination / "SOURCE.json").write_text(
        json.dumps({
            "origin": "microsoft/fluentui-emoji-animated",
            "commit": UPSTREAM_SHA,
            "license": "MIT, Copyright (c) Microsoft Corporation",
            "note": "Original frame-by-frame Fluent 3D animation, optimized to offline GIF",
            "count": len(entries),
            "assets": entries,
        }, indent=2, sort_keys=True) + "\n",
        encoding="utf-8"
    )
    print(f"Packaged {len(entries)} genuine animated Fluent emojis.")
    return len(entries)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path)
    parser.add_argument("--dest", type=Path)
    parser.add_argument("--list-source-paths", action="store_true")
    args = parser.parse_args()
    if args.list_source_paths:
        print(",".join(SOURCES.values()))
        return
    if args.source is None or args.dest is None:
        parser.error("--source and --dest are required unless --list-source-paths is used")
    package(args.source, args.dest)


if __name__ == "__main__":
    main()
