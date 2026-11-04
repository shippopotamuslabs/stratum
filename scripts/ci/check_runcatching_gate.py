#!/usr/bin/env python3
"""STR-239 gate (v2): plain `runCatching` absorbs CancellationException into a
Result, which suspend code must never do (CODE-STYLE-GOSPEL section 11:
cancellation is not a failure). This project's detekt has no type resolution,
so this scanner carries the rule instead of a detekt rule.

Rules:
  - core/application/src/main: zero tolerance. Every Result-envelope producer
    in a suspend context is runCatchingCancellable (or runCatchingLogged).
  - core/domain/src/main and core/infrastructure/src/main: plain runCatching
    is allowed only in the files listed in scripts/ci/runcatching-allowlist.txt
    (format v2: "<path> <expected-count> # <reason / owner>"), and only in
    exactly the expected number. The gate fails when a listed file's actual
    count differs from the expected count in EITHER direction, when a
    non-listed file has any call, and when a listed path does not exist.

Detection:
  - Plain `runCatching` CALLS in all forms: `runCatching {`,
    `runCatching<T> {`, `runCatching({ ... })`, `runCatching(block = { ... })`,
    and a trailing-lambda brace on the following line.
  - `runCatchingLogged` / `runCatchingCancellable` never match: the token must
    not be followed by another identifier character.
  - Occurrences inside // line comments, /* */ block comments and string
    literals are stripped before scanning, so mentions in prose do not count.
    Approximation (noted per the v2 design): Kotlin string templates are
    stripped whole, so a call inside a "${ ... }" interpolation would not be
    counted; raw strings (\"\"\" ... \"\"\") are recognised and stripped.
    Kotlin block comments nest, and the stripper tracks nesting depth.

On every run the embedded self-test below classifies sample snippets first;
if the classifier disagrees with any expectation the gate fails loudly
before scanning the tree.
"""

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
ALLOWLIST_PATH = REPO_ROOT / "scripts" / "ci" / "runcatching-allowlist.txt"
APPLICATION_DIR = REPO_ROOT / "core" / "application" / "src" / "main"
ALLOWLISTED_DIRS = [
    REPO_ROOT / "core" / "domain" / "src" / "main",
    REPO_ROOT / "core" / "infrastructure" / "src" / "main",
]

# Plain-call pattern: the bare token (not a prefix of runCatchingLogged /
# runCatchingCancellable), an optional type-argument list, then either the
# trailing-lambda "{" (possibly on the following line) or "(" for the
# parenthesised / named-argument forms. "\s" covers the newline-brace form.
CALL_PATTERN = re.compile(
    r"(?<!\w)runCatching(?!\w)"
    r"\s*(?:<[^<>]*(?:<[^<>]*>[^<>]*)*>)?"
    r"\s*(?:\{|\()"
)


def strip_comments_and_strings(source: str) -> str:
    """Blank out comments and string/char literals, preserving newlines (and
    therefore line numbers and offsets). See the module docstring for the
    string-template approximation."""
    out = []
    i = 0
    n = len(source)
    while i < n:
        if source.startswith("//", i):
            # Line comment: blank to (not including) the newline.
            end = source.find("\n", i)
            if end == -1:
                end = n
            out.append(" " * (end - i))
            i = end
        elif source.startswith("/*", i):
            # Block comment; Kotlin allows nesting.
            depth = 1
            j = i + 2
            while j < n and depth:
                if source.startswith("/*", j):
                    depth += 1
                    j += 2
                elif source.startswith("*/", j):
                    depth -= 1
                    j += 2
                else:
                    j += 1
            segment = source[i:j]
            out.append("".join("\n" if c == "\n" else " " for c in segment))
            i = j
        elif source.startswith('"""', i):
            # Raw string: no escapes; ends at the next triple quote.
            end = source.find('"""', i + 3)
            j = n if end == -1 else end + 3
            segment = source[i:j]
            out.append("".join("\n" if c == "\n" else " " for c in segment))
            i = j
        elif source[i] in ('"', "'"):
            # Regular string or char literal: honour backslash escapes.
            quote = source[i]
            j = i + 1
            while j < n:
                if source[j] == "\\":
                    j += 2
                    continue
                if source[j] == quote or source[j] == "\n":
                    j += 1
                    break
                j += 1
            segment = source[i:j]
            out.append("".join("\n" if c == "\n" else " " for c in segment))
            i = j
        else:
            out.append(source[i])
            i += 1
    return "".join(out)


def find_plain_runcatching(source: str):
    """Return [(line_number, matched_text), ...] for plain runCatching calls."""
    cleaned = strip_comments_and_strings(source)
    hits = []
    for match in CALL_PATTERN.finditer(cleaned):
        line = cleaned.count("\n", 0, match.start()) + 1
        hits.append((line, match.group(0).strip()))
    return hits


# --- Embedded self-test ------------------------------------------------------
# (snippet, expected plain-call count). Covers every bypass form, the
# comment/string exclusions, and the two helper names that must never match.
SELF_TEST_CASES = [
    ("val a = runCatching { 1 }", 1),
    ("val a = runCatching<Int> { 1 }", 1),
    ("val a = runCatching({ 1 })", 1),
    ("val a = runCatching(block = { 1 })", 1),
    ("val a = runCatching\n{ 1 }", 1),
    ("val a = runCatching<Int>\n{ 1 }", 1),
    ("// runCatching { 1 }\nval b = 2", 0),
    ("/* runCatching { 1 } */ val b = 2", 0),
    ("/* outer /* runCatching { 1 } */ still comment */ val b = 2", 0),
    ('val s = "runCatching { 1 }"', 0),
    ('val s = """runCatching { 1 }"""', 0),
    ("val a = runCatchingLogged { 1 }", 0),
    ("val a = runCatchingCancellable { 1 }", 0),
    ("val a = runCatchingCancellable<Int> { 1 }", 0),
    # A KDoc/prose mention with no call shape is not a call.
    (" * Decoding uses `runCatching` so a corrupted value maps to null.", 0),
]


def run_self_test() -> bool:
    ok = True
    for snippet, expected in SELF_TEST_CASES:
        actual = len(find_plain_runcatching(snippet))
        if actual != expected:
            print(
                "SELF-TEST FAIL: classifier counted "
                f"{actual} plain runCatching call(s), expected {expected}, in: {snippet!r}",
                file=sys.stderr,
            )
            ok = False
    return ok


# --- Allowlist (format v2) ----------------------------------------------------
ALLOWLIST_ENTRY = re.compile(r"^(\S+)\s+(\d+)\s+#\s*(.+)$")


def load_allowlist():
    """Return (entries, errors): entries maps repo-relative path -> expected count."""
    entries = {}
    errors = []
    for raw_line in ALLOWLIST_PATH.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        match = ALLOWLIST_ENTRY.match(line)
        if not match:
            errors.append(
                "FAIL: malformed allowlist entry (want "
                f"'<path> <expected-count> # <reason / owner>'): {raw_line}"
            )
            continue
        path, count, _reason = match.groups()
        if path in entries:
            errors.append(f"FAIL: duplicate allowlist entry: {path}")
            continue
        entries[path] = int(count)
    return entries, errors


def scan_tree(root: Path):
    """Return {repo-relative path: [(line, matched_text), ...]} for *.kt under root."""
    results = {}
    for kt_file in sorted(root.rglob("*.kt")):
        hits = find_plain_runcatching(kt_file.read_text(encoding="utf-8"))
        if hits:
            results[kt_file.relative_to(REPO_ROOT).as_posix()] = hits
    return results


def main() -> int:
    if not run_self_test():
        print("runCatching gate: SELF-TEST FAILED — classifier is broken, refusing to scan", file=sys.stderr)
        return 1

    failures = []

    # --- application: zero tolerance --------------------------------------
    for path, hits in scan_tree(APPLICATION_DIR).items():
        for line, _matched in hits:
            failures.append(
                f"FAIL: plain runCatching in core/application/src/main "
                f"(use runCatchingCancellable): {path}:{line}"
            )

    # --- domain + infrastructure: allowlisted files, exact counts ----------
    entries, errors = load_allowlist()
    failures.extend(errors)

    actual_by_path = {}
    for directory in ALLOWLISTED_DIRS:
        actual_by_path.update(scan_tree(directory))

    for path, hits in actual_by_path.items():
        if path not in entries:
            for line, _matched in hits:
                failures.append(f"FAIL: plain runCatching outside the allowlist: {path}:{line}")

    for path, expected in entries.items():
        if not (REPO_ROOT / path).is_file():
            failures.append(
                f"FAIL: allowlist entry points at a missing file: {path} "
                "— update the path if it moved, remove the entry if it was deleted"
            )
            continue
        actual = len(actual_by_path.get(path, []))
        if actual != expected:
            failures.append(
                f"FAIL: allowlist count mismatch for {path}: expected {expected}, "
                f"found {actual} — update the expected count if the change is "
                "intended, or convert the site(s) if it is not"
            )

    if failures:
        for failure in failures:
            print(failure)
        return 1
    print("runCatching gate: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
