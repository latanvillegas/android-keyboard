#!/usr/bin/env python3
"""Build full-color, genuine Fluent 3D animations for offline Android sharing.

Animated WebP: 256x256 RGBA, lossless, original motion frames and timing.
Fallback GIF: 256x256, limited palette only where the receiving editor needs it.
No upscaling of the original artwork, no synthesized motion, and no runtime I/O
outside local Android assets. Source is a pinned official Microsoft MIT commit.
"""
from __future__ import annotations

import argparse
import os
from concurrent.futures import ProcessPoolExecutor
import hashlib
import json
import math
import shutil
from pathlib import Path

from PIL import Image, features

UPSTREAM_SHA = "daa0365c09795789ed2bc6e8b228c97736cb6669"
SIZE = 256
MAX_WEBP_FRAMES = 96
MAX_GIF_FRAMES = 48
MAX_WEBP_BYTES = 16 * 1024 * 1024
MAX_GIF_BYTES = 16 * 1024 * 1024
DEFAULT_WORKERS = min(3, os.cpu_count() or 1)

# Unicode key -> exact upstream APNG location at the immutable commit.
# Unknown variants are never substituted with the wrong animated glyph.
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


def get_frames(source: Path) -> tuple[list[Image.Image], list[int], tuple[int, int]]:
    """Decode composited source APNG frames exactly once, preserving their timing."""
    with Image.open(source) as image:
        total = getattr(image, "n_frames", 1)
        first = 1 if image.info.get("default_image", False) else 0
        count = total - first
        if count < 2:
            raise ValueError(f"Fluent source is not an animation: {source}")

        source_size = image.size
        stride = max(1, math.ceil(count / MAX_WEBP_FRAMES))
        frames: list[Image.Image] = []
        delays: list[int] = []

        for index in range(first, total):
            image.seek(index)
            duration = int(float(image.info.get("duration", 80) or 80))
            duration = max(20, min(500, duration))
            if (index - first) % stride:
                delays[-1] += duration
                continue

            rgba = image.convert("RGBA")
            rgba.thumbnail((SIZE, SIZE), Image.Resampling.LANCZOS)
            canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
            # Plain paste preserves the original unassociated RGBA bytes,
            # including antialiased edge colors and partially transparent pixels.
            canvas.paste(rgba, ((SIZE - rgba.width) // 2, (SIZE - rgba.height) // 2))
            frames.append(canvas)
            delays.append(duration)

    if len(frames) < 2:
        raise ValueError(f"Too few distinct source frames: {source}")
    return frames, delays, source_size


def sample(
    frames: list[Image.Image], durations: list[int], limit: int
) -> tuple[list[Image.Image], list[int]]:
    stride = max(1, math.ceil(len(frames) / limit))
    sampled: list[Image.Image] = []
    delays: list[int] = []
    for index, frame in enumerate(frames):
        if index % stride:
            delays[-1] += durations[index]
        else:
            sampled.append(frame)
            delays.append(durations[index])
    return sampled, delays


def gif_palette(rgba: Image.Image) -> Image.Image:
    """GIF-only RGBA->indexed conversion, reserving index 0 for transparency."""
    # Preserve straight RGB at the alpha edges. Blending against black
    # before quantization would introduce dark fringes in transparent GIFs.
    rgb = rgba.convert("RGB")
    palette_image = rgb.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    palette = palette_image.getpalette()[: 255 * 3]
    palette.extend([0] * (255 * 3 - len(palette)))
    indexes = palette_image.tobytes()
    alphas = rgba.getchannel("A").tobytes()
    packed = bytes(
        (palette_index + 1 if alpha >= 128 else 0)
        for palette_index, alpha in zip(indexes, alphas)
    )
    frame = Image.frombytes("P", rgba.size, packed)
    frame.putpalette([0, 0, 0] + palette)
    frame.info["transparency"] = 0
    return frame


def validate(
    destination: Path, fmt: str, max_bytes: int, expect_transparency: bool
) -> tuple[int, int]:
    byte_count = destination.stat().st_size
    if not 1 <= byte_count <= max_bytes:
        raise ValueError(f"{destination.name}: {byte_count} bytes is outside limit")
    with Image.open(destination) as result:
        if result.format != fmt or getattr(result, "n_frames", 1) < 2:
            raise ValueError(f"{destination.name}: animation missing or wrong format")
        if result.size != (SIZE, SIZE):
            raise ValueError(f"{destination.name}: incorrect frame resolution")
        result.seek(0)
        pixels = result.convert("RGBA")
        first_bytes = pixels.tobytes()
        has_transparency = pixels.getchannel("A").getextrema()[0] < 255
        changed = False
        for index in range(1, result.n_frames):
            result.seek(index)
            if result.convert("RGBA").tobytes() != first_bytes:
                changed = True
                break
        if not changed:
            raise ValueError(f"{destination.name}: no genuine moving frames")
        if expect_transparency and not has_transparency:
            raise ValueError(f"{destination.name}: alpha channel disappeared")
        return result.n_frames, byte_count


def convert_apng(source: Path, output_webp: Path, output_gif: Path) -> dict:
    frames, delays, original_size = get_frames(source)
    output_webp.parent.mkdir(parents=True, exist_ok=True)

    # Lossless animated WebP keeps the full 24-bit RGB + 8-bit alpha channel.
    # Transparency is preserved; not quantized to the GIF's 256-color palette.
    frames[0].save(
        output_webp,
        format="WEBP",
        save_all=True,
        append_images=frames[1:],
        duration=delays,
        loop=0,
        lossless=True,
        quality=80,
        method=4,
        exact=True,
        minimize_size=False,
    )
    webp_frames, webp_bytes = validate(
        output_webp, "WEBP", MAX_WEBP_BYTES, expect_transparency=True
    )

    gif_frames, gif_delays = sample(frames, delays, MAX_GIF_FRAMES)
    indexed = [gif_palette(frame) for frame in gif_frames]
    indexed[0].save(
        output_gif,
        format="GIF",
        save_all=True,
        append_images=indexed[1:],
        duration=gif_delays,
        loop=0,
        disposal=2,
        transparency=0,
        optimize=False,
    )
    gif_count, gif_bytes = validate(
        output_gif, "GIF", MAX_GIF_BYTES, expect_transparency=True
    )
    return {
        "source_dimensions": list(original_size),
        "size": SIZE,
        "webp_frames": webp_frames,
        "webp_bytes": webp_bytes,
        "gif_frames": gif_count,
        "gif_bytes": gif_bytes,
        "duration_ms": sum(delays),
        "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
    }


def convert_verified(
    task: tuple[str, str, Path, Path]
) -> tuple[str, dict]:
    """Each original APNG is independent: verify Unicode and render in parallel."""
    key, relative, source_root, destination = task
    source = source_root / relative
    if not source.is_file():
        raise FileNotFoundError(f"Pinned animation not found: {relative}")
    metadata_path = source.parent.parent / "metadata.json"
    if not metadata_path.is_file():
        raise FileNotFoundError(f"Emoji metadata missing: {metadata_path}")

    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    actual = metadata["unicode"].lower().replace("_", "-").replace(" ", "-")
    actual = "-".join(
        value for value in actual.split("-") if value not in {"fe0e", "fe0f"}
    )
    if actual != key:
        raise ValueError(f"Unicode mismatch: {key} maps to {actual} in {relative}")

    info = convert_apng(
        source, destination / f"{key}.webp", destination / f"{key}.gif"
    )
    return key, {"source": relative, **info}


def package(source_root: Path, destination: Path, jobs: int = DEFAULT_WORKERS) -> int:
    if not features.check("webp"):
        raise RuntimeError("Pillow must include the libwebp encoder")

    license_file = source_root / "LICENSE"
    if not license_file.is_file() or "MIT License" not in license_file.read_text(
        encoding="utf-8"
    ):
        raise FileNotFoundError("Microsoft's MIT animation license is missing")

    destination.mkdir(parents=True, exist_ok=True)
    if jobs < 1 or jobs > 6:
        raise ValueError("Worker count must be between 1 and 6")
    entries: dict[str, dict] = {}
    tasks = (
        (key, relative, source_root, destination)
        for key, relative in SOURCES.items()
    )

    def record(key: str, info: dict) -> None:
        entries[key] = info
        print(
            f"{len(entries)}/{len(SOURCES)}: {key} lossless WebP "
            f"{info['webp_frames']} frames / {info['webp_bytes']:,} bytes; "
            f"GIF {info['gif_frames']} frames / {info['gif_bytes']:,} bytes",
            flush=True,
        )

    if jobs == 1:
        for key, info in map(convert_verified, tasks):
            record(key, info)
    else:
        with ProcessPoolExecutor(max_workers=jobs) as pool:
            for key, info in pool.map(convert_verified, tasks):
                record(key, info)

    (destination / "index.txt").write_text(
        "\n".join(sorted(entries)) + "\n", encoding="utf-8"
    )
    shutil.copyfile(license_file, destination / "LICENSE.txt")
    (destination / "SOURCE.json").write_text(
        json.dumps(
            {
                "origin": "microsoft/fluentui-emoji-animated",
                "commit": UPSTREAM_SHA,
                "license": "MIT, copyright Microsoft Corporation",
                "format": "Animated WebP lossless RGBA + compatibility GIF",
                "maximum_side_pixels": SIZE,
                "count": len(entries),
                "assets": entries,
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    webp_bytes = sum(x["webp_bytes"] for x in entries.values())
    gif_bytes = sum(x["gif_bytes"] for x in entries.values())
    print(
        f"Verified {len(entries)} original animated Fluent emojis: "
        f"WebP={webp_bytes / 1048576:.1f} MiB, "
        f"GIF={gif_bytes / 1048576:.1f} MiB."
    )
    return len(entries)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path)
    parser.add_argument("--dest", type=Path)
    parser.add_argument("--list-source-paths", action="store_true")
    parser.add_argument("--jobs", type=int, default=DEFAULT_WORKERS)
    args = parser.parse_args()
    if args.list_source_paths:
        print(",".join(SOURCES.values()))
        return
    if args.source is None or args.dest is None:
        parser.error("--source and --dest are required unless --list-source-paths is used")
    package(args.source, args.dest, jobs=args.jobs)


if __name__ == "__main__":
    main()
