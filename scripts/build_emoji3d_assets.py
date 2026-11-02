#!/usr/bin/env python3
"""Package licensed Fluent Emoji 3D PNGs as local transparent WebP Android assets.

This script runs only in GitHub Actions before assembleStableRelease. It does
not download anything at runtime and does not change keyboard themes.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
from pathlib import Path

from PIL import Image

ASSET_NAME = re.compile(r"^([0-9a-f]+(?:-[0-9a-f]+)*)_3d\.png$")
MAX_SIDE = 128
MIN_IMAGES = 2500


def package(source: Path, destination: Path, upstream_sha: str) -> int:
    if not source.is_dir():
        raise FileNotFoundError(f"Upstream Fluent Unicode assets not found: {source}")
    license_file = source.parent / "LICENSE"
    if not license_file.is_file():
        raise FileNotFoundError("Required Microsoft MIT license is missing")

    destination.mkdir(parents=True, exist_ok=True)
    names: list[str] = []
    for original in sorted(source.glob("*_3d.png")):
        match = ASSET_NAME.fullmatch(original.name)
        if not match:
            continue
        codepoint = match.group(1)
        output = destination / f"{codepoint}.webp"
        with Image.open(original) as raw:
            image = raw.convert("RGBA")
            image.thumbnail((MAX_SIDE, MAX_SIDE), Image.Resampling.LANCZOS)
            image.save(output, "WEBP", quality=88, method=4, exact=True)
        names.append(codepoint)

    if len(names) < MIN_IMAGES:
        raise RuntimeError(
            f"Only {len(names)} 3D emoji assets were found; require {MIN_IMAGES}"
        )
    for essential in ("1f600", "1f44d", "1f44d-1f3fb", "1f602", "2764"):
        if essential not in names:
            raise RuntimeError(f"Essential 3D emoji asset missing: {essential}")

    (destination / "index.txt").write_text(
        "\n".join(names) + "\n", encoding="utf-8"
    )
    shutil.copyfile(license_file, destination / "LICENSE.txt")
    (destination / "SOURCE.json").write_text(
        json.dumps(
            {
                "origin": "microsoft/fluentui-emoji",
                "unicode_reorganization": "shuding/fluentui-emoji-unicode",
                "source_commit": upstream_sha,
                "license": "MIT, copyright Microsoft Corporation",
                "format": "WebP RGBA, prerendered 3D artwork",
                "maximum_side_pixels": MAX_SIDE,
                "count": len(names),
            },
            indent=2,
            ensure_ascii=False,
        ) + "\n",
        encoding="utf-8",
    )
    total_bytes = sum(file.stat().st_size for file in destination.glob("*.webp"))
    print(
        f"Packaged {len(names)} Fluent Emoji 3D assets, "
        f"{total_bytes / (1024 * 1024):.1f} MiB, "
        f"source {upstream_sha}"
    )
    return len(names)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--dest", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    args = parser.parse_args()
    package(args.source, args.dest, args.commit)


if __name__ == "__main__":
    main()
