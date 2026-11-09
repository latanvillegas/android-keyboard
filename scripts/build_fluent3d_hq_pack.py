#!/usr/bin/env python3
"""Create an offline-installable Fluent 3D full-color pack for a slim APK.

No recompression of any WebP pixels or animation frames. GitHub keeps GIF
fallbacks and the 128px picker inside the APK; the optional pack contains
2,980 256px static RGBA masters plus 30 lossless Animated WebP originals.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

PACK_FORMAT = "fluent3d-hq-v1"
METADATA = ("index.txt", "SOURCE.json", "LICENSE.txt")


def sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def gather(static: Path, motion: Path) -> tuple[list[tuple[str, bytes]], int, int]:
    def keys(folder: Path) -> list[str]:
        found = (folder / "index.txt").read_text(encoding="utf-8").splitlines()
        if len(found) != len(set(found)):
            raise ValueError(f"Duplicate catalog entries: {folder}")
        return found
    static_keys = keys(static)
    motion_keys = keys(motion)
    if len(static_keys) < 2500 or len(motion_keys) != 30:
        raise ValueError("Unexpected Fluent catalog count")
    members: list[tuple[str, bytes]] = []
    for name, directory, items in (
        ("emoji3d-share", static, static_keys),
        ("emoji3d-motion", motion, motion_keys),
    ):
        for key in items:
            if not key or any(c not in "0123456789abcdef-" for c in key):
                raise ValueError(f"Invalid Unicode key: {key}")
        for file_name in METADATA + tuple(key + ".webp" for key in items):
            source = directory / file_name
            if not source.is_file():
                raise ValueError(f"Missing source artwork: {source}")
            data = source.read_bytes()
            if not data or len(data) > 16 * 1024 * 1024:
                raise ValueError(f"Out of range artwork: {source}")
            if source.suffix == ".webp":
                if data[:4] != b"RIFF" or data[8:12] != b"WEBP":
                    raise ValueError(f"Not WebP: {source}")
            members.append((f"{name}/{file_name}", data))
    return members, len(static_keys), len(motion_keys)


def build(static: Path, motion: Path, output: Path) -> None:
    members, static_count, motion_count = gather(static, motion)
    manifest = {
        "format": PACK_FORMAT,
        "version": 1,
        "static_count": static_count,
        "motion_count": motion_count,
        "members": {name: sha256(data) for name, data in members},
        "description": "Microsoft Fluent 3D lossless RGBA static + Animated WebP",
        "distribution": "offline installable media files; no executable code",
    }
    manifest_bytes = (json.dumps(manifest, separators=(",", ":"), sort_keys=True)
                      + "\n").encode("utf-8")
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", allowZip64=True) as archive:
        # First entry permits deterministic detection without scanning 3k files.
        archive.writestr("fluent-hq.json", manifest_bytes,
                         compress_type=zipfile.ZIP_STORED)
        for name, data in members:
            archive.writestr(name, data, compress_type=zipfile.ZIP_STORED)
    with zipfile.ZipFile(output) as archive:
        assert archive.namelist()[0] == "fluent-hq.json"
        assert len(archive.namelist()) == len(members) + 1
        for name, expected in manifest["members"].items():
            if sha256(archive.read(name)) != expected:
                raise ValueError(f"Corrupt pack member: {name}")
    print(f"Fluent HQ pack: {static_count} static 256px masters, "
          f"{motion_count} animated WebP, "
          f"{output.stat().st_size / 1048576:.1f} MiB, SHA256 {sha256(output.read_bytes())}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--static", type=Path, required=True)
    parser.add_argument("--motion", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    build(args.static, args.motion, args.out)


if __name__ == "__main__":
    main()
