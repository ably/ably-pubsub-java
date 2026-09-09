#!/usr/bin/env python3
"""Resolve a UTS spec module directory to its ably-java test targets.

Deterministic helper for the uts-to-kotlin skill. Given a UTS spec *module*
directory (a directory directly under .../specification/uts/), it:

  - validates the path and the module's tier structure,
  - reads uts-package-mapping.json (next to this script's skill dir), where each
    tier value is ONE repo-root-relative path (never machine-absolute),
  - resolves, per tier, the target output directory, Kotlin package (the path
    after 'src/test/kotlin/'), and owning Gradle module (from the path's first
    segment), and
  - lists the candidate spec files with their derived Kotlin class names.

Doing this in code (rather than asking the model to eyeball regexes, join
paths, and hand-convert snake_case -> PascalCase every run) keeps the skill's
selection phase byte-for-byte deterministic.

Usage:
  resolve_uts.py <module-dir>                 # validate + resolve + list specs
  resolve_uts.py <module-dir> --create NAME   # add a mapping entry for this
                                              # module (target base name NAME),
                                              # then resolve

Always prints a single JSON object to stdout. On failure: {"ok": false,
"error": <CODE>, "message": ...} and a non-zero exit.
"""
import argparse
import json
import re
import sys
from pathlib import Path

SKILL_DIR = Path(__file__).resolve().parent.parent
MAPPING = SKILL_DIR / "uts-package-mapping.json"
TIERS = ("unit", "integration", "proxy")
# Owning Gradle module, keyed by a target dir's first path segment. The `lib` -> `:java`
# pair is the load-bearing non-obvious mapping (the `:java` module's build file wires
# `../lib/src/...` srcDirs).
MODULE_BY_PREFIX = {"lib": ":java", "liveobjects": ":liveobjects", "uts": ":uts"}


def fail(code, message):
    print(json.dumps({"ok": False, "error": code, "message": message}, indent=2))
    sys.exit(1)


def class_name(md_path: Path) -> str:
    """objects_batch_test.md -> ObjectsBatchTest; instance.md -> InstanceTest."""
    stem = md_path.stem
    if stem.endswith("_test"):
        stem = stem[: -len("_test")]
    return "".join(part.capitalize() for part in stem.split("_") if part) + "Test"


def is_nonspec_doc(name: str) -> bool:
    """README/PLAN/*_SUMMARY markdown are docs, not test specs."""
    if re.fullmatch(r"(README|PLAN)\.md", name, re.IGNORECASE):
        return True
    return name.upper().endswith("_SUMMARY.MD")


def list_specs(base: Path, exclude_proxy: bool = False):
    """List spec .md files under `base`, deterministically.

    All exclusions are checked against the path **relative to base**, so they
    can't be tripped by an ancestor directory in the checkout path (e.g. a
    clone living under some `.../helpers/...` path).
    """
    if not base.is_dir():
        return []
    specs = []
    for p in sorted(base.rglob("*.md")):
        rel_parts = p.relative_to(base).parts
        if "helpers" in rel_parts:
            continue
        if exclude_proxy and "proxy" in rel_parts:
            continue
        if is_nonspec_doc(p.name):
            continue
        specs.append(p)
    return specs


def package_for(target_dir: str) -> str:
    marker = "src/test/kotlin/"
    idx = target_dir.find(marker)
    return target_dir[idx + len(marker):].replace("/", ".") if idx != -1 else ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("module_dir")
    ap.add_argument(
        "--create",
        metavar="NAME",
        help="add a mapping for this source module using NAME as the ably-java "
        "module base name, then resolve. Scaffolds full lib/-rooted (:java) paths "
        "only — a module whose tiers live in another Gradle module (like objects "
        "-> :liveobjects) still needs a hand-edit afterwards.",
    )
    args = ap.parse_args()

    # Validate via Path.parts so this works regardless of separator (Windows '\' as well as
    # POSIX '/'); a hard-coded "/uts/<module>$" regex would reject otherwise-valid Windows paths.
    module_dir = Path(args.module_dir)
    raw = str(module_dir)
    parts = module_dir.parts
    if len(parts) < 2 or parts[-2] != "uts":
        fail("NOT_A_UTS_MODULE_PATH",
             f"{raw!r} is not a module directory directly under uts/ "
             f"(expected .../uts/<module>).")
    if not module_dir.is_dir():
        fail("DIR_NOT_FOUND", f"{raw!r} does not exist or is not a directory.")
    if not (module_dir / "unit").is_dir() and not (module_dir / "integration").is_dir():
        fail("NO_TIER_DIRS",
             f"{raw!r} has no unit/ or integration/ sub-directory; "
             f"not a valid UTS module.")

    source_module = module_dir.name

    if not MAPPING.is_file():
        fail("MAPPING_NOT_FOUND", f"mapping file not found at {MAPPING}")
    data = json.loads(MAPPING.read_text(encoding="utf-8"))
    packages = data.setdefault("packages", {})

    if args.create:
        target = args.create
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*", target):
            fail("BAD_TARGET_NAME",
                 f"--create target {target!r} must be a simple module base name "
                 f"(letters/digits/underscore, e.g. 'liveobjects') so it forms a "
                 f"valid path and Kotlin package.")
        # Full repo-root-relative paths using the realtime/`lib` (:java) template.
        # A module whose tiers live in another Gradle module (like objects ->
        # :liveobjects) still needs a hand-edit — --create only scaffolds :java-hosted modules.
        base = "lib/src/test/kotlin/io/ably/lib/uts"
        new_entry = {
            "unit": f"{base}/unit/{target}",
            "integration": f"{base}/integration/standard/{target}",
            "proxy": f"{base}/integration/proxy/{target}",
        }
        # preserve a hand-maintained "notes" pointer when re-creating an existing entry
        notes = packages.get(source_module, {}).get("notes")
        if notes:
            new_entry["notes"] = notes
        packages[source_module] = new_entry
        # Write bytes (not write_text): explicit utf-8, and binary mode does zero newline
        # translation on any OS or Python version — so this git-tracked file stays LF on
        # Windows too. (write_text(newline=...) would need Python 3.10+.)
        MAPPING.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))

    mapped = source_module in packages
    entry = packages.get(source_module, {})

    # Per-module translation notes (ably-js -> ably-java type map etc.), declared by
    # the module's "notes" field in the mapping (a path relative to this skill dir).
    # Read it before translating when present. None when the module declares no notes,
    # or the declared file is missing.
    notes_rel = entry.get("notes")
    notes_path = SKILL_DIR / notes_rel if notes_rel else None
    translation_notes = str(notes_path) if (notes_path and notes_path.is_file()) else None

    src = {
        "unit": module_dir / "unit",
        "integration": module_dir / "integration",
        "proxy": module_dir / "integration" / "proxy",
    }
    specs = {
        "unit": list_specs(src["unit"]),
        "integration": list_specs(src["integration"], exclude_proxy=True),
        "proxy": list_specs(src["proxy"]),
    }

    tiers_out = {}
    for tier in TIERS:
        # A tier value is ONE repo-root-relative path (never machine-absolute); the
        # owning module comes from its first path segment (MODULE_BY_PREFIX).
        target_dir = entry.get(tier) if mapped else None
        module = (
            MODULE_BY_PREFIX.get(target_dir.split("/", 1)[0]) if target_dir else None
        )
        tiers_out[tier] = {
            "present": src[tier].is_dir(),
            "sourceDir": str(src[tier]),
            "targetDir": target_dir,
            "package": package_for(target_dir) if target_dir else None,
            "module": module,
            "specs": [{"file": str(p), "className": class_name(p)} for p in specs[tier]],
        }

    print(json.dumps({
        "ok": True,
        "sourceModule": source_module,
        "mapped": mapped,
        "translationNotes": translation_notes,
        "tiers": tiers_out,
    }, indent=2))


if __name__ == "__main__":
    main()
