#!/usr/bin/env python3
"""Package real Microsoft Fluent 3D artwork into separate preview/share assets.

The picker keeps compact 128px WebP illustrations; the content provider sends
a distinct, lossless 256px RGBA WebP master converted to PNG on demand.
Neither path ever upscales lower-resolution source art.
Both catalogs are generated offline during the Stable Release build.
"""
from __future__ import annotations

import argparse
import os
from concurrent.futures import ProcessPoolExecutor
import json
import re
import shutil
from pathlib import Path

from PIL import Image

ASSET_NAME = re.compile(r"^([0-9a-f]+(?:-[0-9a-f]+)*)_3d\.png$")
PREVIEW_SIDE = 128
SHARE_SIDE = 256
MIN_IMAGES = 2500
DEFAULT_WORKERS = min(4, os.cpu_count() or 1)


def index_and_license(
    folder: Path, keys: list[str], license_file: Path, source_sha: str,
    *, side: int, format_description: str, total_bytes: int
) -> None:
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "index.txt").write_text(
        "\n".join(keys) + "\n", encoding="utf-8"
    )
    shutil.copyfile(license_file, folder / "LICENSE.txt")
    (folder / "SOURCE.json").write_text(
        json.dumps(
            {
                "origin": "microsoft/fluentui-emoji",
                "unicode_reorganization": "shuding/fluentui-emoji-unicode",
                "source_commit": source_sha,
                "license": "MIT, copyright Microsoft Corporation",
                "format": format_description,
                "maximum_side_pixels": side,
                "count": len(keys),
                "total_bytes": total_bytes,
            },
            indent=2,
            ensure_ascii=False,
        ) + "\n",
        encoding="utf-8",
    )


def convert_single(task: tuple[Path, Path, Path | None]) -> tuple[str, int, int]:
    """Convert one independent illustration in a worker process.

    WebP lossless quality is ENCODER EFFORT, not pixel accuracy. Level 80
    and method 4 preserve exact 8-bit RGBA while avoiding the slowest search.
    """
    original, destination, share_destination = task
    match = ASSET_NAME.fullmatch(original.name)
    if match is None:
        raise ValueError(f"Invalid Fluent source name: {original}")
    key = match.group(1)
    with Image.open(original) as raw:
        rgba = raw.convert("RGBA")

    preview = rgba.copy()
    preview.thumbnail((PREVIEW_SIDE, PREVIEW_SIDE), Image.Resampling.LANCZOS)
    preview_path = destination / f"{key}.webp"
    preview.save(preview_path, "WEBP", quality=88, method=4, exact=True)
    preview_bytes = preview_path.stat().st_size

    share_bytes = 0
    if share_destination is not None:
        rgba.thumbnail((SHARE_SIDE, SHARE_SIDE), Image.Resampling.LANCZOS)
        share_path = share_destination / f"{key}.webp"
        rgba.save(share_path, "WEBP", lossless=True, quality=80,
                  method=4, exact=True)
        share_bytes = share_path.stat().st_size
    return key, preview_bytes, share_bytes


def package(
    source: Path, destination: Path, upstream_sha: str,
    share_destination: Path | None = None,
    jobs: int = DEFAULT_WORKERS
) -> int:
    if not source.is_dir():
        raise FileNotFoundError(f"Fluent Unicode source missing: {source}")
    license_file = source.parent / "LICENSE"
    if not license_file.is_file() or "MIT License" not in license_file.read_text(
        encoding="utf-8"
    ):
        raise FileNotFoundError("Microsoft Fluent 3D MIT license missing")

    destination.mkdir(parents=True, exist_ok=True)
    if share_destination is not None:
        if destination.resolve() == share_destination.resolve():
            raise ValueError("Preview and sharing folders must be different")
        share_destination.mkdir(parents=True, exist_ok=True)

    originals = sorted(
        path for path in source.glob("*_3d.png")
        if ASSET_NAME.fullmatch(path.name)
    )
    names: list[str] = []
    preview_bytes = share_bytes = 0
    tasks = (
        (original, destination, share_destination)
        for original in originals
    )
    if jobs < 1 or jobs > 8:
        raise ValueError("Worker count must be between 1 and 8")
    if jobs == 1:
        results = map(convert_single, tasks)
        for key, preview_size, share_size in results:
            names.append(key)
            preview_bytes += preview_size
            share_bytes += share_size
            if len(names) % 250 == 0:
                print(f"Converted {len(names)}/{len(originals)} static assets", flush=True)
    else:
        with ProcessPoolExecutor(max_workers=jobs) as pool:
            # map yields deterministically in source order; workers run in parallel.
            for key, preview_size, share_size in pool.map(
                convert_single, tasks, chunksize=8
            ):
                names.append(key)
                preview_bytes += preview_size
                share_bytes += share_size
                if len(names) % 250 == 0:
                    print(
                        f"Converted {len(names)}/{len(originals)} static assets",
                        flush=True,
                    )

    if len(names) < MIN_IMAGES:
        raise RuntimeError(
            f"Only {len(names)} static 3D assets found; require {MIN_IMAGES}"
        )
    for required in ("1f600", "1f44d", "1f44d-1f3fb", "1f602", "2764"):
        if required not in names:
            raise RuntimeError(f"Essential Fluent 3D asset missing: {required}")

    index_and_license(
        destination, names, license_file, upstream_sha,
        side=PREVIEW_SIDE,
        format_description="WebP RGBA 128px, UI preview quality 88",
        total_bytes=preview_bytes
    )
    if share_destination is not None:
        index_and_license(
            share_destination, names, license_file, upstream_sha,
            side=SHARE_SIDE,
            format_description="WebP lossless RGBA 256px, image sharing master",
            total_bytes=share_bytes
        )

    print(
        f"Packaged {len(names)} Fluent 3D illustrations. "
        f"UI preview: {preview_bytes / 1048576:.1f} MiB. "
        f"Lossless sharing: {share_bytes / 1048576:.1f} MiB. "
        f"Source {upstream_sha}."
    )
    return len(names)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--dest", type=Path, required=True)
    parser.add_argument("--share-dest", type=Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--jobs", type=int, default=DEFAULT_WORKERS)
    args = parser.parse_args()
    package(args.source, args.dest, args.commit, args.share_dest, jobs=args.jobs)


if __name__ == "__main__":
    main()
