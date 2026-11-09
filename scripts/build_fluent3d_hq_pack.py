#!/usr/bin/env python3
"""Pack ALL Fluent 3D artwork outside the APK without recompressing pixels."""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

FORMAT = "fluent3d-full-v2"
META = ("index.txt", "SOURCE.json", "LICENSE.txt")
MAX_MEMBER = 16 * 1024 * 1024
KEY_CHARACTERS = frozenset("0123456789abcdef-")


def checksum(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as inp:
        for chunk in iter(lambda: inp.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def keys(root: Path) -> list[str]:
    result = (root / "index.txt").read_text("utf-8").splitlines()
    if not result or len(result) != len(set(result)) or any(
        not value or any(c not in KEY_CHARACTERS for c in value)
        for value in result
    ):
        raise ValueError(f"Invalid Fluent index: {root}")
    return result


def inventory(preview: Path, share: Path, motion: Path):
    ui, high, animated = keys(preview), keys(share), keys(motion)
    if len(ui) < 2500 or ui != high or len(animated) != 30:
        raise ValueError("Fluent catalog mismatch or wrong animation count")
    groups = (
        ("emoji3d", preview, (key + ".webp" for key in ui)),
        ("emoji3d-share", share, (key + ".webp" for key in high)),
        ("emoji3d-motion", motion,
         (key + suffix for key in animated for suffix in (".webp", ".gif"))),
    )
    files: list[tuple[str, Path, str]] = []
    for prefix, root, images in groups:
        for basename in [*META, *images]:
            source = root / basename
            if not source.is_file() or not (0 < source.stat().st_size <= MAX_MEMBER):
                raise ValueError(f"Missing/oversized Fluent asset: {source}")
            if basename.endswith(".webp"):
                with source.open("rb") as stream:
                    header = stream.read(12)
                if header[:4] != b"RIFF" or header[8:12] != b"WEBP":
                    raise ValueError(f"Invalid WebP: {source}")
            if basename.endswith(".gif"):
                with source.open("rb") as stream:
                    if stream.read(6) not in (b"GIF89a", b"GIF87a"):
                        raise ValueError(f"Invalid GIF: {source}")
            files.append((f"{prefix}/{basename}", source, checksum(source)))
    if len(files) > 6500:
        raise ValueError("Excessive pack inventory")
    return files, ui, animated


def stable_info(filename: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(filename, date_time=(1980, 1, 1, 0, 0, 0))
    info.compress_type = zipfile.ZIP_STORED
    info.create_system = 3
    info.external_attr = 0o644 << 16
    return info


def build(preview: Path, share: Path, motion: Path, output: Path) -> None:
    files, ui, animated = inventory(preview, share, motion)
    manifest = {
        "format": FORMAT,
        "version": 2,
        "ui_count": len(ui),
        "share_count": len(ui),
        "motion_count": len(animated),
        "members": {name: digest for name, _, digest in files},
        "license": "Microsoft Fluent Emoji (MIT); see LICENSE.txt in pack",
    }
    encoded = (json.dumps(manifest, sort_keys=True, separators=(",", ":")) +
               "\n").encode("utf-8")
    if len(encoded) > 2 * 1024 * 1024:
        raise ValueError("Manifest size limit exceeded")
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", allowZip64=True) as archive:
        archive.writestr(stable_info("fluent-hq.json"), encoded)
        for name, source, _ in files:
            with archive.open(stable_info(name), "w") as out:
                with source.open("rb") as inp:
                    shutil.copyfileobj(inp, out, 1024 * 1024)
    with zipfile.ZipFile(output) as archive:
        if archive.namelist()[0] != "fluent-hq.json" or len(archive.namelist()) != len(files) + 1:
            raise ValueError("Incomplete ZIP inventory")
        for name, _, expected in files:
            digest = hashlib.sha256()
            with archive.open(name) as inp:
                for chunk in iter(lambda: inp.read(1024 * 1024), b""):
                    digest.update(chunk)
            if digest.hexdigest() != expected:
                raise ValueError(f"Corrupt Fluent pack member: {name}")
    print(f"Verified pack: {len(ui)} picker and lossless sharing images; "
          f"{len(animated)} Animated WebP and {len(animated)} GIF; "
          f"{output.stat().st_size / 1048576:.1f} MiB.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview", type=Path, required=True)
    parser.add_argument("--share", type=Path, required=True)
    parser.add_argument("--motion", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    build(args.preview, args.share, args.motion, args.out)


if __name__ == "__main__":
    main()
