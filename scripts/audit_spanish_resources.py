#!/usr/bin/env python3
"""Audit UI Spanish coverage against the exact translations Git submodule.

Read-only. Reports missing translations and argument-placeholder mismatches.
The report is included with Release artifacts. It does not create overlapping
Android resources or modify the upstream translation submodule.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

PLACEHOLDER = re.compile(r"%(?!%)(?:[1-9][0-9]*\$)?[-+# 0,(]*[0-9]*(?:\.[0-9]+)?[a-zA-Z]")


def read_strings(paths: list[Path], strict: bool = False) -> dict[str, tuple[str, str]]:
    result: dict[str, tuple[str, str]] = {}
    for path in sorted(paths):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError as e:
            if strict:
                raise ValueError(f"Invalid XML {path}: {e}") from e
            continue
        for item in root:
            name = item.attrib.get("name")
            if not name or item.tag not in ("string", "plurals", "string-array"):
                continue
            if item.attrib.get("translatable", "true").lower() == "false":
                continue
            value = " ".join("".join(item.itertext()).split())
            if value:
                result[name] = (value, str(path))
    return result


def translated_file(path: Path) -> bool:
    return any(folder.startswith("values-es") for folder in path.parts)


def main() -> None:
    cli = argparse.ArgumentParser()
    cli.add_argument("--root", type=Path, default=Path("."))
    cli.add_argument("--out", type=Path, default=Path("spanish-audit"))
    args = cli.parse_args()
    root = args.root

    base = read_strings(list((root / "java/res/values").glob("*.xml")), strict=True)
    # Covers the public core/core-ign translation trees and fork-local overrides.
    candidates = (
        list((root / "translations").rglob("*.xml"))
        + list((root / "java/res").rglob("*.xml"))
    )
    available = read_strings([p for p in candidates if translated_file(p)], strict=True)
    missing = sorted(set(base) - set(available))

    mismatches: list[dict[str, object]] = []
    for key in sorted(set(base) & set(available)):
        orig = PLACEHOLDER.findall(base[key][0].replace("%%", ""))
        translated = PLACEHOLDER.findall(available[key][0].replace("%%", ""))
        if sorted(orig) != sorted(translated):
            mismatches.append({
                "id": key,
                "english": orig,
                "spanish": translated,
                "source": available[key][1],
            })

    settings_prefix = (
        "settings_", "theme_", "action_", "voice_", "language_", "prefs_",
        "keyboard_", "typing_", "advanced_", "custom_"
    )
    high_priority = [k for k in missing if k.startswith(settings_prefix)]
    report = {
        "source_strings": len(base),
        "translated_strings": len(set(base) & set(available)),
        "coverage_percent": round(
            100 * len(set(base) & set(available)) / max(1, len(base)), 2
        ),
        "missing_count": len(missing),
        "missing": [
            {"id": k, "english": base[k][0], "source": base[k][1]}
            for k in missing
        ],
        "high_priority_missing": high_priority,
        "placeholder_mismatches": mismatches,
        "localization_source_count": len(available),
    }
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    heading = [
        "# Cobertura de la interfaz en español",
        "",
        f"Cadenas originales: {len(base)}",
        f"Traducciones disponibles: {report['translated_strings']}",
        f"Cobertura: {report['coverage_percent']}%",
        f"Pendientes: {len(missing)}",
        f"Marcadores de formato a revisar: {len(mismatches)}",
        "",
        "## Cadenas prioritarias pendientes",
        "",
    ]
    heading.extend(
        f"- \`{name}\`: {base[name][0]}" for name in high_priority[:200]
    )
    (args.out / "report.md").write_text(
        "\n".join(heading) + "\n", encoding="utf-8"
    )
    print(
        f"Spanish coverage: {report['coverage_percent']}% "
        f"({report['translated_strings']}/{len(base)}), "
        f"missing={len(missing)}, placeholders={len(mismatches)}"
    )
    if not (root / "translations").exists():
        print("WARNING: translations submodule is not checked out", file=sys.stderr)


if __name__ == "__main__":
    main()
