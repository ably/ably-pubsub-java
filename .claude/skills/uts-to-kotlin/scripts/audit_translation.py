#!/usr/bin/env python3
"""
audit_translation.py — deterministic faithfulness audit of a UTS spec against its
generated Kotlin test, so the per-spec review (SKILL.md Step 7) reconciles a concrete
extracted ledger instead of eyeballing two files.

Usage:
    python3 audit_translation.py <spec-file.md> <GeneratedTest.kt>

It does ZERO semantic judgement — only mechanical extraction with regex, so the same
inputs always give the same report:

  1. Test-ID coverage. Every `**Test ID**: \`<id>\`` in the spec vs every `@UTS <id>`
     in the Kotlin file.
       - missingInKotlin: a spec Test ID with no matching @UTS tag  → a whole test
         case is absent; implement it (or consciously exclude it and explain why).
       - orphanInKotlin:  an @UTS tag with no matching spec Test ID  → a stale or
         hand-edited tag. Investigate.

  2. Per-test line ledger. Within each spec test block (from one Test ID to the next),
     every non-blank, non-comment code line inside the ```pseudo fences is extracted
     verbatim, grouped by its section (Setup / Test Steps / Assertions / …) — so setup,
     operations AND assertions are all enumerated, nothing escapes the ledger. Each line
     is tagged: "assert" (ASSERT*), "await" (AWAIT* / EXPECT), or "step" (everything else
     — setup, mock construction, operations). For convenience the ASSERT* and AWAIT*
     lines are also surfaced flat as specAsserts / specAwaits.
     Alongside them, the count of assertion / await / poll calls in the matching Kotlin
     method is reported as a tripwire: Kotlin assertions < spec ASSERTs for a test is a
     strong signal an assertion was silently dropped.

Robustness contract: this tool must never crash mid-run on any spec/test pair. It is a
review aid — if it can't extract something it degrades to "couldn't verify" (fewer lines
in the ledger) rather than throwing. Whatever happens it emits ONE parseable JSON object,
never a traceback, so a caller can always rely on the output shape.

Exit status:
  0  — clean audit (no missing/orphan Test IDs)
  2  — audit ran, but there are missing/orphan Test IDs (gateable)
  64 — could not run: bad usage, unreadable file, or an internal error (JSON carries `error`)
"""

import json
import re
import sys
from pathlib import Path

# Spec markers -------------------------------------------------------------
TEST_ID_RE = re.compile(r"\*\*Test ID\*\*:\s*`([^`]+)`")
HEADING_RE = re.compile(r"^#{1,4}\s+(.*\S)\s*$")
FENCE_RE = re.compile(r"^\s*```")
# Imperative spec keywords. Order matters: longest / most specific first so AWAIT_STATE
# is classified before the bare AWAIT.
DIRECTIVE_RE = re.compile(r"\b(ASSERT_[A-Z_]+|ASSERT|AWAIT_STATE|AWAIT_ERROR|AWAIT_ALL|AWAIT|EXPECT)\b")

# Kotlin markers -----------------------------------------------------------
UTS_TAG_RE = re.compile(r"@UTS\s+(\S+)")
KOTLIN_ASSERT_RE = re.compile(
    r"\b(assertEquals|assertNotEquals|assertNull|assertNotNull|assertTrue|assertFalse|"
    r"assertIs|assertIsNot|assertContains|assertContentEquals|assertFailsWith|assertFails|assertSame|"
    r"assertNotSame|awaitState|awaitChannelState|pollUntil)\b"
)


def fail(code, message, status=64, **extra):
    """Emit a structured error (same JSON shape as resolve_uts.py) and exit. `status`
    defaults to 64 — "couldn't run" — kept distinct from the audit's own 0 (clean) and
    2 (missing/orphan IDs) outcomes so callers can tell the two apart."""
    print(json.dumps({"ok": False, "error": code, "message": message, **extra}, indent=2))
    sys.exit(status)


def read_lines(path):
    """Read a file into lines, tolerant of encoding issues — a stray non-UTF-8 byte in a
    spec must never crash the audit, so undecodable bytes are replaced, not raised on."""
    return Path(path).read_text(encoding="utf-8", errors="replace").splitlines()


def classify(keyword):
    if keyword.startswith("ASSERT"):
        return "assert"
    if keyword.startswith("AWAIT") or keyword == "EXPECT":
        return "await"
    return "other"


def line_kind(stripped):
    """Tag a pseudocode line: assert / await / step."""
    m = DIRECTIVE_RE.search(stripped)
    if m:
        k = classify(m.group(1))
        if k in ("assert", "await"):
            return k
    return "step"


def parse_spec(path):
    """Return an ordered list of test-id dicts, each:
       {testId, title, sections:[{heading, lines:[{text, kind}]}], asserts[], awaits[], codeLineTotal}."""
    lines = read_lines(path)

    # locate each Test ID line and the nearest preceding heading (its human title)
    tests = []
    last_heading = ""
    for i, line in enumerate(lines):
        h = HEADING_RE.match(line)
        if h:
            last_heading = h.group(1)
        m = TEST_ID_RE.search(line)
        if m:
            tests.append({"testId": m.group(1), "title": last_heading, "_start": i})

    # block boundaries: each test runs to the next test's start (or EOF)
    for idx, t in enumerate(tests):
        start = t["_start"]
        end = tests[idx + 1]["_start"] if idx + 1 < len(tests) else len(lines)

        sections = []          # [{heading, lines:[{text, kind}]}]
        cur = None             # current section being filled
        in_fence = False
        for line in lines[start:end]:
            h = HEADING_RE.match(line)
            if h and not in_fence:
                cur = {"heading": h.group(1), "lines": []}
                sections.append(cur)
                continue
            if FENCE_RE.match(line):
                in_fence = not in_fence
                continue
            if not in_fence:
                continue
            stripped = line.strip()
            if not stripped or stripped.startswith("#"):  # blank / pseudocode comment
                continue
            if cur is None:  # code before any heading in the block (rare)
                cur = {"heading": "(preamble)", "lines": []}
                sections.append(cur)
            cur["lines"].append({"text": stripped, "kind": line_kind(stripped)})

        # drop sections with no code (prose-only headings, requirement tables, the title line)
        sections = [s for s in sections if s["lines"]]
        asserts = [ln["text"] for s in sections for ln in s["lines"] if ln["kind"] == "assert"]
        awaits = [ln["text"] for s in sections for ln in s["lines"] if ln["kind"] == "await"]
        t["sections"] = sections
        t["asserts"] = asserts
        t["awaits"] = awaits
        t["codeLineTotal"] = sum(len(s["lines"]) for s in sections)
        del t["_start"]
    return tests


def parse_kotlin(path):
    """Return dict: uts-id -> {assertionCalls[], assertionCount}. Method block for a tag
    runs from its @UTS line to the next @UTS line (or EOF)."""
    lines = read_lines(path)
    tags = [(i, m.group(1)) for i, line in enumerate(lines) for m in [UTS_TAG_RE.search(line)] if m]
    out = {}
    for idx, (start, tag) in enumerate(tags):
        end = tags[idx + 1][0] if idx + 1 < len(tags) else len(lines)
        calls = []
        for line in lines[start:end]:
            for m in KOTLIN_ASSERT_RE.finditer(line):
                calls.append(m.group(1))
        out[tag] = {"assertionCalls": calls, "assertionCount": len(calls)}
    return out


def main():
    if len(sys.argv) != 3:
        fail("USAGE", "Usage: audit_translation.py <spec-file.md> <GeneratedTest.kt>")

    spec_path, kt_path = sys.argv[1], sys.argv[2]
    for p in (spec_path, kt_path):
        if not Path(p).is_file():
            fail("FILE_NOT_FOUND", f"{p!r} not found.")

    # Robustness backstop: never let an unexpected parsing/IO error surface as a traceback.
    # fail() emits structured JSON and exits 64 (distinct from the 0/2 audit outcomes) so
    # callers and the model can tell "couldn't run" apart from "ran, found gaps".
    try:
        spec_tests = parse_spec(spec_path)
        kotlin = parse_kotlin(kt_path)
        report = build_report(spec_path, kt_path, spec_tests, kotlin)
    except Exception as exc:  # noqa: BLE001 — deliberate catch-all; this tool must not crash
        fail("INTERNAL_ERROR", f"{type(exc).__name__}: {exc}", spec=spec_path, kotlin=kt_path)

    print(json.dumps(report, indent=2))
    cov = report["idCoverage"]
    sys.exit(2 if (cov["missingInKotlin"] or cov["orphanInKotlin"]) else 0)


def build_report(spec_path, kt_path, spec_tests, kotlin):
    spec_ids = [t["testId"] for t in spec_tests]
    kt_ids = list(kotlin.keys())
    spec_id_set, kt_id_set = set(spec_ids), set(kt_ids)
    missing = [i for i in spec_ids if i not in kt_id_set]
    orphan = [i for i in kt_ids if i not in spec_id_set]

    per_test = []
    for t in spec_tests:
        kt = kotlin.get(t["testId"])
        per_test.append({
            "testId": t["testId"],
            "title": t["title"],
            # every code line of the spec test, grouped by section (setup / steps / assertions),
            # each tagged assert | await | step — the full "did I translate every line?" ledger
            "sections": t["sections"],
            "specCodeLineTotal": t["codeLineTotal"],
            # flat convenience views of the observable lines
            "specAsserts": t["asserts"],
            "specAwaits": t["awaits"],
            "specAssertCount": len(t["asserts"]),
            "specAwaitCount": len(t["awaits"]),
            "kotlinPresent": kt is not None,
            "kotlinAssertionCalls": kt["assertionCalls"] if kt else [],
            "kotlinAssertionCount": kt["assertionCount"] if kt else 0,
            # tripwire: fewer Kotlin assertions than spec ASSERTs => likely a dropped assertion
            "assertionShortfall": (len(t["asserts"]) - kt["assertionCount"]) if kt else len(t["asserts"]),
        })

    report = {
        "ok": True,
        "spec": spec_path,
        "kotlin": kt_path,
        "idCoverage": {
            "specCount": len(spec_ids),
            "kotlinCount": len(kt_ids),
            "missingInKotlin": missing,
            "orphanInKotlin": orphan,
        },
        "perTest": per_test,
        "summary": {
            "specAssertTotal": sum(len(t["asserts"]) for t in spec_tests),
            "specAwaitTotal": sum(len(t["awaits"]) for t in spec_tests),
            "kotlinAssertionTotal": sum(k["assertionCount"] for k in kotlin.values()),
            "testsWithShortfall": [p["testId"] for p in per_test if p["assertionShortfall"] > 0],
        },
    }
    return report


if __name__ == "__main__":
    main()
