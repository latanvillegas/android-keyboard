#!/usr/bin/env python3
"""No-network test suite for the complete offline Fluent 3D ZIP inventory.

Builds realistic counts with tiny valid signature fixtures; production images
are separately checked byte-for-byte and decoded in the final CI workflow.
"""
from __future__ import annotations

import hashlib
import importlib.util
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "fluent_pack_builder", ROOT / "build_fluent3d_hq_pack.py"
)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)

WEBP = b"RIFF" + (12).to_bytes(4, "little") + b"WEBP" + b"VP8X" + b"\x00" * 8
GIF = b"GIF89a" + b"\x01\x00\x01\x00" + b"\x80\x00\x00" + b"\x00" * 9


class FluentFullPackTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.preview = self.root / "emoji3d"
        self.share = self.root / "emoji3d-share"
        self.motion = self.root / "emoji3d-motion"
        self.keys = [f"{0x1f600 + i:x}" for i in range(2980)]
        self.animations = self.keys[:30]
        for folder, selected, extensions in (
            (self.preview, self.keys, (".webp",)),
            (self.share, self.keys, (".webp",)),
            (self.motion, self.animations, (".webp", ".gif")),
        ):
            folder.mkdir()
            (folder / "index.txt").write_text(
                "\n".join(selected) + "\n", encoding="utf-8"
            )
            (folder / "LICENSE.txt").write_text("Microsoft MIT License")
            (folder / "SOURCE.json").write_text('{"source":"fixture"}')
            for key in selected:
                for ext in extensions:
                    (folder / (key + ext)).write_bytes(
                        GIF if ext == ".gif" else WEBP
                    )
        self.output = self.root / "complete.zip"

    def test_four_catalogs_and_original_byte_identity(self):
        module.build(self.preview, self.share, self.motion, self.output)
        with zipfile.ZipFile(self.output) as archive:
            manifest = json.loads(archive.read("fluent-hq.json"))
            self.assertEqual(manifest["format"], "fluent3d-full-v2")
            self.assertEqual(manifest["ui_count"], 2980)
            self.assertEqual(manifest["share_count"], 2980)
            self.assertEqual(manifest["motion_count"], 30)
            self.assertEqual(len(manifest["members"]), 6029)
            self.assertEqual(len(archive.namelist()), 6030)
            for directory in ("emoji3d", "emoji3d-share", "emoji3d-motion"):
                for item in ("index.txt", "SOURCE.json", "LICENSE.txt"):
                    self.assertIn(f"{directory}/{item}", manifest["members"])
            for folder, source in (
                ("emoji3d", self.preview), ("emoji3d-share", self.share),
                ("emoji3d-motion", self.motion),
            ):
                for original in source.iterdir():
                    data = archive.read(f"{folder}/{original.name}")
                    self.assertEqual(data, original.read_bytes())
                    self.assertEqual(
                        hashlib.sha256(data).hexdigest(),
                        manifest["members"][f"{folder}/{original.name}"]
                    )

    def test_deterministic_pack(self):
        module.build(self.preview, self.share, self.motion, self.output)
        sha1 = hashlib.sha256(self.output.read_bytes()).hexdigest()
        module.build(
            self.preview, self.share, self.motion, self.root / "again.zip"
        )
        sha2 = hashlib.sha256((self.root / "again.zip").read_bytes()).hexdigest()
        self.assertEqual(sha1, sha2)

    def test_rejects_invalid_source_and_incomplete_catalog(self):
        (self.motion / (self.animations[0] + ".gif")).unlink()
        with self.assertRaises(ValueError):
            module.build(self.preview, self.share, self.motion, self.output)
        (self.motion / (self.animations[0] + ".gif")).write_bytes(GIF)
        (self.preview / (self.keys[0] + ".webp")).write_bytes(b"FAKEWEBP")
        with self.assertRaises(ValueError):
            module.build(self.preview, self.share, self.motion, self.output)

    def test_rejects_mismatched_static_index(self):
        (self.share / "index.txt").write_text(
            "\n".join(reversed(self.keys)) + "\n", encoding="utf-8"
        )
        with self.assertRaises(ValueError):
            module.build(self.preview, self.share, self.motion, self.output)


if __name__ == "__main__":
    unittest.main()
